package redacted.client.module.impl.cosmetics;

import org.lwjglx.input.Keyboard;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.ModeValue;

public class Hat extends Module {
    private static Hat INSTANCE;

    private final ModeValue mode = new ModeValue("Mode", this, "Samurai", "Samurai", "Sensei").describe("Which kasa hat style to wear");

    public Hat() {
        super("Hat", "Render a client-side kasa hat on yourself", Keyboard.KEY_NONE, Category.COSMETICS);
        INSTANCE = this;
    }

    // LayerHat reads this on the local player only; null = no hat
    public static String getActiveHat() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return null;
        return INSTANCE.mode.getValue();
    }
}
