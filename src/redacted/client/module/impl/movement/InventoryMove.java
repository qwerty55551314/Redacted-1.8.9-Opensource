package redacted.client.module.impl.movement;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;

// Walk while a GUI is open. Vanilla's KeyBinding.unPressAllKeys() (fired when a screen opens) zeroes
// the movement keys; we re-press them from the real key state every tick, after that runs, so
// MovementInput still picks them up. Chat is skipped so WASD types into the box normally. Sprint is
// re-pressed too, but EntityPlayerSP won't sprint with a screen open, so it can't pair with an
// inventory click into a Grim MultiActionsC flag.
public class InventoryMove extends Module {

    private final KeyBinding[] moveKeys = {
            mc.gameSettings.keyBindForward,
            mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindLeft,
            mc.gameSettings.keyBindRight,
            mc.gameSettings.keyBindJump,
            mc.gameSettings.keyBindSprint,
    };

    public InventoryMove() {
        super("Inventory Move", "Move while GUI screens are open", Keyboard.KEY_NONE, Category.MOVEMENT);
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre() || mc.thePlayer == null) return;
        if (mc.currentScreen == null || mc.currentScreen instanceof GuiChat) return;

        for (KeyBinding key : moveKeys) {
            KeyBinding.setKeyBindState(key.getKeyCode(), GameSettings.isKeyDown(key));
        }
    }

    @Override
    public void onDisable() {
        // only force the keys down-state while a screen is up (where they belong unpressed, like vanilla
        // after unPressAllKeys). otherwise hand each one back to its real physical state -- blindly forcing
        // false here would freeze a genuinely-held W and stomp Sprint/Speed/WTap if they're mid-hold.
        final boolean inScreen = mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat);
        for (KeyBinding key : moveKeys) {
            KeyBinding.setKeyBindState(key.getKeyCode(), !inScreen && GameSettings.isKeyDown(key));
        }
    }
}
