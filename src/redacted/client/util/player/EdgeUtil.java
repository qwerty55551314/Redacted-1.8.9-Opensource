package redacted.client.util.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public final class EdgeUtil {
    public static final double EDGE_LOOKAHEAD = 0.26D;
    public static final double DIRECTION_EPSILON = 1.0E-4D;

    private static final double[][] CARDINALS = {
            {1.0D, 0.0D}, {-1.0D, 0.0D}, {0.0D, 1.0D}, {0.0D, -1.0D}
    };

    private EdgeUtil() {
    }

    public static double[] getInputDirection(final EntityPlayer player, final float forward, final float strafe) {
        if (forward == 0.0F && strafe == 0.0F) {
            return new double[]{0.0D, 0.0D};
        }

        final double yaw = Math.toRadians(player.rotationYaw);
        final double directionX = forward * -Math.sin(yaw) + strafe * Math.cos(yaw);
        final double directionZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        return new double[]{directionX, directionZ};
    }

    public static boolean hasDirection(final double directionX, final double directionZ) {
        return directionX * directionX + directionZ * directionZ > DIRECTION_EPSILON;
    }

    public static boolean isAtEdge(final EntityPlayer player, final World world, final double directionX, final double directionZ) {
        return isAtEdge(player, world, directionX, directionZ, EDGE_LOOKAHEAD);
    }

    // caller-chosen probe distance, for gates that have to fire before the default 0.26 would
    // (e.g. a jump decision at sprint speed covers more than that in a single tick)
    public static boolean isAtEdge(final EntityPlayer player, final World world, final double directionX, final double directionZ, final double lookahead) {
        final double length = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (length <= DIRECTION_EPSILON) {
            return false;
        }

        final double offsetX = directionX / length * lookahead;
        final double offsetZ = directionZ / length * lookahead;
        final AxisAlignedBB probe = player.getEntityBoundingBox().offset(offsetX, -1.0D, offsetZ);
        return !hasGround(player, world, probe);
    }

    // true if there's a drop on any side of where we're stood; lets the bridge sneak hold
    // whether we're moving or standing still, instead of cutting out the moment input stops
    public static boolean isAtAnyEdge(final EntityPlayer player, final World world) {
        for (final double[] dir : CARDINALS) {
            if (isAtEdge(player, world, dir[0], dir[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGround(final EntityPlayer player, final World world, final AxisAlignedBB probe) {
        return !world.getCollidingBoundingBoxes(player, probe).isEmpty();
    }
}
