package redacted.client.module.impl.world;

import baritone.api.BaritoneAPI;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.BooleanValue;

/**
 * Baritone integration toggle. The engine itself runs whenever the client runs
 * (BaritoneBridge is registered at client start), so this module only controls
 * the path/goal rendering and provides a single "stop everything" switch.
 */
public class BaritoneModule extends Module {

    private final BooleanValue renderPath = new BooleanValue("Render Path", this, true);
    private final BooleanValue renderGoal = new BooleanValue("Render Goal", this, true);
    private final BooleanValue renderSelectionBoxes = new BooleanValue("Render Selection Boxes", this, true);
    private final BooleanValue allowChatControl = new BooleanValue("Chat Control", this, true);

    public BaritoneModule() {
        super("Baritone", "Pathfinding bot (ported from Baritone 1.13.2)", 0, Category.WORLD);
    }

    @Override
    public void onEnable() {
        BaritoneAPI.getProvider();
        syncRenderSettings();
    }

    @Override
    public void onDisable() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
    }

    @redacted.client.event.EventTarget
    public void onTick(final redacted.client.event.impl.EventTick event) {
        if (event.isPre()) {
            syncRenderSettings();
        }
    }

    private void syncRenderSettings() {
        BaritoneAPI.getSettings().renderPath.value = this.renderPath.getValue();
        BaritoneAPI.getSettings().renderGoal.value = this.renderGoal.getValue();
        BaritoneAPI.getSettings().renderSelectionBoxes.value = this.renderSelectionBoxes.getValue();
        BaritoneAPI.getSettings().prefixControl.value = this.allowChatControl.getValue();
    }
}
