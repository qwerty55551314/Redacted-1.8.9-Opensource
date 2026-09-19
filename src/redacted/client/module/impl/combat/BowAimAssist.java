package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.CombatManager;
import redacted.util.ProjectileUtil;
import redacted.util.RotationUtil;

/**
 * Solves the bow shot for you: while you're drawing, it picks a target, works out the launch pitch
 * that makes the arc actually land on it (arrows drop), and leads a moving target by its velocity
 * across the arrow's flight time. Silent by default -- the arrow flies to the intercept while your
 * camera stays put, since the server fires along the rotation our movement packets report. The
 * Move Camera toggle mirrors that aim onto your view for a legit look.
 */
public class BowAimAssist extends Module {
    private final BooleanValue moveCamera = new BooleanValue("Move Camera", this, false).describe("Mirror the silent aim onto your view");
    private final NumberValue range = new NumberValue("Range", this, 50, 5, 120, 1).describe("How far to target with the bow");
    private final NumberValue fov = new NumberValue("FOV", this, 90, 5, 180, 1).describe("Cone width to lock a target in");
    private final NumberValue speed = new NumberValue("Speed", this, 30, 1, 180, 1).describe("How fast to turn onto the target");
    private final BooleanValue predict = new BooleanValue("Predict Movement", this, true).describe("Lead moving targets by their velocity");
    private final NumberValue predictionFactor = new NumberValue("Prediction Factor", this, 1.0, 0.0, 2.0, 0.05, predict, true).describe("How hard to lead the target");
    private final BooleanValue assumeFullCharge = new BooleanValue("Assume Full Charge", this, true).describe("Solve as if the bow is fully drawn");
    // entity types come from the shared Target Filter module (disabled filter = target everything)

    // per-target velocity EMA -- damps single-tick jitter while tracking the run/strafe trend
    private static final double VEL_SMOOTHING = 0.5D;
    private int velTrackId = -1;
    private double velX, velY, velZ;
    private boolean aiming;

    public BowAimAssist() {
        super("BowAimAssist", "Aims the bow at entities and leads moving targets", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onDisable() {
        stopAiming();
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            stopAiming();
            return;
        }

        final ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBow)
                || !mc.thePlayer.isUsingItem() || mc.thePlayer.getItemInUse() != held) {
            stopAiming();
            return;
        }

        final double arrowSpeed = assumeFullCharge.getValue()
                ? ProjectileUtil.fullDrawVelocity()
                : ProjectileUtil.bowVelocity(held);
        if (arrowSpeed <= 0.0D) {     // not drawn far enough to fire -- nothing stable to solve yet
            stopAiming();
            return;
        }

        final EntityLivingBase target = findTarget();
        if (target == null) {
            stopAiming();
            return;
        }

        trackVelocity(target);

        final Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        final Vec3 aimPoint = new Vec3(target.posX, target.posY + target.height * 0.5D, target.posZ);
        final Vec3 lead = predict.getValue()
                ? new Vec3(velX * predictionFactor.doubleValue(),
                           velY * predictionFactor.doubleValue(),
                           velZ * predictionFactor.doubleValue())
                : new Vec3(0.0D, 0.0D, 0.0D);

        final ProjectileUtil.Aim aim = ProjectileUtil.solveLeadAim(eyes, aimPoint, lead, arrowSpeed);

        RotationUtil.setRotationDirect(aim.yaw, aim.pitch, speed.floatValue());
        aiming = true;

        if (moveCamera.getValue()) {
            // mirror the silent aim onto the real view. vanilla snapshots prevRotation = rotation in
            // EntityLivingBase.onUpdate during the updateEntities that runs right after this PRE, so
            // there's no prev bookkeeping to do here -- the camera just rides serverYaw at tick rate.
            mc.thePlayer.rotationYaw = RotationUtil.getServerYaw();
            mc.thePlayer.rotationPitch = RotationUtil.getServerPitch();
        }
    }

    private EntityLivingBase findTarget() {
        // FOV is measured off the real camera on purpose: it's the cone the player is actually
        // looking through, so they aim the bow at the rough area and we lock the target in it.
        // (Measuring off the reported rotation would be useless -- we aim the wire AT the target,
        // so every candidate would sit at ~0 deg and the gate would never reject anything.)
        final float camYaw = mc.thePlayer.rotationYaw;
        final float camPitch = mc.thePlayer.rotationPitch;
        final double rangeSq = range.doubleValue() * range.doubleValue();

        EntityLivingBase best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (final CombatManager.Candidate c : CombatManager.candidates()) {
            final EntityLivingBase e = c.entity();
            if (!TargetFilter.allows(e)) continue;
            final double reachSq = c.reachSq();
            if (reachSq > rangeSq) continue;
            final float[] rot = c.rotations();
            if (RotationUtil.getRotationDifference(camYaw, camPitch, rot[0], rot[1]) > fov.doubleValue()) continue;
            if (reachSq < bestDistSq) {
                bestDistSq = reachSq;
                best = e;
            }
        }
        return best;
    }

    private void trackVelocity(final EntityLivingBase e) {
        final double mx = e.posX - e.prevPosX;
        final double my = e.posY - e.prevPosY;
        final double mz = e.posZ - e.prevPosZ;
        if (e.getEntityId() != velTrackId) {
            velTrackId = e.getEntityId();
            velX = mx;
            velY = my;
            velZ = mz;
            return;
        }
        velX += (mx - velX) * VEL_SMOOTHING;
        velY += (my - velY) * VEL_SMOOTHING;
        velZ += (mz - velZ) * VEL_SMOOTHING;
    }

    private void stopAiming() {
        if (aiming) {
            RotationUtil.beginRelease(speed.floatValue());
            aiming = false;
        }
        velTrackId = -1;
        velX = velY = velZ = 0.0D;
    }
}
