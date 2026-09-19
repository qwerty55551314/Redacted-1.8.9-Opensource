package redacted.client.baritone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.world.WorldSettings;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.util.debug.DebugSystem;

/**
 * Dev-only auto-joiner: once the main menu is up, launches the singleplayer world named by
 * {@link DebugSystem#AUTO_JOIN_WORLD}. Only active when the debug system says to (dev builds),
 * so a normal session is never affected.
 */
public final class AutoJoinBridge {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean joined;
    private boolean requested;

    public AutoJoinBridge() {
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) {
            return;
        }
        if (this.joined) {
            return;
        }

        final String worldName = DebugSystem.autoJoinWorld();
        if (worldName == null) {
            this.joined = true;
            return;
        }

        // Wait until we're on the main menu (vanilla or this client's custom one) with no world loaded.
        final GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return;
        }
        final boolean mainMenu = screen instanceof GuiMainMenu
                || screen instanceof redacted.client.gui.menu.GuiCustomMainMenu
                || screen.getClass().getName().endsWith("GuiCustomMainMenu");
        if (!mainMenu) {
            return;
        }
        if (mc.theWorld != null) {
            this.joined = true;
            return;
        }

        if (this.requested) {
            return;
        }
        this.requested = true;

        if (DebugSystem.isEnabled(DebugSystem.CELERITAS)) {
            DebugSystem.log(DebugSystem.CELERITAS, "Auto-joining singleplayer world \"" + worldName + "\"");
        }

        mc.launchIntegratedServer(worldName, worldName, null);
    }
}
