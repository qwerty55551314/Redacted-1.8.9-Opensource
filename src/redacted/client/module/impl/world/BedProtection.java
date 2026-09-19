package redacted.client.module.impl.world;

import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.combat.SilentAura;
import redacted.client.util.world.BedTargetingUtil;
import redacted.client.util.world.PlaceUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.RotationUtil;

public class BedProtection extends Module {

    private static final EnumFacing[] SUPPORT_PRIORITY = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP
    };

    // full 1-layer cover, bed-relative {length, side, up}: length runs foot->head,
    // side is facing.rotateY(). order = build priority AND support dependency: the
    // bed-top blocks can't clip onto the bed itself (isValidSupport rejects beds),
    // so the ends go up first and the top blocks place sideways against them
    private static final int[][] HYPIXEL_COVER = {
            {-1, 0, 0}, {2, 0, 0},              // behind foot, beyond head
            {-1, 0, 1}, {2, 0, 1},              // above the ends, support for the bed top
            {0, 0, 1}, {1, 0, 1},               // above foot, above head
            {0, -1, 0}, {0, 1, 0},              // foot sides
            {1, -1, 0}, {1, 1, 0},              // head sides
    };

    private final ModeValue mode = new ModeValue("Mode", this, "Hypixel", "Hypixel").describe("Bed cover layout to build");
    private final NumberValue range = new NumberValue("Range", this, 4.5D, 1.0D, 4.5D, 0.1D).describe("How far to reach for beds and supports");
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", this, 160.0, 30.0, 200.0, 5.0).describe("How fast to turn toward each placement");
    private final BooleanValue autoSwitch = new BooleanValue("Auto-Switch", this, true).describe("Auto-grab a block from your hotbar");
    private final NumberValue placeDelay = new NumberValue("Place Delay", this, 2.0, 0.0, 10.0, 1.0).describe("Ticks to wait between placements");

    private Target queuedTarget;
    private int placeCooldown;
    private int restoreSlot;
    private boolean changedSlot;

    public BedProtection() {
        super("BedProtection", "Covers nearby beds with blocks.", Keyboard.KEY_NONE, Category.WORLD);
    }

    @Override
    public void onEnable() {
        queuedTarget = null;
        changedSlot = false;
        placeCooldown = 0;
        if (mc.thePlayer != null) {
            restoreSlot = mc.thePlayer.inventory.currentItem;
        }
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null && autoSwitch.getValue() && changedSlot) {
            // straight to playerController, vanilla's per-tick resync would fire a 2nd C09 (grim BackPacketsA)
            mc.playerController.setCurrentItemAndSync(MathHelper.clamp_int(restoreSlot, 0, 8));
        }
        queuedTarget = null;
        changedSlot = false;
        placeCooldown = 0;
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            queuedTarget = null;
            return;
        }
        if (shouldYield() || !selectBlock()) {
            queuedTarget = null;
            return;
        }

        // place last tick's queue first, rotation already shipped
        if (queuedTarget != null) {
            if (placeQueuedTarget()) {
                placeCooldown = placeDelay.intValue();
            }
            queuedTarget = null;
        }

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        Target nextTarget = findTarget();
        if (nextTarget == null) return;

        // re-check at stepped rotation, GCD snap can land shy of the ideal aim
        RotationUtil.setRotation(nextTarget.yaw, nextTarget.pitch, rotationSpeed.floatValue());
        float steppedYaw = RotationUtil.getServerYaw();
        float steppedPitch = RotationUtil.getServerPitch();
        if (!PlaceUtil.raytraceMatches(nextTarget.support, nextTarget.face, steppedYaw, steppedPitch, 5.0D)) {
            return;
        }
        queuedTarget = new Target(nextTarget.place, nextTarget.support, nextTarget.face, nextTarget.hitVec, steppedYaw, steppedPitch);
    }

    private boolean placeQueuedTarget() {
        if (!isTargetStillValid(queuedTarget)) return false;
        // re-validate at server-confirmed rotation, queued yaw can drift if anyone else writes RotationUtil
        float liveYaw = mc.thePlayer.getLastReportedYaw();
        float livePitch = mc.thePlayer.getLastReportedPitch();
        if (!PlaceUtil.raytraceMatches(queuedTarget.support, queuedTarget.face, liveYaw, livePitch, 5.0D)) return false;

        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (!PlaceUtil.isPlaceableBlock(stack)) return false;

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, stack,
                queuedTarget.support, queuedTarget.face, queuedTarget.hitVec);
        mc.thePlayer.swingItem();
        return true;
    }

    private Target findTarget() {
        int cubeRange = MathHelper.ceiling_double_int(range.getValue());
        double maxDistanceSq = range.getValue() * range.getValue();
        BedTargetingUtil.Bed bed = BedTargetingUtil.findNearestBed(cubeRange, maxDistanceSq);
        if (bed == null) return null;

        EnumFacing right = bed.facing.rotateY();
        for (int[] offset : HYPIXEL_COVER) {
            BlockPos position = bed.foot
                    .offset(bed.facing, offset[0])
                    .offset(right, offset[1])
                    .up(offset[2]);
            if (!PlaceUtil.isReplaceable(position)) continue;
            Target target = buildTarget(position);
            if (target != null) return target;
        }
        return null;
    }

    private Target buildTarget(BlockPos position) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        float baseYaw = mc.thePlayer.getLastReportedYaw();
        float basePitch = mc.thePlayer.getLastReportedPitch();
        for (EnumFacing supportSide : SUPPORT_PRIORITY) {
            BlockPos support = position.offset(supportSide);
            EnumFacing face = supportSide.getOpposite();
            if (!PlaceUtil.isValidSupport(support, range.getValue())) continue;
            if (!PlaceUtil.canPlaceOnSide(support, face, mc.thePlayer.inventory.getCurrentItem())) continue;

            PlaceUtil.Aim aim = PlaceUtil.findAim(support, face, eyes, baseYaw, basePitch, 5.0D);
            if (aim != null) {
                return new Target(position, support, face, aim.hitVec, aim.yaw, aim.pitch);
            }
        }
        return null;
    }

    private boolean isTargetStillValid(Target target) {
        return target != null
                && PlaceUtil.isStillPlaceable(target.place, target.support, target.face, range.getValue());
    }

    private boolean selectBlock() {
        if (autoSwitch.getValue()) {
            int slot = PlaceUtil.findFirstPlaceableHotbarSlot();
            if (slot == -1) return false;
            if (mc.thePlayer.inventory.currentItem != slot) {
                mc.playerController.setCurrentItemAndSync(slot);
                changedSlot = true;
            }
            return true;
        }
        return PlaceUtil.isPlaceableBlock(mc.thePlayer.inventory.getCurrentItem());
    }

    private boolean shouldYield() {
        // let SilentAura's C02 land first, our C08 would split the rotation window
        SilentAura aura = Client.getInstance().getModuleManager().getModule(SilentAura.class);
        return aura != null && aura.isEnabled() && aura.hasActiveRotationTarget();
    }

    private static final class Target {
        private final BlockPos place;
        private final BlockPos support;
        private final EnumFacing face;
        private final Vec3 hitVec;
        private final float yaw;
        private final float pitch;

        private Target(BlockPos place, BlockPos support, EnumFacing face, Vec3 hitVec, float yaw, float pitch) {
            this.place = place;
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
