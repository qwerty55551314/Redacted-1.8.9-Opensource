package redacted.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;

public final class MovementUtils {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private MovementUtils() {}

    public static boolean isMoving() {
        return mc.thePlayer.movementInput.moveForward != 0 || mc.thePlayer.movementInput.moveStrafe != 0;
    }

    // any WASD key held, read from live key state (movementInput is tick-scoped and stale per-frame)
    public static boolean anyMoveKeyDown() {
        return mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
    }

    // absolute world-space yaw the WASD input points at (camera yaw folded in); camera yaw when idle.
    // matches Entity.moveFlying's motion vector: forward=W(+)/S(-), strafe=A(+)/D(-).
    public static float movementYaw(final EntityPlayerSP player) {
        final float forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1.0F : 0.0F)
                - (mc.gameSettings.keyBindBack.isKeyDown() ? 1.0F : 0.0F);
        final float strafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1.0F : 0.0F)
                - (mc.gameSettings.keyBindRight.isKeyDown() ? 1.0F : 0.0F);
        if (forward == 0.0F && strafe == 0.0F) {
            return player.rotationYaw;
        }
        return player.rotationYaw + (float) Math.toDegrees(Math.atan2(-strafe, forward));
    }

    // vanilla sprint-start eligibility: forward input, nothing blocking it, food or fly allowed
    public static boolean canSprint(final EntityPlayerSP player) {
        return mc.gameSettings.keyBindForward.isKeyDown()
                && !player.isCollidedHorizontally
                && !player.isSneaking()
                && !player.isUsingItem()
                && (player.capabilities.allowFlying || player.getFoodStats().getFoodLevel() > 6);
    }

    // pos-delta based (blocks moved this tick), unlike the input-based isMoving
    public static double horizontalSpeed(final EntityLivingBase e) {
        final double mx = e.posX - e.prevPosX;
        final double mz = e.posZ - e.prevPosZ;
        return Math.sqrt(mx * mx + mz * mz);
    }

    public static boolean isOnGround() {
        return mc.thePlayer.onGround && mc.thePlayer.isCollidedVertically;
    }

    public static void stop() {
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionZ = 0;
    }

    // block within `depth` below the feet? XZ shrunk a hair so a flush wall beside us doesn't read as ground.
    public static boolean isBlockUnder(final double depth) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        final AxisAlignedBB below = mc.thePlayer.getEntityBoundingBox()
                .addCoord(0.0D, -depth, 0.0D).contract(0.001D, 0.0D, 0.001D);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, below).isEmpty();
    }

    // anything solid between the feet and the void floor (y=0)? the "am I over the void" check
    public static boolean isBlockUnder() {
        return mc.thePlayer != null && isBlockUnder(Math.max(0.0D, mc.thePlayer.posY));
    }

    public static void setSpeed(double speed) {
        setSpeed(speed, mc.thePlayer.rotationYaw, mc.thePlayer.movementInput.moveStrafe, mc.thePlayer.movementInput.moveForward);
    }

    public static void setSpeed(double speed, float yaw, double strafe, double forward) {
        if (forward == 0 && strafe == 0) {
            stop();
            return;
        }

        if (forward != 0) {
            if (strafe > 0) yaw += forward > 0 ? -45 : 45;
            else if (strafe < 0) yaw += forward > 0 ? 45 : -45;
            strafe = 0;
            forward = forward > 0 ? 1 : -1;
        }

        if (strafe > 0) strafe = 1;
        else if (strafe < 0) strafe = -1;

        double mx = Math.cos(Math.toRadians(yaw + 90));
        double mz = Math.sin(Math.toRadians(yaw + 90));
        mc.thePlayer.motionX = forward * speed * mx + strafe * speed * mz;
        mc.thePlayer.motionZ = forward * speed * mz - strafe * speed * mx;
    }

    public static double direction(float rotationYaw, double forward, double strafe) {
        if (forward < 0) rotationYaw += 180;
        float fwd = forward < 0 ? -0.5F : forward > 0 ? 0.5F : 1F;
        if (strafe > 0) rotationYaw -= 90 * fwd;
        if (strafe < 0) rotationYaw += 90 * fwd;
        return Math.toRadians(rotationYaw);
    }

    // hard-set horizontal motion to `speed` b/t along the WASD travel direction (camera yaw + input
    // angle), independent of the wire-spoofed look. negative speed walks backward -- used as the
    // place-tick brake. no-op with no movement input. call from EventTick.PRE where rotationYaw is the
    // real camera yaw (the spoof only swaps it inside onUpdateWalkingPlayer).
    public static void strafe(final double speed) {
        final double forward = mc.thePlayer.movementInput.moveForward;
        final double strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0 && strafe == 0) return;
        final double yaw = direction(mc.thePlayer.rotationYaw, forward, strafe);
        mc.thePlayer.motionX = -Math.sin(yaw) * speed;
        mc.thePlayer.motionZ = Math.cos(yaw) * speed;
    }
}
