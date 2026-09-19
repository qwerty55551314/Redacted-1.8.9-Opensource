package redacted.client.module.impl.world;

import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.player.MovementUtils;
import redacted.client.value.impl.NumberValue;
import redacted.util.RotationUtil;

public class AutoPearl extends Module {

    private static final float ROTATION_SPEED = 180.0F;
    private static final float SETTLE_ALIGN = 5.0F;
    private static final int ARM_OFF_GROUND_TICKS = 2;
    private static final int RETRY_TICKS = 40;   // still falling this long after a throw: retry

    private final NumberValue voidDepth = new NumberValue("Void Depth", this, 4.0, 3.0, 60.0, 1.0)
            .describe("No blocks within this far below counts as over the void");

    private Vec3 lastSafe;
    private int offGroundTicks;
    private boolean settling;     // wire held, look not yet on target
    private boolean holding;      // owns the rotation wire
    private boolean thrown;       // one throw per fall, until RETRY_TICKS re-arms
    private int thrownTicks;
    private int pearlSlot = -1;   // also acts as the "already switched" flag
    private int previousSlot = -1;

    public AutoPearl() {
        super("AutoPearl", "Throw an ender pearl back to safety when falling into the void.",
                Keyboard.KEY_NONE, Category.WORLD);
    }

    @Override
    public void onDisable() {
        disarm();
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || mc.currentScreen != null) {
            disarm();
            return;
        }

        if (mc.thePlayer.onGround) {
            lastSafe = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            offGroundTicks = 0;
            thrown = false;
            thrownTicks = 0;
            disarm();
            return;
        }

        offGroundTicks++;
        if (lastSafe == null) return;

        final boolean overVoid = !MovementUtils.isBlockUnder(voidDepth.doubleValue());

        // still descending long after the throw: that pearl died in the void, so re-arm
        if (thrown && ++thrownTicks >= RETRY_TICKS && overVoid) thrown = false;

        // motionY < 0 keeps knockback pop-ups from arming us
        if (thrown || !overVoid || offGroundTicks <= ARM_OFF_GROUND_TICKS
                || mc.thePlayer.motionY >= 0.0D || findPearlSlot() < 0) {
            disarm();
            return;
        }

        if (!holding) {
            holding = true;
            settling = true;
            RotationUtil.setMovementFix(true);   // keep WASD aligned with the spoofed yaw
        }

        // a block above the last ground we touched, so the pearl clears the lip
        final float[] want = RotationUtil.getRotations(mc.thePlayer,
                lastSafe.xCoord, lastSafe.yCoord + 1.0D, lastSafe.zCoord);
        RotationUtil.setRotation(want[0], want[1], ROTATION_SPEED);
        if (settling && RotationUtil.getRotationDifference(RotationUtil.getServerYaw(),
                RotationUtil.getServerPitch(), want[0], want[1]) <= SETTLE_ALIGN) {
            settling = false;
        }
        if (settling) return;

        if (pearlSlot < 0) {                     // switch this tick, throw the next
            pearlSlot = findPearlSlot();
            previousSlot = mc.thePlayer.inventory.currentItem;
            mc.playerController.setCurrentItemAndSync(pearlSlot);
            return;
        }

        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemEnderPearl)) { disarm(); return; }
        // C08 alone this tick: disarm() restores the slot next tick, keeping the C09 out of this window
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        thrown = true;
        thrownTicks = 0;
    }

    private int findPearlSlot() {
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemEnderPearl) return slot;
        }
        return -1;
    }

    private void disarm() {
        InventoryUtil.restoreHotbarSlot(previousSlot, pearlSlot);
        previousSlot = pearlSlot = -1;
        settling = false;
        if (holding) {
            RotationUtil.beginRelease(ROTATION_SPEED, true);
            holding = false;
        }
    }
}
