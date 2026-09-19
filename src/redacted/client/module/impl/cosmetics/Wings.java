package redacted.client.module.impl.cosmetics;

import org.lwjglx.input.Keyboard;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.ModeValue;

public class Wings extends Module {
    private static Wings INSTANCE;

    private final ModeValue mode = new ModeValue("Mode", this, "EDragon", "EDragon").describe("Which wings model to render");

    public Wings() {
        super("Wings", "Render client-side wings on yourself", Keyboard.KEY_NONE, Category.COSMETICS);
        INSTANCE = this;
    }

    // LayerWings reads this on the local player only; null = no wings
    public static String getActiveWings() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return null;
        return INSTANCE.mode.getValue();
    }
}
