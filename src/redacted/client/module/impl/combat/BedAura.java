package redacted.client.module.impl.combat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.utility.AutoTool;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.font.Font;
import redacted.client.util.misc.PacketUtils;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.Render3DUtil;
import redacted.client.util.render.RenderUtil;
import redacted.client.util.world.BedTargetingUtil;
import redacted.client.util.world.PlaceUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.CombatManager;
import redacted.util.RaycastUtil;
import redacted.util.RotationUtil;

public class BedAura extends Module {

    private static final String MODE_DEFAULT = "Default";
    private static final String MODE_HYPIXEL = "Hypixel";

    private static final EnumFacing[] FACE_PRIORITY = {
            EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN
    };

    private final ModeValue mode = new ModeValue("Mode", this, MODE_DEFAULT, MODE_DEFAULT, MODE_HYPIXEL).describe("Default raytrace break vs Hypixel adjacent-then-bed");
    private final NumberValue range = new NumberValue("Range", this, 4.5D, 1.0D, 6.0D, 0.1D).describe("How far to reach beds");
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", this, 45.0D, 10.0D, 90.0D, 5.0D).describe("How fast to turn toward the bed");
    private final BooleanValue throughWalls = new BooleanValue("Through Walls", this, true, mode, MODE_DEFAULT).describe("Break cover blocks to reach the bed");
    private final BooleanValue ignoreOwnBed = new BooleanValue("Ignore Own Bed", this, true).describe("Skip the bed you spawned next to");
    private final BooleanValue pauseOnEnemy = new BooleanValue("Pause On Enemy", this, true).describe("Stand down as soon as any enemy is in range, even before the aim cone");
    private final BooleanValue render = new BooleanValue("Render", this, true).describe("Show the break box in-world");
    private final BooleanValue progressBar = new BooleanValue("Progress Bar", this, true).describe("Show the on-screen break percentage");

    private BlockPos currentTarget;
    private int postBreakCooldown;
    private boolean wasAiming;   // true while we hold the wire -- guards reset() from wiping another module's rotation

    // just took a hit? pause the dig for a few ticks. a C07 fired through the knockback's position
    // desync lands at a stale spot -> grim BlockBreak / watchdog. dual signal, same as AutoBlock: the
    // S12 velocity packet (set on the netty thread) plus the hurtTime rising edge for damage that
    // carries no knockback packet.
    private static final int KNOCKBACK_PAUSE_TICKS = 3;
    private volatile boolean knockbackPacket;
    private int prevHurtTime;
    private int knockbackTicks;
    // hypixel mode: which adjacent we picked to break, and the bed foot it's bound to
    private BlockPos lockedAdjacent;
    private BlockPos lockedAdjacentBed;
    // 2D break bar: fade, plus the last fill/color held during fade-out so it doesn't snap to 0%
    private final Animation barFade = new Animation(Easing.EASE_OUT_QUAD, 200);
    private float lastBarProgress;
    private boolean lastBarIsBed;
    // BedAura-tracked dig progress for the indicator -- vanilla wipes curBlockDamageMP every tick we're not
    // holding attack (Minecraft.sendClickBlockToController -> resetBlockRemoving), so accumulate our own
    private BlockPos renderBreakPos;
    private float renderBreakProgress;

    // yield-tick ABORT: when SilentAura owns the wire at the player and we were mid-dig, abandonTarget
    // fires the C07 ABORT under a transient C05(bed) look then a C05(restore=player), so the abort
    // validates against the bed, not the aura's player aim. lastBedYaw/Pitch is the last injected look.
    private float lastBedYaw, lastBedPitch;
    private boolean haveBedLook;

    // PlayerControllerMP.resetBlockRemoving() bails when this is true, so NO caller (vanilla
    // sendClickBlockToController, AutoBlock raise, AutoClicker) can ABORT our bed dig mid-break
    // a stray ABORT cancels the server-side break and forces a re-break. We hold isHittingBlock alive the
    // whole dig and only ever ABORT ourselves via abandonTarget (which drops ownership first).
    public static boolean owningDig() {
        BedAura b = Client.getInstance().getModuleManager().getModule(BedAura.class);
        return b != null && b.ownsLiveDig();
    }

    private boolean ownsLiveDig() {
        return isEnabled() && currentTarget != null
                && mc.playerController != null && mc.playerController.getIsHittingBlock();
    }

