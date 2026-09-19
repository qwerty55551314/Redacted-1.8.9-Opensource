package redacted.client.module.impl.render;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.ESPUtil;
import redacted.client.util.render.Render3DUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.ProjectileUtil;

import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {

    private final ColorValue color = new ColorValue("Color", this, 0xFF00C8FF).describe("Arc color");
    private final NumberValue lineWidth = new NumberValue("Line Width", this, 2.0D, 1.0D, 5.0D, 0.5D).describe("Arc line width");
    private final BooleanValue hitMarkers = new BooleanValue("Hit Markers", this, true).describe("Box the entity or block the arc hits");
    private final BooleanValue alwaysShowBow = new BooleanValue("Always Show Bow", this, false).describe("Show the bow arc at full draw even before drawing");

    // ponytail: simulating the arc costs a block raytrace AND an entity query per step, up to 200
    // steps -- redoing that every rendered frame burned it hundreds of times a second while you
    // just stood there holding a rod. cached against the inputs that produce it (spawn point,
    // muzzle velocity, held item) and refreshed at least once per tick, so a block placed into the
    // arc can never leave it more than a tick stale. drawing the cached points is nearly free.
    private final List<Vec3> arc = new ArrayList<>();
    private MovingObjectPosition arcHit;
    private Vec3 cachedPos, cachedMotion;
    private Item cachedItem;
    private int cachedTick = -1;

    public Trajectories() {
        super("Trajectories", "Show projectile trajectory arc", Keyboard.KEY_NONE, Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (Mirror.isRendering()) return;   // player rotations are flipped in the mirror pass -> backwards arc
        if (mc.theWorld == null || mc.thePlayer == null) return;
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) return;
        Item item = stack.getItem();

        // vanilla constants: EntityArrow / EntityThrowable / EntityPotion / EntityExpBottle / EntityFishHook
        double speed, gravity = 0.03D, drag = 0.99D;
        float pitchOffset = 0.0F;
        if (item instanceof ItemBow) {
            speed = ProjectileUtil.bowVelocity(stack); // 0 while not drawing
            if (speed <= 0.0D && alwaysShowBow.getValue()) speed = ProjectileUtil.fullDrawVelocity();
            gravity = 0.05D;
        } else if (item instanceof ItemSnowball || item instanceof ItemEgg || item instanceof ItemEnderPearl) {
            speed = 1.5D;
        } else if (item instanceof ItemPotion && ItemPotion.isSplash(stack.getMetadata())) {
            speed = 0.5D; gravity = 0.05D; pitchOffset = -20.0F;
        } else if (item instanceof ItemExpBottle) {
            speed = 0.7D; gravity = 0.07D; pitchOffset = -20.0F;
        } else if (item instanceof ItemFishingRod) {
            speed = 1.5D; gravity = 0.04D; drag = 0.92D;
        } else return;
        if (speed <= 0.0D) return; // bow not drawn yet

        float pt = event.getPartialTicks();
        EntityPlayerSP p = mc.thePlayer;
        double yaw = Math.toRadians(p.rotationYaw), pitch = Math.toRadians(p.rotationPitch);
        // spawn point + heading exactly as the entity constructors: 0.16 sideways, 0.1 below the eye
        Vec3 pos = new Vec3(
                p.prevPosX + (p.posX - p.prevPosX) * pt - Math.cos(yaw) * 0.16D,
                p.prevPosY + (p.posY - p.prevPosY) * pt + p.getEyeHeight() - 0.1D,
                p.prevPosZ + (p.posZ - p.prevPosZ) * pt - Math.sin(yaw) * 0.16D);
        Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(Math.toRadians(p.rotationPitch + pitchOffset)), Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3 motion = new Vec3(dir.xCoord * speed, dir.yCoord * speed, dir.zCoord * speed);

        if (p.ticksExisted != cachedTick || item != cachedItem
                || !same(pos, cachedPos) || !same(motion, cachedMotion)) {
            cachedTick = p.ticksExisted;
            cachedItem = item;
            cachedPos = pos;
            cachedMotion = motion;
            simulate(p, item, pos, motion, gravity, drag);
        }
        if (arc.size() < 2) return;

        double[] v = Render3DUtil.viewerOffset();
        int rgb = color.getRGB();
        Render3DUtil.start3D();
        try {
            GL11.glLineWidth(lineWidth.floatValue());
            ColorUtil.glColor(ColorUtil.withAlpha(rgb, 255));

            // one batched GL_LINES draw for the whole arc instead of one tessellator
            // draw per segment (up to ~200 draw calls/frame while holding a bow)
            Render3DUtil.beginLines();
            for (int i = 1; i < arc.size(); i++) {
                Vec3 a = arc.get(i - 1), b = arc.get(i);
                Render3DUtil.lineVertex(a.xCoord - v[0], a.yCoord - v[1], a.zCoord - v[2]);
                Render3DUtil.lineVertex(b.xCoord - v[0], b.yCoord - v[1], b.zCoord - v[2]);
            }
            Render3DUtil.drawLines();

            if (arcHit != null && hitMarkers.getValue()) {
                AxisAlignedBB box = arcHit.entityHit != null
                        ? ESPUtil.getInterpolatedBoundingBox(arcHit.entityHit, pt)
                        : mc.theWorld.getBlockState(arcHit.getBlockPos()).getBlock().getSelectedBoundingBox(mc.theWorld, arcHit.getBlockPos()).offset(-v[0], -v[1], -v[2]);
                Render3DUtil.drawBox(box, ColorUtil.withAlpha(rgb, 40), ColorUtil.withAlpha(rgb, 255));
            }
        } finally {
            Render3DUtil.end3D();
        }
    }

    // fills arc with the segment endpoints (arc[0] is the spawn point) and arcHit with what stopped it
    private void simulate(EntityPlayerSP p, Item item, Vec3 pos, Vec3 motion, double gravity, double drag) {
        arc.clear();
        arc.add(pos);
        arcHit = null;
        for (int i = 0; i < 200 && arcHit == null && pos.yCoord > 0.0D; i++) { // ponytail: 200 ticks = 10 s of flight, plenty
            Vec3 next = pos.add(motion);
            // same ray flags as vanilla: arrows skip no-hitbox blocks (tall grass), thrown items hit them, the hook stops on water
            arcHit = mc.theWorld.rayTraceBlocks(pos, next, item instanceof ItemFishingRod, item instanceof ItemBow, false);
            AxisAlignedBB sweep = new AxisAlignedBB(pos.xCoord, pos.yCoord, pos.zCoord, next.xCoord, next.yCoord, next.zCoord).expand(1.0D, 1.0D, 1.0D);
            for (Entity e : mc.theWorld.getEntitiesWithinAABBExcludingEntity(p, sweep)) {
                if (!e.canBeCollidedWith()) continue;
                MovingObjectPosition m = e.getEntityBoundingBox().expand(0.3D, 0.3D, 0.3D).calculateIntercept(pos, next); // 0.3 = vanilla projectile hit inflation
                if (m != null && (arcHit == null || pos.squareDistanceTo(m.hitVec) < pos.squareDistanceTo(arcHit.hitVec))) arcHit = new MovingObjectPosition(e, m.hitVec);
            }
            if (arcHit != null) next = arcHit.hitVec;
            arc.add(next);
            // ponytail: air physics only; add water drag (0.6 arrow / 0.8 thrown) if underwater arcs ever matter
            motion = new Vec3(motion.xCoord * drag, motion.yCoord * drag - gravity, motion.zCoord * drag);
            pos = next;
        }
    }

    // Vec3 has no equals() in 1.8 -- identity compare would miss every cache hit
    private static boolean same(Vec3 a, Vec3 b) {
        return b != null && a.xCoord == b.xCoord && a.yCoord == b.yCoord && a.zCoord == b.zCoord;
    }
}
