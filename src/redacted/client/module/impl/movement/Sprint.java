package redacted.client.module.impl.movement;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.combat.WTap;

public class Sprint extends Module {
    private static final float SPRINT_FORWARD_THRESHOLD = 0.8F;

    private WTap wTap;

    public Sprint() {
        super("Sprint", "Auto sprint", Keyboard.KEY_V, Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        wTap = Client.getInstance().getModuleManager().getModule(WTap.class);
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre() || mc.thePlayer == null || mc.theWorld == null) return;

        // WTap owns the sprint key while it's holding it released for a knockback reset -- don't fight it
        if (wTap != null && wTap.isSuppressing()) return;

        final int code = mc.gameSettings.keyBindSprint.getKeyCode();

        // GUI up or window unfocused -> don't drive sprint; hand it back to physical and let
        // InventoryMove own the movement keys in screens. stops us re-pressing sprint off the
        // forward key InventoryMove feeds us while a screen is open.
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            KeyBinding.setKeyBindState(code, GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
            return;
        }

        // NoSlow bypass active -> we're not slowed server-side, so keep driving sprint while using
        // an item (otherwise every bite drops you out of sprint and feels like a partial slow).
        final boolean usingStripped = mc.thePlayer.isUsingItem()
                && redacted.client.module.impl.movement.NoSlow.isStrippingSlow();

        if ((!mc.thePlayer.isUsingItem() || usingStripped) && getEffectiveForwardInput() >= SPRINT_FORWARD_THRESHOLD) {
            KeyBinding.setKeyBindState(code, true);
        } else {
            KeyBinding.setKeyBindState(code, GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
        }
    }

    @Override
    public void onDisable() {
        // just hand the key back to hardware state -- vanilla re-derives sprint and emits any C0B itself
        if (mc.thePlayer != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(),
                    GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
        }
    }

    private float getEffectiveForwardInput() {
        float forward = 0.0F;

        if (mc.gameSettings.keyBindForward.isKeyDown()) ++forward;
        if (mc.gameSettings.keyBindBack.isKeyDown()) --forward;

        if (mc.gameSettings.keyBindSneak.isKeyDown()) {
            forward *= 0.3F;
        }

        return forward;
    }
}
