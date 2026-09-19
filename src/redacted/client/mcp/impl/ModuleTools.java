package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.module.Module;
import redacted.client.module.ModuleManager;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;
import redacted.client.mcp.McpToolException;
import redacted.client.value.Value;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.client.value.impl.RangeValue;
import redacted.client.value.impl.StringValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Module control tools: discovery, enable/disable, settings (sliders/checkboxes/modes/
 * colors/ranges) and keybinds -- everything the ClickGUI can do per module.
 */
public final class ModuleTools {

    private ModuleTools() {
    }

    public static void registerAll(final McpServer server, final MainThreadDispatcher dispatcher) {
        server.registerTool(new ListModules());
        server.registerTool(new ListModuleSettings());
        server.registerTool(new EnableModule(dispatcher, true));
        server.registerTool(new EnableModule(dispatcher, false));
        server.registerTool(new ToggleModule(dispatcher));
        server.registerTool(new SetModuleSetting(dispatcher));
        server.registerTool(new SetModuleKeybind(dispatcher));
        server.registerTool(new ResetModuleSettings(dispatcher));
        server.registerTool(new EnsureModulesEnabled(dispatcher));
    }

    // shared helpers

    static Module findModule(final String name) {
        final Module module = Client.getInstance().getModuleManager().getModule(name);
        if (module == null) throw new McpToolException("Unknown module: " + name);
        return module;
    }

    static JsonObject describeModule(final Module module) {
        final JsonObject o = new JsonObject();
        o.addProperty("name", module.getName());
        o.addProperty("description", module.getDescription());
        o.addProperty("category", module.getCategory() == null ? "" : module.getCategory().name());
        o.addProperty("enabled", module.isEnabled());
        o.addProperty("key", module.getKey());
        o.addProperty("keyName", Keyboard.getKeyName(module.getKey()));
        return o;
    }

    /** Same "<parent>/<name>" convention the config file uses for child settings. */
    private static String settingKey(final Value<?> value) {
        return value.getParent() == null ? value.getName() : value.getParent().getName() + "/" + value.getName();
    }

    private static Value<?> findValue(final Module module, final String setting) {
        final String wanted = setting.toLowerCase(Locale.ROOT);
        Value<?> plainMatch = null;
        for (final Value<?> value : module.getSettings()) {
            if (settingKey(value).toLowerCase(Locale.ROOT).equals(wanted)) return value;
            if (plainMatch == null && value.getName().toLowerCase(Locale.ROOT).equals(wanted)) plainMatch = value;
        }
        if (plainMatch == null) {
            throw new McpToolException("Unknown setting \"" + setting + "\" on module " + module.getName()
                    + ". Use list_module_settings to see valid names.");
        }
        return plainMatch;
    }

    private static JsonElement currentValue(final Value<?> value) {
        return new com.google.gson.Gson().toJsonTree(value.getValue());
    }

    private static JsonObject describeSetting(final Value<?> value) {
        final JsonObject o = new JsonObject();
        o.addProperty("name", settingKey(value));
        o.addProperty("type", typeName(value));
        o.add("value", currentValue(value));
        o.addProperty("visible", value.isVisible());
        if (value.getDescription() != null && !value.getDescription().isEmpty()) {
            o.addProperty("description", value.getDescription());
        }
        if (value.getParent() != null) {
            o.addProperty("parent", value.getParent().getName());
        }

        if (value instanceof NumberValue) {
            final NumberValue number = (NumberValue) value;
            o.addProperty("min", number.getMinimum());
            o.addProperty("max", number.getMaximum());
            o.addProperty("increment", number.getIncrement());
        } else if (value instanceof RangeValue) {
            final RangeValue range = (RangeValue) value;
            o.addProperty("min", range.getMinimum());
            o.addProperty("max", range.getMaximum());
            o.addProperty("increment", range.getIncrement());
        } else if (value instanceof ModeValue) {
            final JsonArray modes = new JsonArray();
            for (final String mode : ((ModeValue) value).getModes()) modes.add(new JsonPrimitive(mode));
            o.add("modes", modes);
        }
        return o;
    }

