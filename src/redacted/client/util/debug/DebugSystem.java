package redacted.client.util.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import redacted.client.Client;

/**
 * Dev-only, categorized debug logging, disabled by default.
 *
 * <p>Two gates, both required:
 * <ol>
 *   <li>Per-category compile-time booleans below - all {@code false} by
 *       default, so even a dev build logs nothing until a developer flips
 *       the relevant flag and rebuilds.</li>
 *   <li>{@link Client#DEV_BUILD} - only developer builds log anything.</li>
 * </ol>
 *
 * <p>Call sites name the category that owns them ({@link #GUI_INPUT},
 * {@link #REPLAY}, ...) so a whole class of diagnostics can be enabled as
 * one unit.
 *
 * <p><b>Output routing:</b> when a category is enabled it can write to a
 * dedicated file under {@code logs/} (one file per category) and optionally
 * echo to the console. The per-category file is the recommended sink for
 * high-volume or noisy tracing so it never spams the normal game log / F3;
 * echo-to-console defaults to off for exactly that reason.
 */
public final class DebugSystem {
    /** GUI button / mouse / keyboard / menu input tracing. */
    public static final String GUI_INPUT = "guiinput";
    /** Replay loading / playback internals. */
    public static final String REPLAY = "replay";
    /** Baritone pathing internals (frontier dumps on failed searches, etc.). */
    public static final String BARITONE = "baritone";
    /** Anticheat flag / setback / rubberband detection tracing. */
    public static final String FLAG = "flag";
    /** Anticheat identification (transaction UID fingerprinting, polling-rate heuristics). */
    public static final String ANTICHEAT = "anticheat";
    /** Celeritas terrain renderer bridge internals. */
    public static final String CELERITAS = "celeritas";
    /** Human scaffold sessions: yaw/pitch/click + C03/C08 + sneaking + ping per tick, for ScaffoldEngine training. */
    public static final String SCAFFOLD_HUMAN = "scaffold-human";
    /** Movement simulation: per-tick pos/motion/onGround + yaw/pitch + ping, for LagPicker/RotationPacer training. */
    public static final String MOVEMENT = "movement";

    /** Enable GUI input tracing (dev builds only). */
    public static final boolean GUI_INPUT_ENABLED = false;
    /** Enable replay internals tracing (dev builds only). */
    public static final boolean REPLAY_ENABLED = false;
    /**
     * Enable baritone pathing tracing (dev builds only). Flip to true while
     * tuning the humanized rotation/aim system; {@link #setBaritoneEnabled(boolean)}
     * can also silence it at runtime without a rebuild.
     */
    public static final boolean BARITONE_ENABLED = false;
    /** Enable anticheat flag detection tracing (dev builds only). */
    public static final boolean FLAG_ENABLED = false;
    /** Enable anticheat identification tracing (dev builds only). */
    public static final boolean ANTICHEAT_ENABLED = false;
    /** Enable Celeritas terrain renderer tracing (dev builds only). */
    public static final boolean CELERITAS_ENABLED = true;
    /** Enable human scaffold session tracing (dev builds only). Writes logs/scaffold-human/<mode>-<ts>.csv */
    public static final boolean SCAFFOLD_HUMAN_ENABLED = false;
    /** Enable movement simulation tracing (dev builds only). Writes logs/movement/*.csv */
    public static final boolean MOVEMENT_ENABLED = false;

    /**
     * Auto-start the MCP bridge on client launch (dev builds only, gated by dev mode).
     * Off by default so a normal session never opens the port; flip to {@code true}
     * while testing MCP / debug integration and it starts on the default port during
     * {@link redacted.client.Client#start()}.
     */
    public static final boolean AUTO_START_MCP = true;

    /**
     * Auto-join a singleplayer world on launch (dev builds only). Name is the world save folder /
     * level name, e.g. {@code "New World"}. Empty/null disables auto-join. Used by the self-testing
     * loop so an AI can drop straight into a world over MCP without a human clicking the menu.
     */
    public static final String AUTO_JOIN_WORLD = "";

    /**
     * Per-category "also echo to console" switches. These default to {@code false}
     * so a noisy category logs to its own file without cluttering the console or
     * the main game log. Set a specific category to {@code true} if you want it on
     * screen/console as well.
     */
    private static final Map<String, Boolean> ECHO_TO_CONSOLE = new ConcurrentHashMap<String, Boolean>() {{
        put(CELERITAS, Boolean.FALSE);
        put(BARITONE, Boolean.FALSE);
        put(SCAFFOLD_HUMAN, Boolean.FALSE);
        put(MOVEMENT, Boolean.FALSE);
    }};

    /** Runtime switch so baritone tracing alone can be silenced without a rebuild. */
    private static volatile boolean baritoneRuntimeEnabled = true;

