package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import redacted.client.Client;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;

import java.util.Arrays;
import java.util.concurrent.Callable;

/** Config profile tools -- the same save/load/list/delete surface as .config. */
public final class ConfigTools {

    private ConfigTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new ListConfigs());
        server.registerTool(new SaveConfig(dispatcher));
        server.registerTool(new LoadConfig(dispatcher));
        server.registerTool(new DeleteConfig(dispatcher));
    }

    private static String[] listConfigs() {
        return Client.getInstance().getConfigManager().list();
    }

    private static void requireExists(final String name) {
        for (final String existing : listConfigs()) {
            if (existing.equalsIgnoreCase(name)) return;
        }
        throw new McpToolException("Config \"" + name + "\" not found. Available: " + Arrays.toString(listConfigs()));
    }

    private static final class ListConfigs implements McpTool {
        @Override
        public String name() {
            return "config_list";
        }

        @Override
        public String description() {
            return "List saved config profiles.";
        }

        @Override
        public JsonObject inputSchema() {
            return ModuleTools.schema(new JsonObject(), null);
        }

        @Override
        public Object execute(final JsonObject args) {
            final JsonArray configs = new JsonArray();
            for (final String name : listConfigs()) configs.add(new com.google.gson.JsonPrimitive(name));

            final JsonObject result = new JsonObject();
            result.add("configs", configs);
            result.addProperty("total", configs.size());
            return result;
        }
    }

    private static final class SaveConfig implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SaveConfig(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "config_save";
        }

        @Override
        public String description() {
            return "Save the current client state (modules, settings, keybinds) to a config profile. "
                    + "Omit \"name\" to update the auto-saved latest.json.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description", "Profile name; defaults to \"latest\".");
            properties.add("name", name);
            return ModuleTools.schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String name = args.has("name") && args.get("name").isJsonPrimitive()
                    ? args.get("name").getAsString().trim() : "latest";
            if (name.isEmpty()) throw new McpToolException("Config name must not be empty");

            this.dispatcher.call(new Callable<Object>() {
                @Override
                public Object call() {
                    Client.getInstance().getConfigManager().save(name);
                    return null;
                }
            });

            final JsonObject result = new JsonObject();
            result.addProperty("saved", name);
            return result;
        }
    }

    private static final class LoadConfig implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private LoadConfig(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "config_load";
        }

        @Override
        public String description() {
            return "Load a config profile, applying its modules/settings/keybinds.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description", "Profile name (see config_list).");
            properties.add("name", name);
            return ModuleTools.schema(properties, Arrays.asList("name"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String name = ModuleTools.requireString(args, "name");
            requireExists(name);

            this.dispatcher.call(new Callable<Object>() {
                @Override
                public Object call() {
                    Client.getInstance().getConfigManager().load(name);
                    return null;
                }
            });

            final JsonObject result = new JsonObject();
            result.addProperty("loaded", name);
            return result;
        }
    }

    private static final class DeleteConfig implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private DeleteConfig(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "config_delete";
        }

        @Override
        public String description() {
            return "Delete a saved config profile.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject name = new JsonObject();
            name.addProperty("type", "string");
            name.addProperty("description", "Profile name (see config_list).");
            properties.add("name", name);
            return ModuleTools.schema(properties, Arrays.asList("name"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String name = ModuleTools.requireString(args, "name");
            requireExists(name);

            this.dispatcher.call(new Callable<Object>() {
                @Override
                public Object call() {
                    Client.getInstance().getConfigManager().delete(name);
                    return null;
                }
            });

            final JsonObject result = new JsonObject();
            result.addProperty("deleted", name);
            return result;
        }
    }
}
