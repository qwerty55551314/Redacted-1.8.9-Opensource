package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;

/**
 * Renderer + world-load introspection tools: which terrain renderer is active, renderer stats,
 * chunk-load state around the player, and arbitrary block lookups. Dev/QA oriented.
 */
public final class RenderTools {

    private RenderTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new GetRendererInfo());
        server.registerTool(new GetWorldChunks());
        server.registerTool(new GetBlockAt());
        server.registerTool(new SetBlock());
        server.registerTool(new Screenshot());
    }

    /** Saves a screenshot of the current framebuffer to disk and returns the path. */
    private static final class Screenshot implements McpTool {
        @Override
        public String name() {
            return "screenshot";
        }

        @Override
        public String description() {
            return "Save a screenshot of the current game framebuffer to the screenshots directory and "
                    + "return the absolute file path. Use to visually verify what is actually being rendered.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                final Minecraft mc = Minecraft.getMinecraft();
                final java.io.File shotDir = new java.io.File(mc.mcDataDir, "screenshots");
                if (!shotDir.exists()) shotDir.mkdirs();
                final String name = "mcp_" + System.currentTimeMillis() + ".png";
                final java.io.File target = new java.io.File(shotDir, name);
                try {
                    net.minecraft.util.ScreenShotHelper.saveScreenshot(target.getParentFile(), name,
                            mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                } catch (final Throwable t) {
                    final JsonObject err = new JsonObject();
                    err.addProperty("error", t.getClass().getSimpleName() + ": " + t.getMessage());
                    return err;
                }
                final JsonObject out = new JsonObject();
                out.addProperty("path", target.getAbsolutePath());
                out.addProperty("exists", target.exists());
                return out;
            });
        }
    }

    /** Set/break a block in the world directly (self-testing for the block-break ghost bug). */
    private static final class SetBlock implements McpTool {
        @Override
        public String name() {
            return "set_block";
        }

        @Override
        public String description() {
            return "Set or break a block at a coordinate (bypasses normal survival mining). Pass "
                    + "air=true to break, or block=\"stone\" to set. Triggers the same markBlockForUpdate "
                    + "path so you can verify the renderer rebuilds. Returns the resulting block id and "
                    + "renderer state.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            props.add("x", ModuleTools.schemaProperty("integer", "X"));
            props.add("y", ModuleTools.schemaProperty("integer", "Y"));
            props.add("z", ModuleTools.schemaProperty("integer", "Z"));
            props.add("air", ModuleTools.schemaProperty("boolean", "true to break (set to air)."));
            props.add("block", ModuleTools.schemaProperty("string", "block id to set (optional)."));
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                requireInWorld();
                final int x = args.get("x").getAsInt();
                final int y = args.get("y").getAsInt();
                final int z = args.get("z").getAsInt();
                final BlockPos pos = new BlockPos(x, y, z);
                final net.minecraft.world.World world = mc().theWorld;

                final String before = blockId(world.getBlockState(pos).getBlock());
                final boolean ok;
                if (args.has("air") && args.get("air").getAsBoolean()) {
                    ok = world.setBlockToAir(pos);
                } else if (args.has("block")) {
                    final Block block = Block.getBlockFromName(args.get("block").getAsString().replace("minecraft:", ""));
                    if (block == null) {
                        throw new redacted.client.mcp.McpToolException("Unknown block: " + args.get("block").getAsString());
                    }
                    ok = world.setBlockState(pos, block.getDefaultState());
                } else {
                    ok = world.setBlockToAir(pos);
                }

                final JsonObject out = new JsonObject();
                out.addProperty("set", ok);
                out.addProperty("x", x);
                out.addProperty("y", y);
                out.addProperty("z", z);
                out.addProperty("before", before);
                out.addProperty("now", blockId(world.getBlockState(pos).getBlock()));
                out.addProperty("celeritasActive", celeritas.CeleritasRenderHook.isActive());
                return out;
            });
        }

        private String blockId(final Block block) {
            final net.minecraft.util.ResourceLocation name = Block.blockRegistry.getNameForObject(block);
            return name == null ? "?" : name.toString().replace("minecraft:", "");
        }
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private static void requireInWorld() {
        if (mc() == null || mc().thePlayer == null || mc().theWorld == null) {
            throw new redacted.client.mcp.McpToolException("Not in a world.");
        }
    }

    /** Which terrain renderer is active + stats. */
    private static final class GetRendererInfo implements McpTool {
        @Override
        public String name() {
            return "get_renderer_info";
        }

        @Override
        public String description() {
            return "Which terrain renderer is active (celeritas vs vanilla/optifine) and why, plus "
                    + "renderer stats: visible chunks, total render chunks, and the Celeritas video toggle state.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                requireInWorld();
                final JsonObject out = new JsonObject();

                final boolean supported = celeritas.CeleritasAvailability.isSupported();
                final boolean shadersOn = net.minecraft.src.Config.isShaders();
                final boolean toggleOn = mc().gameSettings.ofCeleritasRenderer;
                final celeritas.CeleritasWorldRenderer renderer = celeritas.CeleritasWorldRenderer.getInstanceOrNull();
                final boolean active = renderer != null && supported && !shadersOn && toggleOn;

                out.addProperty("renderer", active ? "celeritas" : "vanilla");
                out.addProperty("celeritasActive", active);
                out.addProperty("gpuSupported", supported);
                out.addProperty("shadersOn", shadersOn);
                out.addProperty("toggleEnabled", toggleOn);
                out.addProperty("rendererConstructed", renderer != null);
                if (!supported) {
                    out.addProperty("unsupportedReason", celeritas.CeleritasAvailability.unsupportedReason());
                }

                final RenderGlobal rg = mc().renderGlobal;
                if (rg != null) {
                    out.addProperty("activeRenderers", rg.getCountActiveRenderers());
                    out.addProperty("totalRenderers", rg.getCountRenderers());
                }
                if (renderer != null) {
                    out.addProperty("visibleChunks", renderer.getVisibleChunkCount());
                    out.addProperty("renderDistanceChunks", mc().gameSettings.renderDistanceChunks);
                }
                return out;
            });
        }
    }

    /** Chunk-load state around the player. */
    private static final class GetWorldChunks implements McpTool {
        @Override
        public String name() {
            return "get_world_chunks";
        }

        @Override
        public String description() {
            return "Chunk load state around the player: how many chunks are loaded, the provider name, "
                    + "and per-chunk load status within a radius (in chunks, default 4).";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            props.add("radius", ModuleTools.schemaProperty("number",
                    "Scan radius in chunks (default 4, max 16)."));
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                requireInWorld();
                final WorldClient world = (WorldClient) mc().theWorld;
                final ChunkProviderClient provider = (ChunkProviderClient) world.getChunkProvider();

                int radius = 4;
                if (args.has("radius") && args.get("radius").isJsonPrimitive()) {
                    radius = (int) Math.max(1, Math.min(16, args.get("radius").getAsDouble()));
                }

                final int cx = mc().thePlayer.chunkCoordX;
                final int cz = mc().thePlayer.chunkCoordZ;

                final JsonObject out = new JsonObject();
                out.addProperty("loadedChunkCount", provider.getLoadedChunkCount());
                out.addProperty("providerName", world.getProviderName());
                out.addProperty("playerChunkX", cx);
                out.addProperty("playerChunkZ", cz);

                final JsonArray chunks = new JsonArray();
                for (int x = cx - radius; x <= cx + radius; x++) {
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        final JsonObject c = new JsonObject();
                        c.addProperty("x", x);
                        c.addProperty("z", z);
                        final boolean loaded = provider.chunkExists(x, z);
                        c.addProperty("loaded", loaded);
                        if (loaded) {
                            final Chunk chunk = provider.provideChunk(x, z);
                            c.addProperty("isEmpty", chunk.isEmpty());
                        }
                        chunks.add(c);
                    }
                }
                out.add("chunks", chunks);
                return out;
            });
        }
    }

    /** Look up the block at a coordinate (or around the player). */
    private static final class GetBlockAt implements McpTool {
        @Override
        public String name() {
            return "get_block_at";
        }

        @Override
        public String description() {
            return "Return the block id + metadata at a block position. Pass x/y/z explicitly, or "
                    + "relative offsets (dx/dy/dz) from the player's feet.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject props = new JsonObject();
            props.add("x", ModuleTools.schemaProperty("integer", "Absolute X, or relative dx if no absolute coords."));
            props.add("y", ModuleTools.schemaProperty("integer", "Absolute Y, or relative dy."));
            props.add("z", ModuleTools.schemaProperty("integer", "Absolute Z, or relative dz."));
            return ModuleTools.schema(props, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                requireInWorld();
                final BlockPos playerFeet = new BlockPos(mc().thePlayer);

                final int x;
                final int y;
                final int z;
                if (args.has("x") && args.has("y") && args.has("z")
                        && args.get("x").isJsonPrimitive() && args.get("y").isJsonPrimitive()
                        && args.get("z").isJsonPrimitive()) {
                    x = args.get("x").getAsInt();
                    y = args.get("y").getAsInt();
                    z = args.get("z").getAsInt();
                } else {
                    x = playerFeet.getX() + (args.has("x") ? args.get("x").getAsInt() : 0);
                    y = playerFeet.getY() + (args.has("y") ? args.get("y").getAsInt() : 0);
                    z = playerFeet.getZ() + (args.has("z") ? args.get("z").getAsInt() : 0);
                }

                final BlockPos pos = new BlockPos(x, y, z);
                final JsonObject out = new JsonObject();
                out.addProperty("x", x);
                out.addProperty("y", y);
                out.addProperty("z", z);
                out.addProperty("loaded", mc().theWorld.isBlockLoaded(pos));
                if (mc().theWorld.isBlockLoaded(pos)) {
                    final Block block = mc().theWorld.getBlockState(pos).getBlock();
                    final String id = net.minecraft.block.Block.blockRegistry.getNameForObject(block) == null
                            ? block.getClass().getSimpleName()
                            : net.minecraft.block.Block.blockRegistry.getNameForObject(block).toString().replace("minecraft:", "");
                    out.addProperty("block", id);
                    out.addProperty("meta", mc().theWorld.getBlockState(pos).getBlock().getMetaFromState(
                            mc().theWorld.getBlockState(pos)));
                    out.addProperty("renderType", block.getRenderType());
                }
                return out;
            });
        }
    }
}
