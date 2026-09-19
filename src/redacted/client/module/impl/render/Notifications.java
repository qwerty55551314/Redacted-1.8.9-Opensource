package redacted.client.module.impl.render;

import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.util.font.Font;
import redacted.client.util.gui.Draggable;
import redacted.client.util.notification.NotificationType;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

public class Notifications extends Module {
    private final BooleanValue info = new BooleanValue("Info", this, true).describe("Show info notifications");
    private final BooleanValue warning = new BooleanValue("Warning", this, true).describe("Show warning notifications");
    private final BooleanValue error = new BooleanValue("Error", this, true).describe("Show error notifications");
    private final BooleanValue toggled = new BooleanValue("Toggled", this, true).describe("Pop a notification when a module toggles");
    private final ModeValue fontMode = new ModeValue("Font", this, "SF-Pro-Rounded", "SF-Pro-Rounded", "Titan One", "Minecraft").describe("Notification font");
    // theme knobs: icons are white-base tinted grey to match the clickgui, alphas user-tunable
    private final NumberValue iconAlpha = new NumberValue("Icon Alpha", this, 230, 0, 255, 5).describe("Notification icon opacity");
    private final NumberValue cardAlpha = new NumberValue("Card Alpha", this, 110, 0, 255, 5).describe("Notification card background opacity");

    private Draggable draggable;

    public Notifications() {
        super("Notifications", "Displays notifications on screen", Keyboard.KEY_NONE, Category.RENDER);
    }

    public Draggable getDraggable() {
        if (draggable == null) {
            draggable = registerDraggable("Notifications", -1, -1, 220, 80);
        }
        return draggable;
    }

    public boolean isTypeEnabled(NotificationType type) {
        switch (type) {
            case INFO: return info.getValue();
            case WARNING: return warning.getValue();
            case ERROR: return error.getValue();
            default: return true;
        }
    }

    public boolean isToggleNotificationEnabled() {
        return toggled.getValue();
    }

    public int getIconAlpha() {
        return iconAlpha.intValue();
    }

    public int getCardAlpha() {
        return cardAlpha.intValue();
    }

    // scale rides the Draggable so notifications resize when dragged in the edit GUI (same trick as the ArrayList)
    public double getScale() {
        return getDraggable().getScale();
    }

    public Font getSelectedFont(int size, Weight weight) {
        return Fonts.getForMode(fontMode.getValue(), size, weight);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Client.getInstance().getNotificationManager().render(event.getScaledResolution());
    }
}