    public BedAura() {
        super("BedAura", "Auto-breaks enemy beds in range, breaking through cover as needed.",
                Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        postBreakCooldown = 0;
        wasAiming = false;
        haveBedLook = false;
        lockedAdjacent = null;
        lockedAdjacentBed = null;
        knockbackPacket = false;
        knockbackTicks = 0;
        // seed off the live hurtTime so toggling on mid-hurt doesn't read as a fresh hit
        prevHurtTime = mc.thePlayer != null ? mc.thePlayer.hurtTime : 0;
        clearBreakRender();
    }

    @Override
    public void onDisable() {
        releaseAim();
        abandonTarget();   // sandwich-aware: fires the ABORT under the bed look if we're interleaving
        postBreakCooldown = 0;
        haveBedLook = false;
        lockedAdjacent = null;
        lockedAdjacentBed = null;
        knockbackPacket = false;
        knockbackTicks = 0;
        prevHurtTime = 0;
        barFade.setValue(0.0);
        lastBarProgress = 0F;
        clearBreakRender();
    }

    // drop our claim on the wire -- only if we actually own it, so an idle bed aura
    // never wipes another module's held rotation (scaffold mid-bridge etc).
    private void releaseAim() {
        if (!wasAiming) return;
        RotationUtil.reset();
        wasAiming = false;
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;

        // tick the post-hit pause down first, then re-arm on a fresh hit (S12 from the netty thread, or
        // a hurtTime rising edge for damage with no knockback packet). kept ahead of the screen/alive bail
        // so the hurt baseline doesn't drift while a GUI is open.
        if (knockbackTicks > 0) knockbackTicks--;
        final int hurtTime = mc.thePlayer.hurtTime;
        if (knockbackPacket || hurtTime > prevHurtTime) {
            knockbackPacket = false;
            knockbackTicks = KNOCKBACK_PAUSE_TICKS;
        }
        prevHurtTime = hurtTime;

        if (mc.currentScreen != null || !mc.inGameHasFocus || !mc.thePlayer.isEntityAlive()) {
            abandonTarget();
            releaseAim();
            return;
        }

        // SilentAura fighting? fully stand down for the tick -- our bed-break rotation+dig contention
        // otherwise eats its opening C02s (the dead-hits-for-a-few-ticks bug). Pause On Enemy widens the
        // yield to any enemy merely in range, an earlier stand-down before the target enters the cone.
        final boolean auraOwnsWire = isInterleaving();
        if (auraOwnsWire || (pauseOnEnemy.getValue() && auraHasEnemyInRange())) {
            abandonTarget();   // sandwich-aware ABORT if we were mid-dig (fires under a bed-look)
            if (auraOwnsWire) {
                // the aura already set the wire this tick (it runs onTick before us) -- concede WITHOUT
                // reset(): reset() is a global active=false and would wipe the aura's just-set rotation,
                // killing the opening hits we're trying to protect. just drop our exclusive claim.
                wasAiming = false;
            } else {
                // enemy in range but the aura isn't holding the wire -- nothing to preserve, drop our claim.
                releaseAim();
            }
            return;
        }

        if (postBreakCooldown > 0) postBreakCooldown--;

        // target just popped, sit two ticks so grim's BlockBreak.Order doesn't see back-to-back STOP+START.
        // route through abandonTarget so an externally-broken block (isHittingBlock still true) aborts under
        // a bed look, not the aura's player aim. after our own STOP isHittingBlock is already false -> no-op.
        if (currentTarget != null && mc.theWorld.isAirBlock(currentTarget)) {
            abandonTarget();
            postBreakCooldown = 2;
        }

        int radius = MathHelper.ceiling_double_int(range.getValue()) + 1;
        BlockPos excludeOwn = ignoreOwnBed.getValue() ? CombatManager.ownBedFoot() : null;
        BedTargetingUtil.Bed bed = BedTargetingUtil.findNearestBed(radius, Double.MAX_VALUE, excludeOwn);
        if (bed == null) {
            abandonTarget();
            releaseAim();
            return;
        }

        TargetCandidate candidate;
        if (MODE_HYPIXEL.equals(mode.getValue())) {
            candidate = pickTargetHypixel(bed);
        } else {
            // default mode doesn't own the lockedAdjacent state
            lockedAdjacent = null;
            lockedAdjacentBed = null;
            candidate = pickTarget(bed);
        }
        if (candidate == null) {
            abandonTarget();
            releaseAim();
            return;
        }

        PlaceUtil.Aim aim;
        if (candidate.throughWalls) {
            // hypixel through-wall bed-break: aim at face center, no raycast validation
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            aim = PlaceUtil.findAimUnchecked(candidate.pos, candidate.preferredFace, eyes,
                    mc.thePlayer.getLastReportedYaw(), mc.thePlayer.getLastReportedPitch());
        } else {
            aim = findAim(candidate.pos, candidate.preferredFace);
            if (aim == null) return;
        }

        // LastReported, not stepped -- stepped trips grim RotationBreak.
        // through-wall bed-break skips the LOS check; hypixel allows it once any adjacent is air.
        // knockbackTicks gates every path: while we're being knocked back, a C07 reaches at a stale
        // position. skipping it (not aborting) just holds the server-side break -- same as the transient
        // canDig=false below.
        boolean canDig;
        if (candidate.throughWalls) {
            canDig = postBreakCooldown == 0 && knockbackTicks == 0;
        } else {
            canDig = postBreakCooldown == 0 && knockbackTicks == 0 && PlaceUtil.raytraceMatches(
                    candidate.pos, aim.face,
                    mc.thePlayer.getLastReportedYaw(), mc.thePlayer.getLastReportedPitch(),
                    range.getValue() + 1.0D);
        }

        if (canDig) {
            final BlockPos pos = candidate.pos;
            final EnumFacing face = aim.face;
            final boolean fresh = currentTarget == null || !currentTarget.equals(pos);

            // ask AutoTool to swap BEFORE we read hardness and fire the dig -- the C09 lands ahead of the
            // C07 so the server applies the tool's hardness curve, and perTick (the bar / STOP prediction)
            // reflects the tool, not the hand. selectToolFor is a no-op when current == best, so it stays
            // cheap on same-target ticks and doesn't drift the held item mid-break.
            AutoTool at = Client.getInstance().getModuleManager().getModule(AutoTool.class);
            if (at != null) {
                at.selectToolFor(mc.theWorld.getBlockState(pos).getBlock());
            }

            // same curve onPlayerDamageBlock accumulates with -- drives both the STOP prediction and the bar
            float perTick = mc.theWorld.getBlockState(pos).getBlock()
                    .getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, pos);
            if (perTick < 0F) perTick = 0F;

            if (fresh) {
                // don't reset first, clickBlock bundles ABORT+START -- splitting trips grim MultiActions
                mc.playerController.clickBlock(pos, face);
            } else {
                // vanilla emits STOP_DESTROY itself once curBlockDamageMP hits 1.0F
                mc.playerController.onPlayerDamageBlock(pos, face);
            }
            // vanilla's swing loop is gated on !RotationUtil.isActive(), drive C0A ourselves
            mc.thePlayer.swingItem();
            currentTarget = pos;

            // accumulate our own break progress for the indicator. monotonic per target: the same block
            // keeps climbing, a genuine target switch restarts at perTick. all the real resets live in the
            // clearBreakRender callers (target gone/air, abandonTarget, disable) -- never inline here.
            if (pos.equals(renderBreakPos)) {
                renderBreakProgress = Math.min(1F, renderBreakProgress + perTick);
            } else {
                renderBreakPos = pos;
                renderBreakProgress = Math.min(1F, perTick);
            }
        }
        // canDig was false: a transient skip (post-break cooldown, or in default mode the rotation hasn't
        // lined the raytrace up yet) -- NOT a target change. the server-side break still holds its progress,
        // so wiping ours here is exactly what snapped the bar 0->100. hold the fill and let the genuine
        // resets above own it.

        // rotation last, dig's already queued ahead of MotionEvent.PRE
        RotationUtil.setRotation(aim.yaw, aim.pitch, rotationSpeed.floatValue());
        wasAiming = true;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.INBOUND) return;
        Packet<?> packet = event.getPacket();