    private static String typeName(final Value<?> value) {
        if (value instanceof BooleanValue) return "boolean";
        if (value instanceof NumberValue) return "number";
        if (value instanceof RangeValue) return "range";
        if (value instanceof ModeValue) return "mode";
        if (value instanceof ColorValue) return "color";
        if (value instanceof StringValue) return "string";
        return value.getValue() instanceof List ? "list" : "unknown";
    }

    /**
     * Type-directed coercion with real errors (unlike applyConfig which silently ignores
     * bad input), falling back to each value's own config parser for exotic types.
     *
     * @return the previous raw value
     */
    private static JsonElement coerceAndSet(final Value<?> value, final JsonElement raw) {
        try {
            if (value instanceof BooleanValue) {
                final Boolean parsed = parseBoolean(raw);
                if (parsed == null) throw new McpToolException("Expected boolean (true/false) for \"" + value.getName() + "\"");
                final JsonElement old = currentValue(value);
                ((BooleanValue) value).setValue(parsed);
                return old;
            }

            if (value instanceof NumberValue) {
                final Double parsed = parseDouble(raw);
                if (parsed == null) throw new McpToolException("Expected a number for \"" + value.getName() + "\"");
                final JsonElement old = currentValue(value);
                ((NumberValue) value).setValue(parsed); // setValue clamps to min/max
                return old;
            }

            if (value instanceof RangeValue) {
                double lo;
                double hi;
                if (raw != null && raw.isJsonArray() && raw.getAsJsonArray().size() >= 2) {
                    lo = raw.getAsJsonArray().get(0).getAsDouble();
                    hi = raw.getAsJsonArray().get(1).getAsDouble();
                } else if (raw != null && raw.isJsonObject()) {
                    final JsonObject obj = raw.getAsJsonObject();
                    if (!obj.has("min") || !obj.has("max")) {
                        throw new McpToolException("Range needs {\"min\":x,\"max\":y} or [lo,hi]");
                    }
                    lo = obj.get("min").getAsDouble();
                    hi = obj.get("max").getAsDouble();
                } else {
                    throw new McpToolException("Range needs [lo,hi] or {\"min\":x,\"max\":y}");
                }
                final JsonElement old = currentValue(value);
                ((RangeValue) value).setValue(new double[] { lo, hi });
                return old;
            }

            if (value instanceof ModeValue) {
                if (raw == null || !raw.isJsonPrimitive()) {
                    throw new McpToolException("Expected a mode string for \"" + value.getName() + "\"");
                }
                final String wanted = raw.getAsString();
                String match = null;
                for (final String mode : ((ModeValue) value).getModes()) {
                    if (mode.equalsIgnoreCase(wanted)) {
                        match = mode;
                        break;
                    }
                }
                if (match == null) {
                    throw new McpToolException("\"" + wanted + "\" is not a valid mode for \"" + value.getName()
                            + "\". Valid: " + ((ModeValue) value).getModes());
                }
                final JsonElement old = currentValue(value);
                ((ModeValue) value).setValue(match);
                return old;
            }

            if (value instanceof ColorValue) {
                final Integer rgb = parseColor(raw);
                if (rgb == null) {
                    throw new McpToolException("Color needs an RGB int or \"#rrggbb\" string");
                }
                final JsonElement old = currentValue(value);
                @SuppressWarnings("unchecked")
                final Value<Integer> colorValue = (Value<Integer>) value;
                colorValue.setValue(rgb);
                ((ColorValue) value).syncFromValue();
                return old;
            }

            if (value instanceof StringValue) {
                if (raw == null || !raw.isJsonPrimitive()) {
                    throw new McpToolException("Expected a string for \"" + value.getName() + "\"");
                }
                final JsonElement old = currentValue(value);
                ((StringValue) value).setValue(raw.getAsString());
                return old;
            }

            // exotic values (item grids etc.) -- let each type run its own config parser
            final JsonElement old = currentValue(value);
            value.applyConfig(toPlainObject(raw));
            return old;
        } catch (final McpToolException e) {
            throw e;
        } catch (final Exception e) {
            throw new McpToolException("Failed to set \"" + value.getName() + "\": " + e.getMessage(), e);
        }
    }

