package redacted.client.module;

import net.minecraft.client.Minecraft;
import redacted.client.Client;
import redacted.client.module.impl.render.Notifications;
import redacted.client.util.gui.Draggable;
import redacted.client.util.notification.NotificationType;
import redacted.client.value.Value;
import redacted.client.value.impl.BooleanValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected static final Minecraft mc = Minecraft.getMinecraft();
    private final String name;
    private final String description;
    private final Category category;
    private final ModuleType type;
    private int key;
    private boolean enabled;
    private final List<Value<?>> values = new ArrayList<>();
    private final boolean notificationExempt;
    // boolean settings render as checkboxes in the Redacted clickgui instead of the pill toggle
    // (opt-in per module -- EntityESP's sub-features use checkboxes, everything else keeps the pill)
    private boolean checkboxBooleans;

    protected Module(String name, String description, int key, Category category) {
        this(name, description, key, category, ModuleType.NORMAL, false);
    }

    protected Module(String name, String description, int key, Category category, boolean notificationExempt) {
        this(name, description, key, category, ModuleType.NORMAL, notificationExempt);
    }

    protected Module(String name, String description, int key, Category category, ModuleType type) {
        this(name, description, key, category, type, false);
    }

    protected Module(String name, String description, int key, Category category, ModuleType type, boolean notificationExempt) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.category = category;
        this.type = type == null ? ModuleType.NORMAL : type;
        this.notificationExempt = notificationExempt;
    }

    public final void setCheckboxBooleans(boolean checkboxBooleans) {
        this.checkboxBooleans = checkboxBooleans;
    }

    public final boolean usesCheckboxes() {
        return this.checkboxBooleans;
    }


    public void toggle() { setEnabled(!this.enabled); }
    public void disable() { setEnabled(false); }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            Client.getInstance().getEventManager().register(this);
            this.onEnable();
        } else {
            Client.getInstance().getEventManager().unregister(this);
            this.onDisable();
        }
        Client.getInstance().getConfigManager().save();

        if (!(this instanceof Notifications)) {
            Notifications notifModule = Client.getInstance().getModuleManager().getModule(Notifications.class);
            if (notifModule != null && notifModule.isToggleNotificationEnabled() && !this.notificationExempt) {
                Client.getInstance().getNotificationManager().showNotification(NotificationType.INFO, getName() + (enabled ? " enabled" : " disabled"), 2);
            }
        }
    }

    public void onEnable() {}
    public void onDisable() {}

    // false = never auto-enable from saved config on launch (e.g. Silent Aura). ClickGUI stays special-cased in ClientConfigManager.
    public boolean persistEnabled() { return true; }

    /**
     * Whether this is a developer-only module. {@code true} when its
     * {@link ModuleType} is {@link ModuleType#DEVELOPER}. When
     * {@code Client.DEV_BUILD} is {@code false} the managers skip registering
     * these, skip saving/loading them, and (for release jars) their .class
     * files are stripped from the JAR.
     */
    public boolean isDeveloperOnly() {
        return this.type == ModuleType.DEVELOPER;
    }

    /** The module's {@link ModuleType} (see {@link ModuleType#DEVELOPER}). */
    public ModuleType getType() {
        return this.type;
    }

    public String getName() { return this.name; }
    public String getDescription() { return this.description; }
    // extra text the HUD arraylist tacks onto the name (AutoClicker shows its cps band)
    public String getArraylistSuffix() { return ""; }
    public Category getCategory() { return this.category; }
    public int getKey() { return this.key; }
    public void setKey(int key) {
        if (this.key == key) return;
        this.key = key;
        Client.getInstance().getConfigManager().save();
    }
    public boolean isEnabled() { return this.enabled; }

    public List<Value<?>> getSettings() { return Collections.unmodifiableList(this.values); }
    public void addValue(Value<?> value) { if (!this.values.contains(value)) this.values.add(value); }

    /**
     * The boolean sub-component toggle that gates a draggable element of this module
     * (e.g. HUD's "Coords Display" draggable is gated by its "Coords Display" setting).
     * Matched by name; override for draggables whose gating value has a different name
     * (HUD's ArrayList draggable is called "HUD"). Null = no per-component toggle, so
     * hiding falls back to disabling the whole module.
     */
    public BooleanValue getComponentToggle(final String draggableName) {
        if (draggableName == null) return null;
        for (final Value<?> value : this.values) {
            if (value instanceof BooleanValue && value.getName().equalsIgnoreCase(draggableName)) {
                return (BooleanValue) value;
            }
        }
        return null;
    }

    protected final Draggable registerDraggable(String name, float defaultX, float defaultY, float width, float height) {
        return Client.getInstance().getDragManager().register(this, name, defaultX, defaultY, width, height);
    }
}