    /** Runtime overrides for category enabled-state, set live over MCP. Absent = use compile-time flag. */
    private static final Map<String, Boolean> RUNTIME_ENABLED = new ConcurrentHashMap<String, Boolean>();

    /** Lazy per-category log writers, keyed by category. */
    private static final Map<String, BufferedWriter> WRITERS = new ConcurrentHashMap<String, BufferedWriter>();
    private static volatile File logDir;

    private DebugSystem() {
    }

    /** Toggle baritone tracing at runtime (dev builds only, compiles on with {@link #BARITONE_ENABLED}). */
    public static void setBaritoneEnabled(final boolean enabled) {
        baritoneRuntimeEnabled = enabled;
    }

    public static boolean isBaritoneEnabled() {
        return BARITONE_ENABLED && baritoneRuntimeEnabled;
    }

    /** Enables or disables console echo for a category at runtime (file logging still happens). */
    public static void setEchoToConsole(final String category, final boolean enabled) {
        ECHO_TO_CONSOLE.put(category, Boolean.valueOf(enabled));
    }

    public static boolean isEnabled(final String category) {
        if (!Client.DEV_BUILD) return false;
        // Live runtime override (set over MCP) takes priority over the compile-time flag.
        final Boolean runtime = RUNTIME_ENABLED.get(category);
        if (runtime != null) return runtime.booleanValue();
        if (GUI_INPUT.equals(category)) return GUI_INPUT_ENABLED;
        if (REPLAY.equals(category)) return REPLAY_ENABLED;
        if (BARITONE.equals(category)) return isBaritoneEnabled();
        if (FLAG.equals(category)) return FLAG_ENABLED;
        if (ANTICHEAT.equals(category)) return ANTICHEAT_ENABLED;
        if (CELERITAS.equals(category)) return CELERITAS_ENABLED;
        if (SCAFFOLD_HUMAN.equals(category)) return SCAFFOLD_HUMAN_ENABLED;
        if (MOVEMENT.equals(category)) return MOVEMENT_ENABLED;
        return false;
    }

    /** Sets a live enabled/disabled override for a category (used by the MCP debug tools). */
    public static void setRuntimeEnabled(final String category, final boolean enabled) {
        RUNTIME_ENABLED.put(category, Boolean.valueOf(enabled));
    }

    /** Whether the MCP bridge should auto-start this session (dev builds only). */
    public static boolean shouldAutoStartMcp() {
        return AUTO_START_MCP && Client.DEV_BUILD;
    }

    /** The singleplayer world to auto-join on launch, or null if disabled. */
    public static String autoJoinWorld() {
        if (!Client.DEV_BUILD) return null;
        if (AUTO_JOIN_WORLD == null || AUTO_JOIN_WORLD.isEmpty()) return null;
        return AUTO_JOIN_WORLD;
    }

    /** Logs to the category's dedicated file ({@code logs/debug-<category>.log}), and to the console only if the category's echo switch is on. */
    public static void log(final String category, final String message) {
        if (!isEnabled(category)) return;

        final String line = "[" + category + "] " + message;
        writeToFile(category, line);

        final Boolean echo = ECHO_TO_CONSOLE.get(category);
        if (echo != null && echo.booleanValue()) {
            System.out.println("[DBG:" + category + "] " + message);
        }
    }

    /** Ensures the log directory is known (called once from client init once mcDataDir is available). */
    public static synchronized void setLogDir(final File dir) {
        logDir = dir;
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    private static File ensureLogDir() {
        if (logDir == null) {
            File dir = null;
            try {
                if (Minecraft.getMinecraft() != null && Minecraft.getMinecraft().mcDataDir != null) {
                    dir = new File(Minecraft.getMinecraft().mcDataDir, "logs");
                }
            } catch (final Throwable ignored) {
            }
            if (dir == null) {
                dir = new File("logs");
            }
            logDir = dir;
        }
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        return logDir;
    }

    private static void writeToFile(final String category, final String line) {
        BufferedWriter writer = WRITERS.get(category);
        if (writer == null) {
            writer = openWriter(category);
            if (writer == null) return;
            WRITERS.put(category, writer);
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (final IOException ignored) {
            // Best-effort diagnostics; never let debug logging break the game.
        }
    }

    private static BufferedWriter openWriter(final String category) {
        try {
            final File file = new File(ensureLogDir(), "debug-" + category + ".log");
            return new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, true));
        } catch (final IOException ignored) {
            return null;
        }
    }

    /** Closes all open writers. Call on client shutdown so log files are flushed cleanly. */
    public static synchronized void closeAll() {
        for (final BufferedWriter writer : WRITERS.values()) {
            try {
                writer.close();
            } catch (final IOException ignored) {
            }
        }
        WRITERS.clear();
    }
}
