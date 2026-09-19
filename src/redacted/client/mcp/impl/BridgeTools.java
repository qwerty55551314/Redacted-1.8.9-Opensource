package redacted.client.mcp.impl;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import redacted.client.Client;
import redacted.client.baritone.AutoBridgeController;
import redacted.client.module.Module;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

/**
 * LLM-directed bridging. Three methods:
 *
 * <ul>
 *   <li>{@code assist} (default) -- the BridgeAssist method: the client turns its real camera
 *       backwards-and-down, sneak-bridges toward the target and right-clicks place on whatever
 *       support face it raytraces. Pure vanilla inputs, no rotation spoofing -- the most
 *       bypass-safe bridging in the client.</li>
 *   <li>{@code native} -- hand the goal to Baritone; it paths and places its own throwaway
 *       blocks across gaps (configure via baritone_set_throwaway_blocks).</li>
 *   <li>{@code scaffold} -- enable the Scaffold module, then drive with hold_key("forward");
 *       Scaffold owns look + placement. Turn Scaffold off afterwards.</li>
 * </ul>
 */
public final class BridgeTools {

    private BridgeTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new BridgeTo(dispatcher));
        server.registerTool(new BridgeStatus());
        server.registerTool(new BridgeStop(dispatcher));
    }

    private static void requireInWorld() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            throw new McpToolException("Not in a world.");
        }
    }

    // bridge_to

    private static final class BridgeTo implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private BridgeTo(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "bridge_to";
        }

        @Override
        public String description() {
            return "Bridge (place a block path) from the current position toward x/z on the current "
                    + "deck plane, straight line only. Methods: \"assist\" (default) = automated "
                    + "sneak-backwards bridge, vanilla inputs, safest vs anticheats; \"native\" = "
                    + "Baritone paths there placing its own throwaway blocks (call "
                    + "baritone_set_throwaway_blocks first); \"scaffold\" = just enables the Scaffold "
                    + "module so you can pair it with hold_key(\"forward\"). For climbing up a ledge "
                    + "afterwards use baritone_goto.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();

            final JsonObject x = new JsonObject();
            x.addProperty("type", "number");
            x.addProperty("description", "Target block X to bridge to.");
            properties.add("x", x);

            final JsonObject z = new JsonObject();
            z.addProperty("type", "number");
            z.addProperty("description", "Target block Z to bridge to.");
            properties.add("z", z);

            final JsonObject y = new JsonObject();
            y.addProperty("type", "integer");
            y.addProperty("description", "Optional deck Y. Omit = current feet Y (flat bridge).");
            properties.add("y", y);

            final JsonObject method = new JsonObject();
            method.addProperty("type", "string");
            method.add("enum", ModuleTools.jsonArrayOf("assist", "native", "scaffold"));
            method.addProperty("description", "Bridging mechanism. Default \"assist\".");
            properties.add("method", method);

            return ModuleTools.schema(properties, java.util.Arrays.asList("x", "z"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            requireInWorld();
            if (!args.has("x") || !args.get("x").isJsonPrimitive()
                    || !args.has("z") || !args.get("z").isJsonPrimitive()) {
                throw new McpToolException("Missing required arguments: x, z");
            }
            final double tx = args.get("x").getAsDouble();
            final double tz = args.get("z").getAsDouble();
            final Integer ty = args.has("y") && args.get("y").isJsonPrimitive()
                    ? Integer.valueOf(args.get("y").getAsInt()) : null;

            String method = "assist";
            if (args.has("method") && args.get("method").isJsonPrimitive()) {
                method = args.get("method").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            }

            if ("assist".equals(method)) {
                final String error = this.dispatcher.call(
                        new java.util.concurrent.Callable<String>() {
                            @Override
                            public String call() {
                                return AutoBridgeController.getInstance().start(tx, tz, ty);
                            }
                        });
                if (error != null) return ModuleTools.toolResult(false, error);

                return ModuleTools.toolResult(true, "Assisted bridging started toward ("
                        + (int) Math.floor(tx) + ", " + (int) Math.floor(tz) + "). Poll bridge_status; "
                        + "it stops itself on arrival / out-of-blocks / stuck.");
            }

            if ("native".equals(method)) {
                this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                    @Override
                    public Void call() {
                        if (ty != null) {
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                                    .setGoalAndPath(new GoalBlock((int) Math.floor(tx), ty, (int) Math.floor(tz)));
                        } else {
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                                    .setGoalAndPath(new GoalXZ((int) Math.floor(tx), (int) Math.floor(tz)));
                        }
                        return null;
                    }
                });
                return ModuleTools.toolResult(true, "Baritone native pathing started toward ("
                        + (int) Math.floor(tx) + ", " + (int) Math.floor(tz) + "). Baritone bridges gaps "
                        + "with its throwaway blocks; ensure they are set (baritone_set_throwaway_blocks).");
            }

            if ("scaffold".equals(method)) {
                final Boolean enabled = this.dispatcher.call(new java.util.concurrent.Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        final Module scaffold = Client.getInstance().getModuleManager().getModule("Scaffold");
                        if (scaffold == null) throw new McpToolException("Scaffold module not found");
                        if (!scaffold.isEnabled()) scaffold.setEnabled(true);
                        return scaffold.isEnabled();
                    }
                });
                return ModuleTools.toolResult(enabled,
                        enabled ? "Scaffold module enabled. Now move yourself: hold_key {key:\"forward\", "
                                + "duration_ms:...} toward the target, or combine with look_at. Disable "
                                + "Scaffold when done."
                                : "Failed to enable the Scaffold module.");
            }

            throw new McpToolException("Unknown method \"" + method + "\" (assist|native|scaffold)");
        }
    }

    // bridge_status

    private static final class BridgeStatus implements McpTool {
        @Override
        public String name() {
            return "bridge_status";
        }

        @Override
        public String description() {
            return "Status of assisted bridging: active, phase/status string, plus whether Baritone "
                    + "is currently pathing.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) {
            final EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            boolean pathing = false;
            try {
                pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
            } catch (final Throwable ignored) {
            }

            final JsonObject result = new JsonObject();
            final AutoBridgeController bridge = AutoBridgeController.getInstance();
            result.addProperty("active", bridge.isActive());
            result.addProperty("status", bridge.getStatus());
            result.addProperty("baritonePathing", pathing);
            if (bridge.isActive() && player != null) {
                final double dx = bridge.getTargetX() + 0.5D - player.posX;
                final double dz = bridge.getTargetZ() + 0.5D - player.posZ;
                result.addProperty("distanceToTarget",
                        Math.round(Math.sqrt(dx * dx + dz * dz) * 10.0D) / 10.0D);
            } else {
                result.addProperty("distanceToTarget", -1);
            }
            return result;
        }
    }

    // bridge_stop

    private static final class BridgeStop implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private BridgeStop(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "bridge_stop";
        }

        @Override
        public String description() {
            return "Stop assisted bridging immediately (releases keys, restores hotbar slot).";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            this.dispatcher.call(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    AutoBridgeController.getInstance().stop("stopped by MCP");
                    return null;
                }
            });
            return ModuleTools.toolResult(true, "Bridging stopped.");
        }
    }
}
