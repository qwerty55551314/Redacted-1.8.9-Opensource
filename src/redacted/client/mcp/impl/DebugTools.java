package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.profiler.Profiler;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.util.debug.DebugSystem;

import java.util.List;

/**
 * Debug / performance introspection tools: expose the Minecraft profiler, the client's
 * real FPS (frame timer, not the F3-sampled debug string), and the {@link DebugSystem}
 * category switches so a developer can enable/disable tracing live over MCP.
 */
public final class DebugTools {

    private DebugTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new GetFps());
        server.registerTool(new GetProfiler());
        server.registerTool(new SetDebugCategory());
        server.registerTool(new GetCeleritasState());
        server.registerTool(new GetSectionState());
    }

    /** Inspects a single Celeritas render section's registration + build state. */
    private static final class GetSectionState implements McpTool {
        @Override
        public String name() {
            return "get_section_state";
        }

        @Override
        public String description() {
            return "Given a block position, report which Celeritas render section it maps to and that "
                    + "section's state (registered? built? visually empty?). Helps diagnose why a "
                    + "section containing an edited block may not rebuild (the block-break ghost bug).";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            props.add("x", ModuleTools.schemaProperty("integer", "Block X"));
            props.add("y", ModuleTools.schemaProperty("integer", "Block Y"));
            props.add("z", ModuleTools.schemaProperty("integer", "Block Z"));
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                final int x = args.get("x").getAsInt();
                final int y = args.get("y").getAsInt();
                final int z = args.get("z").getAsInt();
                final int sx = x >> 4, sy = y >> 4, sz = z >> 4;
                final JsonObject out = new JsonObject();
                out.addProperty("x", x);
                out.addProperty("y", y);
                out.addProperty("z", z);
                out.addProperty("sectionX", sx);
                out.addProperty("sectionY", sy);
                out.addProperty("sectionZ", sz);

                final celeritas.CeleritasWorldRenderer renderer =
                        celeritas.CeleritasWorldRenderer.getInstanceOrNull();
                if (renderer == null) {
                    out.addProperty("rendererConstructed", false);
                    return out;
                }
                out.addProperty("rendererConstructed", true);
                final org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager mgr =
                        renderer.getRenderSectionManager();
                if (mgr == null) {
                    out.addProperty("sectionManager", false);
                    return out;
                }
                out.addProperty("sectionManager", true);
                out.addProperty("isSectionReady", renderer.isSectionReady(sx, sy, sz));
                final net.minecraft.world.World world = Minecraft.getMinecraft().theWorld;
                if (world != null) {
                    out.addProperty("blockNow",
                            net.minecraft.block.Block.blockRegistry.getNameForObject(
                                    world.getBlockState(new net.minecraft.util.BlockPos(x, y, z)).getBlock()) + "");
                }
                return out;
            });
        }
    }

    /** Internal Celeritas renderer state for diagnosing rebuild/upload stalls. */
    private static final class GetCeleritasState implements McpTool {
        @Override
        public String name() {
            return "get_celeritas_state";
        }

        @Override
        public String description() {
            return "Internal Celeritas renderer state: active/constructed, visible chunks, chunk-builder "
                    + "queue depth (is build queue empty, scheduled job count, target queue size), and "
                    + "the current video toggle + shaders. Helps diagnose why rebuilt sections may not "
                    + "be applied.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                final Minecraft mc = Minecraft.getMinecraft();
                final JsonObject out = new JsonObject();

                final boolean supported = celeritas.CeleritasAvailability.isSupported();
                final boolean shadersOn = net.minecraft.src.Config.isShaders();
                final boolean toggleOn = mc.gameSettings.ofCeleritasRenderer;
                final celeritas.CeleritasWorldRenderer renderer = celeritas.CeleritasWorldRenderer.getInstanceOrNull();
                final boolean active = renderer != null && supported && !shadersOn && toggleOn;
                out.addProperty("active", active);
                out.addProperty("constructed", renderer != null);
                out.addProperty("gpuSupported", supported);
                out.addProperty("shadersOn", shadersOn);
                out.addProperty("toggleEnabled", toggleOn);

                if (renderer != null) {
                    out.addProperty("visibleChunks", renderer.getVisibleChunkCount());
                    final org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager mgr =
                            renderer.getRenderSectionManager();
                    if (mgr != null) {
                        out.addProperty("needsUpdate", mgr.needsUpdate());
                        out.addProperty("visibleChunksManager", mgr.getVisibleChunkCount());
                        try {
                            final org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder builder =
                                    mgr.getBuilder();
                            out.addProperty("buildQueueEmpty", builder.isBuildQueueEmpty());
                            out.addProperty("scheduledJobCount", builder.getScheduledJobCount());
                            out.addProperty("targetQueueSize", builder.getTargetQueueSize());
                        } catch (final Throwable t) {
                            out.addProperty("builderError", t.getClass().getSimpleName());
                        }
                    }
                }
                return out;
            });
        }
    }

    private static final class GetFps implements McpTool {
        @Override
        public String name() {
            return "get_fps";
        }

        @Override
        public String description() {
            return "Current FPS plus frame-time stats, from the client's frame timer rather than the F3 debug string.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                final Minecraft mc = Minecraft.getMinecraft();
                final JsonObject out = new JsonObject();

                // debugFPS is the F3-sampled value (updated once per second).
                out.addProperty("debugFps", Minecraft.getDebugFPS());
                // getFrameTimer gives real per-frame intervals; compute an instantaneous FPS.
                out.addProperty("instantFps", frameTimerFps(mc));
                return out;
            });
        }

        /** Sums recent frame-timer entries into an instantaneous FPS. */
        private double frameTimerFps(final Minecraft mc) {
            try {
                final long[] frames = mc.frameTimer.getFrames();
                final int index = mc.frameTimer.getIndex();
                if (index <= 0) return 0.0D;
                long total = 0L;
                for (int i = 0; i < index; i++) {
                    total += frames[i];
                }
                final double avgNanos = total / (double) index;
                return avgNanos > 0.0D ? 1_000_000_000.0D / avgNanos : 0.0D;
            } catch (final Throwable t) {
                return 0.0D;
            }
        }
    }

    private static final class GetProfiler implements McpTool {
        @Override
        public String name() {
            return "get_profiler";
        }

        @Override
        public String description() {
            return "Return the Minecraft profiler's per-section timing breakdown for a given section (default 'root'), "
                    + "or its enabled state. Same data the F3 pie chart shows.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            final JsonObject section = new JsonObject();
            section.addProperty("type", "string");
            section.addProperty("default", "root");
            props.add("section", section);
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                final Minecraft mc = Minecraft.getMinecraft();
                final Profiler profiler = mc.mcProfiler;
                final JsonObject out = new JsonObject();
                out.addProperty("enabled", profiler.profilingEnabled);

                final String section = args.has("section") ? args.get("section").getAsString() : "root";
                final List<Profiler.Result> results = profiler.getProfilingData(section);
                final JsonArray items = new JsonArray();
                if (results != null) {
                    for (final Profiler.Result result : results) {
                        final JsonObject item = new JsonObject();
                        item.addProperty("name", result.field_76331_c);
                        item.addProperty("usePercent", result.field_76332_a);
                        item.addProperty("totalPercent", result.field_76330_b);
                        items.add(item);
                    }
                }
                out.add("results", items);
                return out;
            });
        }
    }

    private static final class SetDebugCategory implements McpTool {
        @Override
        public String name() {
            return "set_debug_category";
        }

        @Override
        public String description() {
            return "Enable/disable a DebugSystem category, and optionally its console echo. Categories: "
                    + "guiinput, replay, baritone, flag, anticheat, celeritas.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            final JsonObject category = new JsonObject();
            category.addProperty("type", "string");
            category.addProperty("description", "DebugSystem category name");
            props.add("category", category);
            final JsonObject enabled = new JsonObject();
            enabled.addProperty("type", "boolean");
            props.add("enabled", enabled);
            final JsonObject echo = new JsonObject();
            echo.addProperty("type", "boolean");
            echo.addProperty("description", "optional: also echo to console (default keeps current)");
            props.add("echo", echo);
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                final String category = args.has("category") ? args.get("category").getAsString() : null;
                if (category == null) {
                    return status();
                }
                if (args.has("enabled")) {
                    DebugSystem.setRuntimeEnabled(category, args.get("enabled").getAsBoolean());
                }
                if (args.has("echo")) {
                    DebugSystem.setEchoToConsole(category, args.get("echo").getAsBoolean());
                }
                return status();
            });
        }

        private JsonObject status() {
            final JsonObject out = new JsonObject();
            out.addProperty("guiinput", DebugSystem.isEnabled(DebugSystem.GUI_INPUT));
            out.addProperty("replay", DebugSystem.isEnabled(DebugSystem.REPLAY));
            out.addProperty("baritone", DebugSystem.isEnabled(DebugSystem.BARITONE));
            out.addProperty("flag", DebugSystem.isEnabled(DebugSystem.FLAG));
            out.addProperty("anticheat", DebugSystem.isEnabled(DebugSystem.ANTICHEAT));
            out.addProperty("celeritas", DebugSystem.isEnabled(DebugSystem.CELERITAS));
            return out;
        }
    }
}
