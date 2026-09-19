package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.command.Command;
import redacted.client.module.Module;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

import java.util.Arrays;

/** Introspection + command-execution tools: commands, keybinds, client info. */
public final class InfoTools {

    private InfoTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new ListCommands());
        server.registerTool(new ListKeybinds());
        server.registerTool(new ExecuteCommand(dispatcher));
        server.registerTool(new GetClientInfo());
    }

    private static final class ListCommands implements McpTool {
        @Override
        public String name() {
            return "list_commands";
        }

        @Override
        public String description() {
            return "List the built-in chat commands (the \".\" commands) with descriptions.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) {
            final JsonArray commands = new JsonArray();
            for (final Command command : Client.getInstance().getCommandManager().getCommands()) {
                final JsonObject o = new JsonObject();
                o.addProperty("name", "." + command.getName());
                o.addProperty("description", command.getDescription());
                commands.add(o);
            }

            final JsonObject result = new JsonObject();
            result.add("commands", commands);
            result.addProperty("total", commands.size());
            return result;
        }
    }

    private static final class ListKeybinds implements McpTool {
        @Override
        public String name() {
            return "list_keybinds";
        }

        @Override
        public String description() {
            return "List module keybinds. By default only bound modules are returned; pass "
                    + "\"includeUnbound\": true to also list unbound ones.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject includeUnbound = new JsonObject();
            includeUnbound.addProperty("type", "boolean");
            includeUnbound.addProperty("description", "Also include modules without a keybind.");
            properties.add("includeUnbound", includeUnbound);
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) {
            boolean includeUnbound = false;
            if (args.has("includeUnbound") && args.get("includeUnbound").isJsonPrimitive()) {
                includeUnbound = args.get("includeUnbound").getAsBoolean();
            }

            final JsonArray keybinds = new JsonArray();
            for (final Module module : Client.getInstance().getModuleManager().getModules()) {
                if (!includeUnbound && module.getKey() == Keyboard.KEY_NONE) continue;
                keybinds.add(ModuleTools.describeModule(module));
            }

            final JsonObject result = new JsonObject();
            result.add("keybinds", keybinds);
            result.addProperty("total", keybinds.size());
            return result;
        }
    }

    private static final class ExecuteCommand implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private ExecuteCommand(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "execute_command";
        }

        @Override
        public String description() {
            return "Run a built-in chat command exactly like typing it in-game, e.g. \".toggle Sprint\" "
                    + "or \".config save myconfig\". Feedback the command prints shows up in the player's chat.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject command = new JsonObject();
            command.addProperty("type", "string");
            command.addProperty("description", "Full command text including the leading dot.");
            properties.add("command", command);
            return ModuleTools.schema(properties, Arrays.asList("command"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            String command = ModuleTools.requireString(args, "command");
            final String prefix = Client.getInstance().getCommandManager().getPrefix();
            if (!command.startsWith(prefix)) command = prefix + command;

            final String finalCommand = command;
            final Boolean handled = this.dispatcher.call(new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return Client.getInstance().getCommandManager().handleInput(finalCommand);
                }
            });

            final JsonObject result = new JsonObject();
            result.addProperty("command", finalCommand);
            result.addProperty("handled", handled);
            return result;
        }
    }

    private static final class GetClientInfo implements McpTool {
        @Override
        public String name() {
            return "get_client_info";
        }

        @Override
        public String description() {
            return "Client identity and state summary: version, build type, world status, module counts, MCP status.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) {
            final Minecraft mc = Minecraft.getMinecraft();
            final redacted.client.mcp.McpManager mcp = redacted.client.mcp.McpManager.getInstance();

            int enabled = 0;
            for (final Module module : Client.getInstance().getModuleManager().getModules()) {
                if (module.isEnabled()) enabled++;
            }

            final JsonObject result = new JsonObject();
            result.addProperty("client", Client.NAME);
            result.addProperty("version", Client.VERSION);
            result.addProperty("buildType", Client.BUILD_TYPE);
            result.addProperty("devBuild", Client.DEV_BUILD);
            result.addProperty("inWorld", mc != null && mc.thePlayer != null && mc.theWorld != null);

            final JsonObject modules = new JsonObject();
            modules.addProperty("total", Client.getInstance().getModuleManager().getModules().size());
            modules.addProperty("enabled", enabled);
            result.add("modules", modules);

            final JsonObject mcpStatus = new JsonObject();
            mcpStatus.addProperty("running", mcp.isRunning());
            mcpStatus.addProperty("port", mcp.getPort());
            mcpStatus.addProperty("connections", mcp.getConnectionCount());
            result.add("mcp", mcpStatus);
            return result;
        }
    }
}