    private static Boolean parseBoolean(final JsonElement raw) {
        if (raw == null || !raw.isJsonPrimitive()) return null;
        final JsonPrimitive primitive = raw.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsInt() != 0;
        final String text = primitive.getAsString().trim();
        if ("true".equalsIgnoreCase(text)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(text)) return Boolean.FALSE;
        return null;
    }

    private static Double parseDouble(final JsonElement raw) {
        if (raw == null || !raw.isJsonPrimitive()) return null;
        final JsonPrimitive primitive = raw.getAsJsonPrimitive();
        if (primitive.isNumber()) return primitive.getAsDouble();
        try {
            return Double.valueOf(primitive.getAsString().trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseColor(final JsonElement raw) {
        if (raw == null || !raw.isJsonPrimitive()) return null;
        final JsonPrimitive primitive = raw.getAsJsonPrimitive();
        if (primitive.isNumber()) return primitive.getAsInt();
        String text = primitive.getAsString().trim();
        if (text.startsWith("#")) text = text.substring(1);
        else if (text.startsWith("0x") || text.startsWith("0X")) text = text.substring(2);
        try {
            return (int) (Long.parseLong(text, 16) & 0xFFFFFF);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /** Gson-decoded equivalent of a JSON element (Boolean/Double/String/List), for applyConfig. */
    private static Object toPlainObject(final JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            final java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
            for (final java.util.Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), toPlainObject(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            final List<Object> list = new ArrayList<Object>();
            for (final JsonElement child : element.getAsJsonArray()) list.add(toPlainObject(child));
            return list;
        }
        final JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsDouble();
        return primitive.getAsString();
    }

    // tools

    private static final class ListModules implements McpTool {
        @Override
        public String name() {
            return "list_modules";
        }

        @Override
        public String description() {
            return "List client modules with category and enabled state. Optionally filter by "
                    + "enabled state or category (combat/movement/render/world/utility/cosmetics).";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject enabled = new JsonObject();
            enabled.addProperty("type", "boolean");
            enabled.addProperty("description", "Only return modules whose enabled state matches this.");
            properties.add("enabled", enabled);
            final JsonObject category = new JsonObject();
            category.addProperty("type", "string");
            category.addProperty("description", "Filter by category name (case-insensitive).");
            properties.add("category", category);

            return schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) {
            final Boolean enabledFilter = args.has("enabled") && args.get("enabled").isJsonPrimitive()
                    ? args.get("enabled").getAsBoolean() : null;
            final String categoryFilter = args.has("category") && args.get("category").isJsonPrimitive()
                    ? args.get("category").getAsString() : null;

            final JsonArray modules = new JsonArray();
            int total = 0;
            for (final Module module : Client.getInstance().getModuleManager().getModules()) {
                if (enabledFilter != null && module.isEnabled() != enabledFilter) continue;
                if (categoryFilter != null
                        && (module.getCategory() == null || !module.getCategory().name().equalsIgnoreCase(categoryFilter))) {
                    continue;
                }
                modules.add(describeModule(module));
                total++;
            }

            final JsonObject result = new JsonObject();
            result.add("modules", modules);
            result.addProperty("total", total);
            return result;
        }
    }

    private static final class ListModuleSettings implements McpTool {
        @Override
        public String name() {
            return "list_module_settings";
        }

        @Override
        public String description() {
            return "List module settings (values): current value, type, min/max/increment for sliders, "
                    + "modes for dropdowns. Pass \"module\" for one module; omit it for every module.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject module = new JsonObject();
            module.addProperty("type", "string");
            module.addProperty("description", "Module name. Omit to list settings of all modules.");
            properties.add("module", module);
            return schema(properties, null);
        }

        @Override
        public Object execute(final JsonObject args) {
            final ModuleManager manager = Client.getInstance().getModuleManager();

            if (args.has("module") && args.get("module").isJsonPrimitive()) {
                final Module module = findModule(args.get("module").getAsString());

                final JsonObject detail = describeModule(module);
                final JsonArray settings = new JsonArray();
                for (final Value<?> value : module.getSettings()) {
                    settings.add(describeSetting(value));
                }
                detail.add("settings", settings);
                return detail;
            }

            final JsonObject modules = new JsonObject();
            for (final Module module : manager.getModules()) {
                final JsonObject entry = new JsonObject();
                entry.addProperty("enabled", module.isEnabled());
                final JsonArray settings = new JsonArray();
                for (final Value<?> value : module.getSettings()) {
                    settings.add(describeSetting(value));
                }
                entry.add("settings", settings);
                modules.add(module.getName(), entry);
            }
            return modules;
        }
    }

    private static final class EnableModule implements McpTool {
        private final MainThreadDispatcher dispatcher;
        private final boolean enable;

        private EnableModule(MainThreadDispatcher dispatcher, boolean enable) {
            this.dispatcher = dispatcher;
            this.enable = enable;
        }

        @Override
        public String name() {
            return this.enable ? "enable_module" : "disable_module";
        }

        @Override
        public String description() {
            return this.enable ? "Enable a module by name." : "Disable a module by name.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject module = new JsonObject();
            module.addProperty("type", "string");
            module.addProperty("description", "Exact module name (see list_modules).");
            properties.add("module", module);
            return schema(properties, Arrays.asList("module"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String moduleName = requireString(args, "module");
            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Module module = findModule(moduleName);
                    module.setEnabled(EnableModule.this.enable);
                    final JsonObject result = describeModule(module);
                    result.addProperty("changed", true);
                    return result;
                }
            });
        }
    }

    private static final class ToggleModule implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private ToggleModule(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "toggle_module";
        }

        @Override
        public String description() {
            return "Flip a module's enabled state.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject module = new JsonObject();
            module.addProperty("type", "string");
            module.addProperty("description", "Exact module name (see list_modules).");
            properties.add("module", module);
            return schema(properties, Arrays.asList("module"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String moduleName = requireString(args, "module");
            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Module module = findModule(moduleName);
                    module.toggle();
                    return describeModule(module);
                }
            });
        }
    }

    private static final class SetModuleSetting implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SetModuleSetting(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "set_module_setting";
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String moduleName = requireString(args, "module");
            final String settingName = requireString(args, "setting");
            final JsonElement value = args.get("value");

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Module module = findModule(moduleName);
                    final Value<?> target = findValue(module, settingName);
                    if (!target.isVisible()) {
                        throw new McpToolException("Setting \"" + settingName + "\" is hidden right now "
                                + "(its parent setting has a different value).");
                    }
                    final JsonElement oldValue = coerceAndSet(target, value);

                    final JsonObject result = new JsonObject();
                    result.addProperty("module", module.getName());
                    result.addProperty("setting", settingKey(target));
                    result.add("oldValue", oldValue);
                    result.add("newValue", currentValue(target));
                    return result;
                }
            });
        }

        @Override
        public String description() {
            return "Change one module setting (slider value, checkbox, mode, color, range...). "
                    + "The value shape depends on the setting type from list_module_settings: "
                    + "boolean -> true/false, number -> 12.5, range -> [lo,hi], mode -> one of its modes, "
                    + "color -> \"#rrggbb\" or RGB int, string -> text.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();

            final JsonObject module = new JsonObject();
            module.addProperty("type", "string");
            module.addProperty("description", "Exact module name.");
            properties.add("module", module);

            final JsonObject setting = new JsonObject();
            setting.addProperty("type", "string");
            setting.addProperty("description", "Setting name (children use \"Parent/Child\", see list_module_settings).");
            properties.add("setting", setting);

            final JsonObject value = new JsonObject();
            value.addProperty("description", "New value; shape depends on the setting type.");
            properties.add("value", value);

            return schema(properties, Arrays.asList("module", "setting", "value"));
        }
    }

    private static final class SetModuleKeybind implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private SetModuleKeybind(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "set_module_keybind";
        }

        @Override
        public String description() {
            return "Bind a module to a keyboard key (LWJGL key name like \"R\", \"F\", \"NONE\" to unbind, "
                    + "or a raw key code). The key toggles the module when no GUI is open.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();

            final JsonObject module = new JsonObject();
            module.addProperty("type", "string");
            module.addProperty("description", "Exact module name.");
            properties.add("module", module);

            final JsonObject key = new JsonObject();
            key.addProperty("type", "string");
            key.addProperty("description", "LWJGL key name (e.g. \"R\") or numeric key code; \"NONE\" unbinds.");
            properties.add("key", key);

            return schema(properties, Arrays.asList("module", "key"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String moduleName = requireString(args, "module");
            final JsonElement keyElement = args.get("key");
            if (keyElement == null || !keyElement.isJsonPrimitive()) {
                throw new McpToolException("Missing required argument: key");
            }

            final int keyCode;
            final JsonPrimitive primitive = keyElement.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                keyCode = primitive.getAsInt();
            } else {
                final String keyName = primitive.getAsString().trim();
                keyCode = "NONE".equalsIgnoreCase(keyName) ? Keyboard.KEY_NONE : Keyboard.getKeyIndex(keyName.toUpperCase(Locale.ROOT));
                if (keyCode == Keyboard.KEY_NONE && !"NONE".equalsIgnoreCase(keyName)) {
                    throw new McpToolException("Unknown key name: " + keyName);
                }
            }

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Module module = findModule(moduleName);
                    module.setKey(keyCode);
                    final JsonObject result = describeModule(module);
                    result.addProperty("keyName", Keyboard.getKeyName(module.getKey()));
                    return result;
                }
            });
        }
    }

    private static final class ResetModuleSettings implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private ResetModuleSettings(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "reset_module_settings";
        }

        @Override
        public String description() {
            return "Reset every setting of one module back to its default value (keeps enabled state and keybind).";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();
            final JsonObject module = new JsonObject();
            module.addProperty("type", "string");
            module.addProperty("description", "Exact module name.");
            properties.add("module", module);
            return schema(properties, Arrays.asList("module"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            final String moduleName = requireString(args, "module");
            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final Module module = findModule(moduleName);
                    int count = 0;
                    for (final Value<?> value : module.getSettings()) {
                        value.reset();
                        count++;
                    }
                    final JsonObject result = describeModule(module);
                    result.addProperty("resetSettings", count);
                    return result;
                }
            });
        }
    }

    // schema/json helpers shared by the tool classes

    static JsonObject schema(final JsonObject properties, final List<String> required) {
        final JsonObject schemaObject = new JsonObject();
        schemaObject.addProperty("type", "object");
        schemaObject.add("properties", properties);
        if (required != null && !required.isEmpty()) {
            final JsonArray requiredArray = new JsonArray();
            for (final String name : required) requiredArray.add(new JsonPrimitive(name));
            schemaObject.add("required", requiredArray);
        }
        return schemaObject;
    }

    static String requireString(final JsonObject args, final String name) {
        if (!args.has(name) || !args.get(name).isJsonPrimitive()) {
            throw new McpToolException("Missing required argument: " + name);
        }
        final String value = args.get(name).getAsString();
        if (value.trim().isEmpty()) throw new McpToolException("Argument \"" + name + "\" must not be empty");
        return value.trim();
    }

    /** Small JsonArray from string varargs (enum listings etc.). */
    static JsonArray jsonArrayOf(final String... values) {
        final JsonArray array = new JsonArray();
        for (final String value : values) array.add(new JsonPrimitive(value));
        return array;
    }

    /** One property schema node: {"type": type, "description": description}. */
    static JsonObject schemaProperty(final String type, final String description) {
        final JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("description", description);
        return o;
    }

    /** Uniform {"ok":bool,"message":string} tool result. */
    static JsonObject toolResult(final boolean ok, final String message) {
        final JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        if (message != null) o.addProperty("message", message);
        return o;
    }

    // ensure_modules_enabled

    private static final class EnsureModulesEnabled implements McpTool {
        private final MainThreadDispatcher dispatcher;

        private EnsureModulesEnabled(MainThreadDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public String name() {
            return "ensure_modules_enabled";
        }

        @Override
        public String description() {
            return "Idempotent batch-enable: for each named module, report whether it was already "
                    + "enabled and enable it if not. Use this instead of guessing -- e.g. "
                    + "{\"modules\":[\"Sprint\"]} returns {\"Sprint\":{\"wasEnabled\":false,\"enabled\":true}}. "
                    + "For checking-only pass \"set\": false.";
        }

        @Override
        public JsonObject inputSchema() {
            final JsonObject properties = new JsonObject();

            final JsonObject modules = new JsonObject();
            modules.addProperty("type", "array");
            modules.addProperty("items", "string");
            modules.addProperty("description", "Module names (case-insensitive), e.g. [\"Sprint\", \"BridgeAssist\"].");
            properties.add("modules", modules);

            final JsonObject set = new JsonObject();
            set.addProperty("type", "boolean");
            set.addProperty("description", "true (default) = enable the ones that are off; false = only report state.");
            properties.add("set", set);

            return schema(properties, java.util.Arrays.asList("modules"));
        }

        @Override
        public Object execute(final JsonObject args) throws Exception {
            if (!args.has("modules") || !args.get("modules").isJsonArray()) {
                throw new McpToolException("Missing required argument: modules (array of names)");
            }
            boolean applySet = true;
            if (args.has("set") && args.get("set").isJsonPrimitive()) {
                applySet = args.get("set").getAsBoolean();
            }
            final boolean set = applySet;

            final List<String> wanted = new ArrayList<String>();
            for (final JsonElement element : args.getAsJsonArray("modules")) {
                if (element.isJsonPrimitive()) wanted.add(element.getAsString());
            }

            return this.dispatcher.call(new java.util.concurrent.Callable<JsonObject>() {
                @Override
                public JsonObject call() {
                    final JsonObject results = new JsonObject();
                    int changedCount = 0;
                    int errors = 0;
                    for (final String name : wanted) {
                        final Module module;
                        try {
                            module = findModule(name);
                        } catch (final McpToolException e) {
                            final JsonObject err = new JsonObject();
                            err.addProperty("error", e.getMessage());
                            results.add(name, err);
                            errors++;
                            continue;
                        }
                        final JsonObject entry = new JsonObject();
                        entry.addProperty("wasEnabled", module.isEnabled());
                        if (set && !module.isEnabled()) {
                            module.setEnabled(true);
                            entry.addProperty("changed", true);
                            changedCount++;
                        } else {
                            entry.addProperty("changed", false);
                        }
                        entry.addProperty("enabled", module.isEnabled());
                        results.add(module.getName(), entry);
                    }

                    final JsonObject result = new JsonObject();
                    result.add("modules", results);
                    result.addProperty("changed", changedCount);
                    if (errors > 0) {
                        result.addProperty("unknownNames", errors);
                        result.addProperty("message", "Some names did not match any module -- see their "
                                + "\"error\" fields; use list_modules for exact names.");
                    }
                    return result;
                }
            });
        }
    }
}
