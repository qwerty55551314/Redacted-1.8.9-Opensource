package redacted.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class RaycastUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final double[] SAMPLE_FACTORS = { 0.1D, 0.5D, 0.9D };

    private RaycastUtil() {
    }

    public static double reach(final Entity entity, final Vec3 eyes) {
        return Math.sqrt(reachSq(entity, eyes));
    }

    public static double reachSq(final Entity entity, final Vec3 eyes) {
        if (entity == null || eyes == null) {
            return 0.0D;
        }

        final AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null) {
            return 0.0D;
        }

        final double x = clamp(eyes.xCoord, box.minX, box.maxX);
        final double y = clamp(eyes.yCoord, box.minY, box.maxY);
        final double z = clamp(eyes.zCoord, box.minZ, box.maxZ);
        final double dx = eyes.xCoord - x;
        final double dy = eyes.yCoord - y;
        final double dz = eyes.zCoord - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean hasLineOfSight(final Entity entity) {
        if (entity == null || mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }

        final AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null) {
            return false;
        }

        final Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        if (box.isVecInside(eyes)) {
            return true;
        }

        for (final double fx : SAMPLE_FACTORS) {
            for (final double fy : SAMPLE_FACTORS) {
                for (final double fz : SAMPLE_FACTORS) {
                    final Vec3 point = new Vec3(
                            lerp(box.minX, box.maxX, fx),
                            lerp(box.minY, box.maxY, fy),
                            lerp(box.minZ, box.maxZ, fz));
                    // null == the ray reached the point with no block in the way.
                    if (mc.theWorld.rayTraceBlocks(eyes, point, false, true, false) == null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // true if a look ray at this yaw/pitch actually lands on the hitbox within range and nothing's in the way
    public static boolean aimRayHitsEntity(final Entity entity, final float yaw, final float pitch,
                                             final double maxRange) {
        if (entity == null || mc.thePlayer == null || mc.theWorld == null || maxRange <= 0.0D) {
            return false;
        }

        final AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null) {
            return false;
        }

        final Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        if (box.isVecInside(eyes)) {
            return true;
        }

        final Vec3 look = mc.thePlayer.getVectorForRotation(pitch, yaw);
        final Vec3 end = eyes.addVector(
                look.xCoord * maxRange,
                look.yCoord * maxRange,
                look.zCoord * maxRange);

        final float border = entity.getCollisionBorderSize();
        final AxisAlignedBB expanded = box.expand(border, border, border);
        final MovingObjectPosition entityHit = expanded.calculateIntercept(eyes, end);
        if (entityHit == null) {
            return false;
        }

        if (eyes.distanceTo(entityHit.hitVec) > maxRange) {
            return false;
        }

        return mc.theWorld.rayTraceBlocks(eyes, entityHit.hitVec, false, true, false) == null;
    }

    public static double reachBlock(final BlockPos pos) {
        if (pos == null || mc.thePlayer == null) {
            return 0.0D;
        }

        final AxisAlignedBB box = new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        final Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        final double x = clamp(eyes.xCoord, box.minX, box.maxX);
        final double y = clamp(eyes.yCoord, box.minY, box.maxY);
        final double z = clamp(eyes.zCoord, box.minZ, box.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    public static MovingObjectPosition rayTraceBlocks(final float yaw, final float pitch, final double distance) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        final Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        final Vec3 look = mc.thePlayer.getVectorForRotation(pitch, yaw);
        final Vec3 end = eyes.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
    }

    private static double clamp(final double value, final double min, final double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static double lerp(final double min, final double max, final double t) {
        return min + (max - min) * t;
    }
}
