package redacted.client.module.impl.movement;

import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.NumberValue;

public class NoFall extends Module {

    private final NumberValue threshold = new NumberValue("Threshold", this, 3.0D, 0.5D, 10.0D, 0.5D).describe("Fall distance before spoofing onGround");

    public NoFall() {
        super("NoFall", "Prevents fall damage", Keyboard.KEY_NONE, Category.MOVEMENT);
    }

    @EventTarget
    public void onMotion(final MotionEvent event) {
        if (!event.isPre() || mc.thePlayer == null) return;
        if (mc.thePlayer.fallDistance > threshold.doubleValue()) event.setOnGround(true);
    }
}
