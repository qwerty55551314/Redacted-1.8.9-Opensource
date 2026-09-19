package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventFrame;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.NumberValue;
import redacted.client.util.entity.EntityUtil;
import redacted.client.util.render.ESPUtil;
import redacted.util.RaycastUtil;
import redacted.util.RotationUtil;

public class AimAssist extends Module {
    private final NumberValue fov = new NumberValue("FOV", this, 60.0, 5.0, 180.0, 1.0).describe("Cone width to grab a target in");
    private final NumberValue range = new NumberValue("Range", this, 4.5, 1.0, 8.0, 0.1).describe("How far to pull aim toward");
    private final NumberValue speed = new NumberValue("Speed", this, 3.0, 0.1, 10.0, 0.1).describe("How fast the aim drags");
    // entity types come from the shared Target Filter module (disabled filter = target everything)

    private EntityLivingBase target;
    private long lastFrameNanos;
    private float yawResidual;
    private float pitchResidual;

    public AimAssist() {
        super("AimAssist", "Pulls aim toward nearest entity", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onDisable() {
        clearAimState();
    }

    private void clearAimState() {
        target = null;
        lastFrameNanos = 0;
        yawResidual = 0;
        pitchResidual = 0;
    }

    // real-camera aim: quantize deltas to the player's mouse GCD so the wire reads as hand movement
    @EventTarget
    public void onFrame(EventFrame event) {
        // a silent-rotation module holding the wire owns aim
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !mc.inGameHasFocus
                || RotationUtil.isActive()) {
            clearAimState();
            return;
        }

        float partial = event.getPartialTicks();
        EntityLivingBase prev = target;
        target = findTarget(partial);

        if (target == null) {
            clearAimState();
            return;
        }

        if (target != prev) {
            yawResidual = 0;
            pitchResidual = 0;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            return;
        }
        float frameDelta = Math.min((now - lastFrameNanos) / 50_000_000f, 2.0f);
        lastFrameNanos = now;

        float[] rot = rotationsTo(target, partial);
        float yawDiff = MathHelper.wrapAngleTo180_float(rot[0] - mc.thePlayer.rotationYaw);
        float pitchDiff = rot[1] - mc.thePlayer.rotationPitch;

        float maxStep = speed.floatValue() * frameDelta;

        float nextYaw = step(mc.thePlayer.rotationYaw, yawDiff, maxStep, true);
        float nextPitch = step(mc.thePlayer.rotationPitch, pitchDiff, maxStep, false);
        float yawStep = MathHelper.wrapAngleTo180_float(nextYaw - mc.thePlayer.rotationYaw);
        float pitchStep = nextPitch - mc.thePlayer.rotationPitch;

        if (Math.abs(yawStep) > 0.0F) {
            mc.thePlayer.rotationYaw = nextYaw;
            mc.thePlayer.prevRotationYaw += yawStep;
        }
        if (Math.abs(pitchStep) > 0.0F) {
            float old = mc.thePlayer.rotationPitch;
            mc.thePlayer.rotationPitch = nextPitch;
            mc.thePlayer.prevRotationPitch += mc.thePlayer.rotationPitch - old;
        }
    }

    private float step(float current, float diff, float maxStep, boolean yaw) {
        // park inside one mouse unit instead of dithering around the target
        if (Math.abs(diff) <= RotationUtil.getGCD()) {
            if (yaw) yawResidual = 0; else pitchResidual = 0;
            return yaw ? current : RotationUtil.clampPitch(current);
        }

        float wanted = MathHelper.clamp_float(diff, -maxStep, maxStep);
        float residual = yaw ? yawResidual : pitchResidual;
        float total = wanted + residual;

        float next = RotationUtil.applyGCD(current, current + total);
        float applied = next - current;
        if (yaw) yawResidual = total - applied;
        else pitchResidual = total - applied;

        return yaw ? next : RotationUtil.clampPitch(next);
    }

    private float[] rotationsTo(EntityLivingBase e, float partial) {
        return RotationUtil.getRotations(interpEyes(mc.thePlayer, partial), interpEyes(e, partial));
    }

    private Vec3 interpEyes(EntityLivingBase e, float partial) {
        double[] p = ESPUtil.getInterpolatedPos(e, partial);
        return new Vec3(p[0], p[1] + e.getEyeHeight(), p[2]);
    }

    private EntityLivingBase findTarget(float partial) {
        EntityLivingBase closest = null;
        double closestDist = range.getValue();

        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityLivingBase)) continue;
            EntityLivingBase e = (EntityLivingBase) o;

            if (!EntityUtil.isValidTarget(e)) continue;
            if (!TargetFilter.allows(e)) continue;

            double dist = mc.thePlayer.getDistanceToEntity(e);
            if (dist > closestDist) continue;

            float[] rot = rotationsTo(e, partial);
            if (RotationUtil.getRotationDifference(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, rot[0], rot[1]) > fov.getValue()) continue;

            if (!RaycastUtil.hasLineOfSight(e)) continue;

            closestDist = dist;
            closest = e;
        }

        return closest;
    }
}
