package redacted.client.util.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import redacted.util.RaycastUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// bed scanning + break targeting: locate the nearest bed, check exposure, pick which adjacent to
// break. generic placement/aim helpers live in PlaceUtil.
public final class BedTargetingUtil {

    private BedTargetingUtil() {}

    public static BlockPos footOf(BlockPos pos, IBlockState state) {
        if (state.getBlock() != Blocks.bed) return null;
        EnumFacing facing = (EnumFacing) state.getValue(BlockBed.FACING);
        return state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD ? pos.offset(facing.getOpposite()) : pos;
    }

    public static Bed findNearestBed(int cubeRange, double maxDistanceSq) {
        return findNearestBed(cubeRange, maxDistanceSq, null);
    }

    public static Bed findNearestBed(int cubeRange, double maxDistanceSq, BlockPos excludeFoot) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return null;

        BlockPos playerPos = new BlockPos(mc.thePlayer);
        Bed best = null;
        double bestDistSq = Double.MAX_VALUE;
        Set<BlockPos> seenFeet = new HashSet<>();

        for (int x = -cubeRange; x <= cubeRange; x++) {
            for (int y = -cubeRange; y <= cubeRange; y++) {
                for (int z = -cubeRange; z <= cubeRange; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    if (state.getBlock() != Blocks.bed) continue;

                    BlockPos foot = footOf(pos, state);
                    if (!seenFeet.add(foot)) continue;
                    // our own bed -- never target it (null excludeFoot -> equals is false, no skip)
                    if (foot.equals(excludeFoot)) continue;

                    IBlockState footState = mc.theWorld.getBlockState(foot);
                    if (footState.getBlock() != Blocks.bed) continue;
                    EnumFacing facing = (EnumFacing) footState.getValue(BlockBed.FACING);
                    BlockPos head = foot.offset(facing);
                    if (mc.theWorld.getBlockState(head).getBlock() != Blocks.bed) continue;

                    double cx = (foot.getX() + head.getX() + 1.0D) * 0.5D;
                    double cy = foot.getY() + 0.5D;
                    double cz = (foot.getZ() + head.getZ() + 1.0D) * 0.5D;
                    double distSq = mc.thePlayer.getDistanceSq(cx, cy, cz);
                    if (distSq > maxDistanceSq) continue;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = new Bed(foot, head, facing);
                    }
                }
            }
        }
        return best;
    }

    // the 10 unique positions touching the bed (foot's 6 neighbors + head's 6, minus the two halves)
    public static List<BlockPos> adjacentPositions(Bed bed) {
        Set<BlockPos> set = new HashSet<>();
        for (EnumFacing face : EnumFacing.values()) {
            set.add(bed.foot.offset(face));
            set.add(bed.head.offset(face));
        }
        set.remove(bed.foot);
        set.remove(bed.head);
        return new ArrayList<>(set);
    }

    // hypixel rule: bed is breakable the instant any one of those 10 is air
    public static boolean isBedExposed(Bed bed) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;
        for (BlockPos pos : adjacentPositions(bed)) {
            if (mc.theWorld.isAirBlock(pos)) return true;
        }
        return false;
    }

    public static BlockPos pickAdjacentToBreak(Bed bed, Vec3 eyes, double maxRange) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return null;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos pos : adjacentPositions(bed)) {
            // floor under the bed is AC-quirky on some servers, skip
            if (pos.equals(bed.foot.down()) || pos.equals(bed.head.down())) continue;

            IBlockState state = mc.theWorld.getBlockState(pos);
            Block block = state.getBlock();
            if (block == Blocks.air || block == Blocks.bedrock) continue;
            if (block.getBlockHardness(mc.theWorld, pos) < 0.0F) continue;
            if (RaycastUtil.reachBlock(pos) > maxRange) continue;

            Vec3 posCenter = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            double score = eyes.squareDistanceTo(posCenter);

            // strong boost when nothing's between us and this adjacent
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, posCenter, false, false, false);
            boolean losClear = hit != null
                    && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && pos.equals(hit.getBlockPos());
            if (losClear) score *= 0.3D;

            // small bonus for the top of the bed (UP face is easiest to aim at from above)
            if (pos.equals(bed.foot.up()) || pos.equals(bed.head.up())) score *= 0.8D;

            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    public static final class Bed {
        public final BlockPos foot;
        public final BlockPos head;
        public final EnumFacing facing;

        public Bed(BlockPos foot, BlockPos head, EnumFacing facing) {
            this.foot = foot;
            this.head = head;
            this.facing = facing;
        }
    }
}
