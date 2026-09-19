package redacted.client.module.impl.movement;

import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.player.EventMoveInput;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.EdgeUtil;
import redacted.client.util.player.InventoryUtil;
import redacted.client.value.impl.NumberValue;

public class BridgeAssist extends Module {

    // only sneak while actually looking down at the bridge -- Meteor's BridgeAssist onLookDown gate.
    // sat on an edge with a block in hand but looking at the horizon (e.g. about to jump to another
    // island) forcing sneak just slows you down and walks you off into the void.
    private final NumberValue lookDown = new NumberValue("Look Down", this, 60.0, 0.0, 90.0, 5.0)
            .describe("Only sneak while looking down at least this far (degrees)");

    // once engaged, keep sneaking until the look drops a full deadband below the threshold -- aim
    // jitter around the boundary would otherwise flap sneak on/off every tick (unshifted ticks at
    // the edge = slide-off risk, and the C0B flapping looks broken)
    private static final double LOOK_HYSTERESIS = 10.0;
    private boolean sneaking;

    public BridgeAssist() {
        super("BridgeAssist", "Assists with safer bridge placement.", Keyboard.KEY_NONE, Category.MOVEMENT);
    }

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        // only assist while actually bridging (block in hand)
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.onGround || !InventoryUtil.isHoldingBlock()) {
            sneaking = false;
            return;
        }

        // never sneak while the jump key is down -- jumping at an edge with sneak forced is exactly
        // how you get yanked off the block (LoseBypass gates on jump too)
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            sneaking = false;
            return;
        }

        // not looking down at the block you're placing on -> not bridging, leave the sneak alone.
        // hysteresis: once engaged we stay engaged until the pitch falls a deadband below the
        // threshold, so aim jitter can't shift/unshift us every tick.
        final double threshold = sneaking ? lookDown.doubleValue() - LOOK_HYSTERESIS : lookDown.doubleValue();
        if (mc.thePlayer.rotationPitch < threshold) {
            sneaking = false;
            return;
        }

        // keep sneaking the whole time we're sat on an edge, moving or standing still,
        // otherwise we slide off the moment a movement key is released
        if (!EdgeUtil.isAtAnyEdge(mc.thePlayer, mc.theWorld)) {
            sneaking = false;
            return;
        }

        sneaking = true;
        event.applyForcedSneakSlowdown();
    }
}
