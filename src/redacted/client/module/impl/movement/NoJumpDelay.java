package redacted.client.module.impl.movement;

import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;

public class NoJumpDelay extends Module {

    public NoJumpDelay() {
        super("No Jump Delay", "Removes jump delay", Keyboard.KEY_NONE, Category.MOVEMENT);
    }

    @EventTarget
    public void onMotion(final MotionEvent event) {
        if (!event.isPre()) return;
        mc.thePlayer.jumpTicks = 0;
    }
}