        // local-player knockback -> arm the dig pause (consumed on the main thread in onTick). snapshot
        // thePlayer: this is the netty thread and a disconnect can null it mid-read.
        if (packet instanceof S12PacketEntityVelocity) {
            EntityPlayerSP self = mc.thePlayer;
            if (self != null && ((S12PacketEntityVelocity) packet).getEntityID() == self.getEntityId()) {
                knockbackPacket = true;
            }
            return;
        }

        if (packet instanceof S07PacketRespawn || packet instanceof S01PacketJoinGame) {
            // respawn or dim change, drop any stale target
            currentTarget = null;
            postBreakCooldown = 0;
            // clear the post-hit pause too -- a death-tick S12 / leftover counter shouldn't bleed a
            // phantom pause into the next life
            knockbackPacket = false;
            knockbackTicks = 0;
            prevHurtTime = 0;
            lockedAdjacent = null;
            lockedAdjacentBed = null;
            clearBreakRender();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;

        // follow whatever block the dig FSM is chipping right now (BedAura drives this same FSM on auto-break)
        BlockPos pos = breakingBlock();
        if (pos == null) return;

        // bed is only 9/16 tall, walls are full cubes
        boolean isBed = mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed;
        double maxY = isBed ? 0.5625D : 1.0D;

        double[] viewer = Render3DUtil.viewerOffset();
        int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();

        // full frame stays put so you always see the target; the solid fills up to it as the dig completes
        AxisAlignedBB outline = new AxisAlignedBB(bx, by, bz, bx + 1.0D, by + maxY, bz + 1.0D)
                .offset(-viewer[0], -viewer[1], -viewer[2]);
        double fillTop = maxY * breakProgress();

        // bed=green, wall=red
        float r = isBed ? 0.25F : 1.0F;
        float g = isBed ? 1.0F : 0.3F;
        float b = isBed ? 0.45F : 0.3F;

        Render3DUtil.start3D();
        if (fillTop > 0.001D) {
            AxisAlignedBB fill = new AxisAlignedBB(bx, by, bz, bx + 1.0D, by + fillTop, bz + 1.0D)
                    .offset(-viewer[0], -viewer[1], -viewer[2]);
            GlStateManager.color(r, g, b, 0.25F);
            Render3DUtil.drawFilledBox(fill);
        }
        GL11.glLineWidth(2.0F);
        GlStateManager.color(r, g, b, 1.0F);
        Render3DUtil.drawBoundingBox(outline);
        Render3DUtil.end3D();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!render.getValue() || !progressBar.getValue()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return;

        // show while any block is being dug (manual or BedAura-driven). hold the last fill during fade-out
        // so the bar eases away from its real value instead of snapping to 0% the frame the block pops
        BlockPos pos = breakingBlock();
        boolean breaking = pos != null;
        if (breaking) {
            lastBarProgress = breakProgress();
            lastBarIsBed = mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed;
        }

        barFade.run(breaking ? 1.0 : 0.0);
        float alpha = (float) barFade.getValue();
        if (alpha < 0.01F) return; // let it ease out before bailing

        float progress = lastBarProgress;
        // bar hue follows the block type so it reads as one indicator with the in-world box
        int fillColor = lastBarIsBed ? 0xFF40FF73 : 0xFFFF4D4D;

        ScaledResolution sr = event.getScaledResolution();
        double barW = 110.0D, barH = 12.0D, radius = barH / 2.0D;
        double cx = sr.getScaledWidth() / 2.0D;
        double barX = cx - barW / 2.0D;
        double barY = sr.getScaledHeight() * 0.78D - barH / 2.0D;

        RenderUtil.drawRoundedRect(barX, barY, barW, barH, radius, ColorUtil.withAlpha(0xFF2A2D34, alpha));
        RenderUtil.drawRoundedOutline(barX, barY, barW, barH, radius, 1.0F, ColorUtil.withAlpha(0x554794FD, alpha));

        // sub-radius slivers render as an ugly clamped pill, so hold the fill back until it can round cleanly
        double fillW = barW * progress;
        if (fillW > radius) {
            RenderUtil.drawRoundedRect(barX, barY, fillW, barH, radius, ColorUtil.withAlpha(fillColor, alpha));
        }

        Font font = Fonts.MAIN.get(12, Weight.BOLD);
        String pct = (int) Math.round(progress * 100.0D) + "%";
        double tx = cx - font.width(pct) / 2.0D;
        double ty = barY + (barH - font.height()) / 2.0D + 0.5D;
        font.draw(pct, tx, ty, ColorUtil.withAlpha(0xFFFFFFFF, alpha), true);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F); // SF-Pro resets this itself, but the MC font path doesn't -- keep the cache honest
    }

    private void abandonTarget() {
        // drop ownership FIRST so owningDig() goes false and our own resetBlockRemoving isn't suppressed
        BlockPos pos = currentTarget;
        currentTarget = null;
        if (pos != null && mc.playerController != null && mc.playerController.getIsHittingBlock()) {
            if (isInterleaving()) {
                // ABORT is rotation-checked too -- fire it under a bed look, not the aura's player aim.
                // reuse the last injected look, or recompute one toward the block (exclusive->interleaving
                // hand-off where we haven't sandwiched yet, so haveBedLook is still false).
                float yaw = lastBedYaw, pitch = lastBedPitch;
                if (!haveBedLook) {
                    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
                    PlaceUtil.Aim a = PlaceUtil.findAimUnchecked(pos, faceTowardBlock(eyes, pos),
                            eyes, mc.thePlayer.getLastReportedYaw(), mc.thePlayer.getLastReportedPitch());
                    yaw = a.yaw;
                    pitch = a.pitch;
                }
                digSandwiched(yaw, pitch, () -> mc.playerController.resetBlockRemoving());
            } else {
                mc.playerController.resetBlockRemoving();
            }
        }
        clearBreakRender();
    }

    // SilentAura has a target in range+FOV and owns the rotation wire at the player. BedAura yields the
    // whole tick when this is true (see onTick); abandonTarget also reads it to fire its ABORT under a
    // bed-look instead of the aura's player aim.
    private boolean isInterleaving() {
        SilentAura aura = Client.getInstance().getModuleManager().getModule(SilentAura.class);
        return aura != null && aura.isEnabled() && aura.hasActiveRotationTarget();
    }

    // any enemy merely in range of the aura (no FOV gate) -- BedAura's Pause On Enemy yields off this.
    private boolean auraHasEnemyInRange() {
        SilentAura aura = Client.getInstance().getModuleManager().getModule(SilentAura.class);
        return aura != null && aura.isEnabled() && aura.hasEnemyInRange();
    }

    // wrap the rotation-checked C07 (now only the abandonTarget ABORT) in a transient look at the bed, then
    // restore the server's current look (the aura's player aim) -- so the abort validates against the bed
    // and combat against the player, same tick. camera is never touched; the end-of-tick C03 carries the aim.
    private void digSandwiched(float bedYaw, float bedPitch, Runnable dig) {
        boolean onGround = mc.thePlayer.onGround;
        PacketUtils.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(bedYaw, bedPitch, onGround));
        try {
            dig.run();
        } finally {
            float ry = RotationUtil.isActive() ? RotationUtil.getServerYaw() : mc.thePlayer.rotationYaw;
            float rp = RotationUtil.isActive() ? RotationUtil.getServerPitch() : mc.thePlayer.rotationPitch;
            PacketUtils.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(ry, rp, onGround));
            lastBedYaw = bedYaw;
            lastBedPitch = bedPitch;
            haveBedLook = true;
        }
    }

    private TargetCandidate pickTarget(BedTargetingUtil.Bed bed) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        // 0.28125 = bed AABB y-midpoint
        Vec3 bedCenter = new Vec3(
                (bed.foot.getX() + bed.head.getX() + 1.0D) * 0.5D,
                bed.foot.getY() + 0.28125D,
                (bed.foot.getZ() + bed.head.getZ() + 1.0D) * 0.5D);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, bedCenter, false, false, false);

        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && (hit.getBlockPos().equals(bed.foot) || hit.getBlockPos().equals(bed.head))) {
            if (RaycastUtil.reachBlock(hit.getBlockPos()) > range.getValue()) return null;
            return new TargetCandidate(hit.getBlockPos(), hit.sideHit);
        }

        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            // eyes inside bed AABB -> raytrace null, fall back to a reachable half
            if (RaycastUtil.reachBlock(bed.head) <= range.getValue()) {
                return new TargetCandidate(bed.head, EnumFacing.UP);
            }
            if (RaycastUtil.reachBlock(bed.foot) <= range.getValue()) {
                return new TargetCandidate(bed.foot, EnumFacing.UP);
            }
            return null;
        }

        if (!throughWalls.getValue()) return null;

        BlockPos wallPos = hit.getBlockPos();
        Block wallBlock = mc.theWorld.getBlockState(wallPos).getBlock();
        if (wallBlock == Blocks.air || wallBlock == Blocks.bedrock) return null;
        if (wallBlock.getBlockHardness(mc.theWorld, wallPos) < 0.0F) return null;
        if (RaycastUtil.reachBlock(wallPos) > range.getValue()) return null;
        return new TargetCandidate(wallPos, hit.sideHit);
    }

    // hypixel mode: lock onto one adjacent block of the bed, break it (through walls if needed),
    // then jump straight to the bed -- which hypixel lets you dig through any remaining walls.
    private TargetCandidate pickTargetHypixel(BedTargetingUtil.Bed bed) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);

        // any adjacent is air? bed is now breakable, dig the bed through whatever's left
        if (BedTargetingUtil.isBedExposed(bed)) {
            lockedAdjacent = null;
            lockedAdjacentBed = null;
            BlockPos half = pickReachableBedHalf(bed);
            if (half == null) return null;
            return new TargetCandidate(half, EnumFacing.UP, true);
        }

        // bed is protected -- need to take out one adjacent. invalidate the lock if stale.
        if (lockedAdjacent != null) {
            boolean stale = !bed.foot.equals(lockedAdjacentBed)
                    || mc.theWorld.isAirBlock(lockedAdjacent)
                    || RaycastUtil.reachBlock(lockedAdjacent) > range.getValue();
            if (stale) {
                lockedAdjacent = null;
                lockedAdjacentBed = null;
            }
        }
        if (lockedAdjacent == null) {
            lockedAdjacent = BedTargetingUtil.pickAdjacentToBreak(bed, eyes, range.getValue());
            if (lockedAdjacent == null) return null;
            lockedAdjacentBed = bed.foot;
        }

        // hypixel: dig the adjacent directly, even through outer cover
        return new TargetCandidate(lockedAdjacent, faceTowardBlock(eyes, lockedAdjacent), true);
    }

    private EnumFacing faceTowardBlock(Vec3 eyes, BlockPos pos) {
        double dx = eyes.xCoord - (pos.getX() + 0.5D);
        double dy = eyes.yCoord - (pos.getY() + 0.5D);
        double dz = eyes.zCoord - (pos.getZ() + 0.5D);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0 ? EnumFacing.EAST : EnumFacing.WEST;
        }
        if (ay >= ax && ay >= az) {
            return dy > 0 ? EnumFacing.UP : EnumFacing.DOWN;
        }
        return dz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private BlockPos pickReachableBedHalf(BedTargetingUtil.Bed bed) {
        double r = range.getValue();
        double footDist = RaycastUtil.reachBlock(bed.foot);
        double headDist = RaycastUtil.reachBlock(bed.head);
        if (footDist <= headDist) {
            if (footDist <= r) return bed.foot;
            if (headDist <= r) return bed.head;
        } else {
            if (headDist <= r) return bed.head;
            if (footDist <= r) return bed.foot;
        }
        return null;
    }

    // BedAura's own dig progress 0..1 (vanilla resets curBlockDamageMP every tick we're not holding attack)
    private float breakProgress() {
        float p = renderBreakProgress;
        return p < 0F ? 0F : (p > 1F ? 1F : p);
    }

    // the block the aura is currently chipping, or null when it's idle (or the block is already gone)
    private BlockPos breakingBlock() {
        if (renderBreakPos == null || mc.theWorld == null || mc.theWorld.isAirBlock(renderBreakPos)) return null;
        return renderBreakPos;
    }

    private void clearBreakRender() {
        renderBreakPos = null;
        renderBreakProgress = 0F;
    }

    private PlaceUtil.Aim findAim(BlockPos target, EnumFacing preferredFace) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double distance = range.getValue() + 1.0D;
        float baseYaw = mc.thePlayer.getLastReportedYaw();
        float basePitch = mc.thePlayer.getLastReportedPitch();
        if (preferredFace != null) {
            PlaceUtil.Aim aim = PlaceUtil.findAim(target, preferredFace, eyes, baseYaw, basePitch, distance);
            if (aim != null) return aim;
        }
        for (EnumFacing face : FACE_PRIORITY) {
            if (face == preferredFace) continue;
            PlaceUtil.Aim aim = PlaceUtil.findAim(target, face, eyes, baseYaw, basePitch, distance);
            if (aim != null) return aim;
        }
        return null;
    }

    private static final class TargetCandidate {
        final BlockPos pos;
        final EnumFacing preferredFace;
        // true = hypixel through-wall bed dig; skip raytraceMatches, use findAimUnchecked
        final boolean throughWalls;

        TargetCandidate(BlockPos pos, EnumFacing preferredFace) {
            this(pos, preferredFace, false);
        }

        TargetCandidate(BlockPos pos, EnumFacing preferredFace, boolean throughWalls) {
            this.pos = pos;
            this.preferredFace = preferredFace;
            this.throughWalls = throughWalls;
        }
    }
}
