package redacted.client.util.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import redacted.client.util.player.InventoryUtil;
import redacted.util.RaycastUtil;
import redacted.util.RotationUtil;

// generic block-placement helpers shared by Scaffold / BedProtection / BedAura -- block validity,
// face hitvecs, and the rotation/raytrace gate. nothing bed-specific lives here.
public final class PlaceUtil {

    public static final double[] SAMPLES = {0.5D, 0.28D, 0.72D};

    private PlaceUtil() {}

    public static boolean isPlaceableBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == null || block instanceof BlockFalling) return false;   // sand/gravel/anvil fall out
        // glass renders see-through so isFullCube() is false, but it's still a fine block to stand on
        if (block == Blocks.glass || block == Blocks.stained_glass) return true;
        // otherwise it has to be a full solid cube -- knocks out flowers, torches, slabs, stairs,
        // fences, panes, cactus, redstone, carpets, signs, ladders, the lot
        if (!block.isFullCube() || !block.getMaterial().isSolid()) return false;
        // full solid cubes you still don't want underfoot
        return block != Blocks.tnt && block != Blocks.soul_sand;
    }

    public static boolean isReplaceable(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return mc.theWorld.isAirBlock(pos) || block.isReplaceable(mc.theWorld, pos);
    }

    public static boolean isValidSupport(BlockPos pos, double range) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return !mc.theWorld.isAirBlock(pos)
                && block != Blocks.bed
                && !block.isReplaceable(mc.theWorld, pos)
                && RaycastUtil.reachBlock(pos) <= range;
    }

    public static boolean canPlaceOnSide(BlockPos support, EnumFacing face, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null
                && mc.thePlayer != null
                && stack != null
                && stack.getItem() instanceof ItemBlock
                && ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(mc.theWorld, support, face, mc.thePlayer, stack);
    }

    // queued-place revalidation: the spot is still buildable with what's in hand right now
    public static boolean isStillPlaceable(BlockPos place, BlockPos support, EnumFacing face, double range) {
        Minecraft mc = Minecraft.getMinecraft();
        return isReplaceable(place)
                && isValidSupport(support, range)
                && canPlaceOnSide(support, face, mc.thePlayer.inventory.getCurrentItem());
    }

    public static int findFirstPlaceableHotbarSlot() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return -1;
        for (int slot = 0; slot < InventoryUtil.HOTBAR_SIZE; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (isPlaceableBlock(stack)) return slot;
        }
        return -1;
    }

    public static Vec3 getHitVec(BlockPos pos, EnumFacing face, double first, double second) {
        double edge = 0.499D;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        switch (face) {
            case UP:
                x = pos.getX() + first;
                y = pos.getY() + 0.5D + edge;
                z = pos.getZ() + second;
                break;
            case DOWN:
                x = pos.getX() + first;
                y = pos.getY() + 0.5D - edge;
                z = pos.getZ() + second;
                break;
            case NORTH:
                x = pos.getX() + first;
                y = pos.getY() + second;
                z = pos.getZ() + 0.5D - edge;
                break;
            case SOUTH:
                x = pos.getX() + first;
                y = pos.getY() + second;
                z = pos.getZ() + 0.5D + edge;
                break;
            case WEST:
                x = pos.getX() + 0.5D - edge;
                y = pos.getY() + first;
                z = pos.getZ() + second;
                break;
            case EAST:
                x = pos.getX() + 0.5D + edge;
                y = pos.getY() + first;
                z = pos.getZ() + second;
                break;
        }
        return new Vec3(x, y, z);
    }

    // full gate: block AND sideHit equality (see traceFace). a steep backward look crosses the
    // support's TOP face while the C08 declares a side face, so this can only pass for a scaffold
    // that solves its pitch per target instead of holding a fixed one -- which is what Scaffold does.
    // grim raytraces at exactly 4.5 -- don't pass range slack on a grim-targeting path.
    public static boolean raytraceMatches(BlockPos pos, EnumFacing face, float yaw, float pitch, double distance) {
        return traceFace(pos, face, yaw, pitch, distance) != null;
    }

    // first block the look ray hits within distance, iff it lands on this support's given face
    public static MovingObjectPosition traceFace(BlockPos pos, EnumFacing face, float yaw, float pitch, double distance) {
        MovingObjectPosition hit = RaycastUtil.rayTraceBlocks(yaw, pitch, distance);
        boolean matches = hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && pos.equals(hit.getBlockPos())
                && face == hit.sideHit;
        return matches ? hit : null;
    }

    public static Aim findAim(BlockPos target, EnumFacing face, Vec3 eyes,
                              float baseYaw, float basePitch, double distance) {
        for (double first : SAMPLES) {
            for (double second : SAMPLES) {
                Vec3 hitVec = getHitVec(target, face, first, second);
                float[] raw = RotationUtil.getRotations(eyes, hitVec);
                float yaw = RotationUtil.applyGCD(baseYaw, raw[0]);
                float pitch = RotationUtil.clampPitch(RotationUtil.applyGCD(basePitch, raw[1]));
                if (raytraceMatches(target, face, yaw, pitch, distance)) {
                    return new Aim(face, yaw, pitch, hitVec);
                }
            }
        }
        return null;
    }

    // single face-center hitVec, skips the raytrace gate -- for through-wall bed-break on hypixel
    public static Aim findAimUnchecked(BlockPos target, EnumFacing face, Vec3 eyes,
                                       float baseYaw, float basePitch) {
        Vec3 hitVec = getHitVec(target, face, 0.5D, 0.5D);
        float[] raw = RotationUtil.getRotations(eyes, hitVec);
        float yaw = RotationUtil.applyGCD(baseYaw, raw[0]);
        float pitch = RotationUtil.clampPitch(RotationUtil.applyGCD(basePitch, raw[1]));
        return new Aim(face, yaw, pitch, hitVec);
    }

    public static final class Aim {
        public final EnumFacing face;
        public final float yaw;
        public final float pitch;
        public final Vec3 hitVec;

        public Aim(EnumFacing face, float yaw, float pitch, Vec3 hitVec) {
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
            this.hitVec = hitVec;
        }
    }
}
