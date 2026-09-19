package redacted.client;

import redacted.client.command.CommandManager;
import redacted.client.event.EventManager;
import redacted.client.config.ClientConfigManager;
import redacted.client.config.ThemeManager;
import redacted.client.module.ModuleManager;
import redacted.client.module.impl.render.EntityESP;
import redacted.client.module.impl.render.FullBright;
import redacted.client.module.impl.render.HUD;
import redacted.client.module.impl.render.Mirror;
import redacted.client.module.impl.render.Notifications;
import redacted.client.util.gui.DragManager;
import redacted.client.util.notification.NotificationManager;
import redacted.util.CombatManager;
import redacted.util.GameStateTracker;
import redacted.util.RotationUtil;
import net.minecraft.client.Minecraft;

public final class Client {
    public static final String NAME = "Redacted";
    public static final String VERSION = "b3.3";
    /* build type shown in the window title */
    public static final String BUILD_TYPE = "developer";

    /** Developer build flag: developer/diagnostic modules and commands are always available. */
    public static final boolean DEV_BUILD = true;

    private static final Client INSTANCE = new Client();

    private final CommandManager commandManager = new CommandManager();
    private final EventManager eventManager = new EventManager();
    private final ModuleManager moduleManager = new ModuleManager();
    private final ClientConfigManager configManager = new ClientConfigManager();
    private final ThemeManager themeManager = new ThemeManager();
    private final NotificationManager notificationManager = new NotificationManager();
    private final DragManager dragManager = new DragManager();
    private boolean started;

    private Client() {
    }

    public static Client getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (this.started) return;
        this.started = true;
        this.commandManager.init();
        this.moduleManager.init();
        this.eventManager.register(CombatManager.getInstance());
        this.eventManager.register(GameStateTracker.getInstance());
        this.eventManager.register(RotationUtil.getInstance());
        // Baritone: always-on event bridge (pathing is inert until a goal/process is set)
        this.eventManager.register(new redacted.client.baritone.BaritoneBridge());
        this.configManager.load(Minecraft.getMinecraft().mcDataDir, this.moduleManager);
        this.themeManager.load(Minecraft.getMinecraft().mcDataDir);
        this.dragManager.loadFromTheme();
        redacted.client.util.debug.DebugSystem.setLogDir(new java.io.File(Minecraft.getMinecraft().mcDataDir, "logs"));
        // Dev-only: auto-start the MCP bridge when the debug system asks for it (flip AUTO_START_MCP).
        if (redacted.client.util.debug.DebugSystem.shouldAutoStartMcp()) {
            redacted.client.mcp.McpManager.getInstance().start(redacted.client.mcp.McpManager.DEFAULT_PORT);
        }
        // Dev-only: auto-join a singleplayer world on launch so the self-testing loop can drop
        // straight into a world over MCP without a human clicking the menu.
        final String autoWorld = redacted.client.util.debug.DebugSystem.autoJoinWorld();
        if (autoWorld != null) {
            this.eventManager.register(new redacted.client.baritone.AutoJoinBridge());
        }

        // register every HUD draggable up front so the edit-HUD dummy containers always exist
        // the lazy getters only fire when their module first renders, which is why the Notifications
        // dummy used to be missing until a popup actually appeared. registration also loads the
        // saved position at startup (ratio-based, so it survives window resizes).
        final Notifications notif = this.moduleManager.getModule(Notifications.class);
        if (notif != null) notif.getDraggable();
        final HUD hud = this.moduleManager.getModule(HUD.class);
        if (hud != null) hud.registerAllDraggables();
        final EntityESP esp = this.moduleManager.getModule(EntityESP.class);
        if (esp != null) esp.registerAllDraggables();
        final Mirror mirror = this.moduleManager.getModule(Mirror.class);
        if (mirror != null) mirror.getDraggable();

        final FullBright fullBright = this.moduleManager.getModule(FullBright.class);
        if (fullBright != null) {
            fullBright.syncGammaState();
        }
    }

    public void shutdown() {
        if (!this.started) return;
        redacted.client.mcp.McpManager.getInstance().shutdownQuietly();
        this.dragManager.saveToConfig();
        this.themeManager.save();
        this.configManager.saveNow();
        this.configManager.disableSaves();
        this.moduleManager.shutdown();
        this.eventManager.clear();
        redacted.client.util.debug.DebugSystem.closeAll();
        this.started = false;
    }

    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    public EventManager getEventManager() {
        return this.eventManager;
    }

    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public ClientConfigManager getConfigManager() {
        return this.configManager;
    }

    public ThemeManager getThemeManager() {
        return this.themeManager;
    }

    public NotificationManager getNotificationManager() {
        return this.notificationManager;
    }

    public DragManager getDragManager() {
        return this.dragManager;
    }
}
