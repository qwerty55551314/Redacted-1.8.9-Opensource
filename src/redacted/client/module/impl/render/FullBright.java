package redacted.client.module.impl.render;

import org.lwjglx.input.Keyboard;
import redacted.client.module.Category;
import redacted.client.module.Module;
public class FullBright extends Module {
    private float preFullBrightValue;

    public FullBright() {
        super("FullBright", "Light everywhere :)", Keyboard.KEY_B, Category.RENDER);
    }

    @Override
    public void onEnable() {
        final float currentGamma = mc.gameSettings.gammaSetting;
        preFullBrightValue = currentGamma <= 1.0F ? currentGamma : 0.0F;
        mc.gameSettings.gammaSetting = 100.0F;
    }

    @Override
    public void onDisable() {
        mc.gameSettings.gammaSetting = preFullBrightValue;
        mc.gameSettings.saveOptions();
    }

    public void syncGammaState() {
        if (isEnabled()) {
            mc.gameSettings.gammaSetting = 100.0F;
            return;
        }

        if (mc.gameSettings.gammaSetting > 1.0F) {
            mc.gameSettings.gammaSetting = preFullBrightValue <= 1.0F ? preFullBrightValue : 0.0F;
            mc.gameSettings.saveOptions();
        }
    }
}
