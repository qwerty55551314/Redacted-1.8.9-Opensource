package redacted.client.module.impl.world;

import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.player.MovementUtils;
import redacted.client.util.world.PlaceUtil;
import redacted.client.value.impl.NumberValue;
import redacted.util.RotationUtil;

// emergency clutch: get knocked off a bedwars island and the moment you're falling over open air,
// hold sneak and it re-bridges a block back under you against whatever support is still in reach.
// shares Scaffold's surviving pattern -- hold the backward-and-down look full-time, queue the place a
// tick so it lands under the rotation the server already saw, never raytrace (the in-bounds hitVec
// carries it). places straight down only (no travel lookahead -- you're falling, not bridging).
public class Clutch extends Module {

    private static final EnumFacing[] SUPPORT_PRIORITY = {
            EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };
    private static final double PLACE_RANGE = 4.5D;
    private static final float ROTATION_SPEED = 180.0F;
    private static final float SPOOF_PITCH = 88.0F;
    private static final float SETTLE_ALIGN = 5.0F;
    // the look settling onto the spoof is ~instant at 180/tick; gate the place on it anyway so the very
    // first place never fires under a half-turned yaw.
    private static final int ARM_TICKS = 10;   // window stays open this long after the last over-air detection

    private final NumberValue placeDelay = new NumberValue("Place Delay", this, 0.0D, 0.0D, 10.0D, 1.0D).describe("Air-ticks to fall before placing");
    private final NumberValue groundDistance = new NumberValue("Ground Distance", this, 10.0D, 1.0D, 30.0D, 1.0D).describe("How far below counts as open air");

    private Target queuedTarget;
    private int offGroundTicks;
    private int armed;            // counts down each tick; > 0 means we're inside the clutch window
    private boolean settling;     // holding the wire but the look hasn't landed on the spoof yet
    private boolean holding;      // do we own the rotation wire right now? guards release()
    private int previousSlot = -1;
    private Scaffold scaffold;

    public Clutch() {
        super("Clutch", "Hold sneak while falling to bridge a block back under yourself.",
                Keyboard.KEY_NONE, Category.WORLD);
    }

    @Override
    public void onDisable() {
        offGroundTicks = 0;
        armed = 0;
        disarm();
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;

        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || mc.currentScreen != null) {
            disarm();
            return;
        }

        offGroundTicks = mc.thePlayer.onGround ? 0 : offGroundTicks + 1;

        // let Scaffold own the wire -- never both reach for it the same tick
        if (scaffoldEnabled()) {
            disarm();
            return;
        }

        // arm the moment we're genuinely dropping with nothing under us
        if (offGroundTicks > 3 && !MovementUtils.isBlockUnder(groundDistance.doubleValue())) {
            armed = ARM_TICKS;
        }

        // stop the moment we land -- no point holding the look / placing while standing safe
        final boolean active = mc.gameSettings.keyBindSneak.isKeyDown() && armed > 0
                && !mc.thePlayer.onGround && ensureBlockSlot();
        if (armed > 0) armed--;

        if (!active) {
            disarm();
            return;
        }

        if (!holding) {
            holding = true;
            settling = true;
            // remap WASD onto the spoofed backward yaw so any input mid-fall still reads camera-correct
            RotationUtil.setMovementFix(true);
        }

        // backward-and-down look held full-time so the wire never times out back to the camera
        final float base = mc.thePlayer.rotationYaw;
        RotationUtil.setRotation(base - 180.0F, SPOOF_PITCH, ROTATION_SPEED);
        if (settling && RotationUtil.getRotationDifference(RotationUtil.getServerYaw(),
                RotationUtil.getServerPitch(), base - 180.0F, SPOOF_PITCH) <= SETTLE_ALIGN) {
            settling = false;
        }
        if (settling || offGroundTicks < placeDelay.intValue()) {
            queuedTarget = null;
            return;
        }

        // flush last tick's queue -- tick T's spoof C03 is already on the wire, so the place lands under
        // the rotation the server sees. revalidate the spot is still buildable before firing (invariant #3).
        if (queuedTarget != null) {
            placeQueuedTarget();
            queuedTarget = null;
        }
        queuedTarget = findTarget();
    }

    private void placeQueuedTarget() {
        if (queuedTarget == null) return;
        if (!PlaceUtil.isStillPlaceable(queuedTarget.place, queuedTarget.support, queuedTarget.face, PLACE_RANGE)) return;

        final ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (!PlaceUtil.isPlaceableBlock(stack)) return;

        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack,
                queuedTarget.support, queuedTarget.face, queuedTarget.hitVec);
        mc.thePlayer.swingItem();
    }

    // the air cell directly under the feet, supported by any reachable neighbour (the island edge we
    // just dropped past). DOWN first (a block beneath), then the four sides for the knocked-off-a-ledge case.
    private Target findTarget() {
        final int planeY = new BlockPos(mc.thePlayer).down().getY();
        final BlockPos place = new BlockPos(mc.thePlayer.posX, planeY, mc.thePlayer.posZ);
        if (!PlaceUtil.isReplaceable(place)) return null;

        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        for (final EnumFacing side : SUPPORT_PRIORITY) {
            final BlockPos support = place.offset(side);
            final EnumFacing face = side.getOpposite();
            if (!PlaceUtil.isValidSupport(support, PLACE_RANGE)) continue;
            if (!PlaceUtil.canPlaceOnSide(support, face, held)) continue;
            final Vec3 hitVec = PlaceUtil.getHitVec(support, face,
                    Randoms.between(0.34D, 0.66D), Randoms.between(0.34D, 0.66D));
            return new Target(place, support, face, hitVec);
        }
        return null;
    }

    private boolean ensureBlockSlot() {
        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (PlaceUtil.isPlaceableBlock(held)) return true;

        final int slot = PlaceUtil.findFirstPlaceableHotbarSlot();
        if (slot < 0) return false;
        if (previousSlot < 0) previousSlot = mc.thePlayer.inventory.currentItem;
        mc.playerController.setCurrentItemAndSync(slot);
        return true;
    }

    private void disarm() {
        queuedTarget = null;
        settling = false;
        restoreSlot();
        release();
    }

    private void release() {
        if (!holding) return;
        RotationUtil.beginRelease(ROTATION_SPEED, true);
        holding = false;
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && mc.thePlayer != null && mc.playerController != null) {
            mc.playerController.setCurrentItemAndSync(previousSlot);
        }
        previousSlot = -1;
    }

    private boolean scaffoldEnabled() {
        if (scaffold == null) {
            scaffold = Client.getInstance().getModuleManager().getModule(Scaffold.class);
        }
        return scaffold != null && scaffold.isEnabled();
    }

    private static final class Target {
        private final BlockPos place;
        private final BlockPos support;
        private final EnumFacing face;
        private final Vec3 hitVec;

        private Target(BlockPos place, BlockPos support, EnumFacing face, Vec3 hitVec) {
            this.place = place;
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}
