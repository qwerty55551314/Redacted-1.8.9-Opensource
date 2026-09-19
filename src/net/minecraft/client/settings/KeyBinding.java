package net.minecraft.client.settings;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IntHashMap;

import java.util.List;
import java.util.Set;

public class KeyBinding implements Comparable<KeyBinding>, com.replaymod.core.mixin.KeyBindingAccessor {
    private static final List<KeyBinding> keybindArray = Lists.newArrayList();
    private static final IntHashMap<KeyBinding> hash = new IntHashMap<>();
    private static final Set<String> keybindSet = Sets.newHashSet();
    private final String keyDescription;
    private final int keyCodeDefault;
    private final String keyCategory;
    private int keyCode;

    /**
     * Is the key held down?
     */
    private boolean pressed;
    private int pressTime;

    @Override
    public int getPressTime() { return this.pressTime; }

    @Override
    public void setPressTime(int pressTime) { this.pressTime = pressTime; }

    public static void onTick(int keyCode) {
        if (com.replaymod.core.events.KeyEventCallback.EVENT.invoker().onKeyEvent(
                new com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput(
                    org.lwjglx.input.Keyboard.getEventKey() == 0
                        ? org.lwjglx.input.Keyboard.getEventCharacter() + 256
                        : org.lwjglx.input.Keyboard.getEventKey()),
                org.lwjglx.input.Keyboard.getEventKeyState() ? 1 : 0)) {
            return;
        }
        if (keyCode != 0) {
            KeyBinding keybinding = hash.lookup(keyCode);

            if (keybinding != null) {
                ++keybinding.pressTime;
            }
        }
        com.replaymod.core.events.KeyBindingEventCallback.EVENT.invoker().onKeybindingEvent();
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        if (keyCode != 0) {
            KeyBinding keybinding = hash.lookup(keyCode);

            if (keybinding != null) {
                keybinding.pressed = pressed;
            }
        }
    }

    public static void unPressAllKeys() {
        for (KeyBinding keybinding : keybindArray) {
            keybinding.unpressKey();
        }
    }

    private static final java.util.List<KeyBinding> replayModTemporarilyRemoved = new java.util.ArrayList<>();

    private static java.util.Collection<KeyBinding> replayModKeyBindings() {
        return keybindArray;
    }

    public static void resetKeyBindingArrayAndHash() {
        com.replaymod.core.ReplayMod replayMod = com.replaymod.core.ReplayMod.instance;
        if (replayMod != null) {
            java.util.Set<KeyBinding> onlyInReplay = replayMod.getKeyBindingRegistry().getOnlyInReplay();
            if (com.replaymod.replay.ReplayModReplay.instance.getReplayHandler() != null) {
                replayModKeyBindings().removeIf(keyBinding -> {
                    for (KeyBinding exclusiveBinding : onlyInReplay) {
                        if (keyBinding.getKeyCode() == exclusiveBinding.getKeyCode() && keyBinding != exclusiveBinding) {
                            replayModTemporarilyRemoved.add(keyBinding);
                            return true;
                        }
                    }
                    return false;
                });
            } else {
                replayModKeyBindings().removeIf(keyBinding -> {
                    if (onlyInReplay.contains(keyBinding)) {
                        replayModTemporarilyRemoved.add(keyBinding);
                        return true;
                    }
                    return false;
                });
            }
        }
        hash.clearMap();

        for (KeyBinding keybinding : keybindArray) {
            hash.addKey(keybinding.keyCode, keybinding);
        }
        for (KeyBinding keyBinding : replayModTemporarilyRemoved) {
            replayModKeyBindings().add(keyBinding);
        }
        replayModTemporarilyRemoved.clear();
    }

    public static Set<String> getKeybinds() {
        return keybindSet;
    }

    public KeyBinding(String description, int keyCode, String category) {
        this.keyDescription = description;
        this.keyCode = keyCode;
        this.keyCodeDefault = keyCode;
        this.keyCategory = category;
        keybindArray.add(this);
        hash.addKey(keyCode, this);
        keybindSet.add(category);
    }

    /**
     * Returns true if the key is pressed (used for continuous querying). Should be used in tickers.
     */
    public boolean isKeyDown() {
        return this.pressed;
    }

    public String getKeyCategory() {
        return this.keyCategory;
    }

    /**
     * Returns true on the initial key press. For continuous querying use {isKeyDown()}. Should be used in key
     * events.
     */
    public boolean isPressed() {
        if (this.pressTime == 0) {
            return false;
        } else {
            --this.pressTime;
            return true;
        }
    }

    private void unpressKey() {
        this.pressTime = 0;
        this.pressed = false;
    }

    public String getKeyDescription() {
        return this.keyDescription;
    }

    public int getKeyCodeDefault() {
        return this.keyCodeDefault;
    }

    public int getKeyCode() {
        return this.keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public int compareTo(KeyBinding p_compareTo_1_) {
        int i = I18n.format(this.keyCategory).compareTo(I18n.format(p_compareTo_1_.keyCategory));

        if (i == 0) {
            i = I18n.format(this.keyDescription).compareTo(I18n.format(p_compareTo_1_.keyDescription));
        }

        return i;
    }
}
