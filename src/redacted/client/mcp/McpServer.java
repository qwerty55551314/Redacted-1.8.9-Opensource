package redacted.client.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redacted.client.Client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loopback MCP (Model Context Protocol) server. Speaks JSON-RPC 2.0, one request per
 * line, over a 127.0.0.1 TCP socket so local agent tooling can attach to the client.
 *
 * <p>Implemented methods: {@code initialize}, {@code ping}, {@code tools/list},
 * {@code tools/call} plus notification tolerance ({@code notifications/initialized}).
 * Tool execution errors are reported in-result ({@code isError:true}) per the spec;
 * unknown methods/tools get JSON-RPC errors.
 */
public final class McpServer {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    private static final int MAX_LINE_CHARS = 4 * 1024 * 1024; // tool results can be large

    private final int port;
    private final Gson gson = new Gson();
    private final Map<String, McpTool> tools = new LinkedHashMap<String, McpTool>();
    private final Set<Connection> connections = Collections.newSetFromMap(new ConcurrentHashMap<Connection, Boolean>());

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    public McpServer(int port) {
        this.port = port;
    }

    public void registerTool(McpTool tool) {
        if (tool != null && tool.name() != null) this.tools.put(tool.name(), tool);
    }

    /** Binds and starts accepting. Throws BindException when the port is taken. */
    public synchronized void start() throws IOException {
        if (this.running) return;

        this.serverSocket = new ServerSocket();
        // loopback only -- never expose the client to the network
        this.serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), this.port), 8);
        this.running = true;

        this.acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                McpServer.this.acceptLoop();
            }
        }, "Redacted-MCP-Accept");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    public synchronized void stop() {
        this.running = false;

        try {
            if (this.serverSocket != null) this.serverSocket.close();
        } catch (final IOException ignored) {
        }

        for (final Connection connection : this.connections) {
            connection.close();
        }
        this.connections.clear();

        if (this.acceptThread != null) this.acceptThread.interrupt();
        this.acceptThread = null;
        this.serverSocket = null;
    }

    public boolean isRunning() {
        return this.running;
    }

    public int getPort() {
        return this.port;
    }

    public int getConnectionCount() {
        return this.connections.size();
    }

    public Map<String, McpTool> getTools() {
        return Collections.unmodifiableMap(this.tools);
    }

    private void acceptLoop() {
        while (this.running) {
            try {
                final Socket socket = this.serverSocket.accept();
                final Connection connection = new Connection(socket);
                this.connections.add(connection);
                connection.start();
            } catch (final IOException e) {
                if (this.running) {
                    // accept failed unexpectedly; back off briefly so a broken socket
                    // can't spin the thread
                    sleepQuietly(200L);
                }
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // connection

    private final class Connection {
        private final Socket socket;
        private final Object writeLock = new Object();
        private BufferedWriter writer;

        private Connection(Socket socket) {
            this.socket = socket;
        }

        private void start() {
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Connection.this.run();
                }
            }, "Redacted-MCP-Conn");
            thread.setDaemon(true);
            thread.start();
        }

        private void run() {
            try {
                this.socket.setSoTimeout(0);
                this.writer = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8));

                final BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = readBoundedLine(reader)) != null) {
                    if (line.trim().isEmpty()) continue;
                    this.handle(line);
                }
            } catch (final IOException ignored) {
                // client went away
            } finally {
                this.close();
                connections.remove(this);
            }
        }

        private void handle(final String line) {
            JsonObject request = null;
            try {
                request = new JsonParser().parse(line).getAsJsonObject();
            } catch (final Exception ignored) {
            }

            if (request == null || !request.has("method")) {
                this.sendError(null, -32600, "Invalid Request");
                return;
            }

            final String method = request.get("method").isJsonPrimitive()
                    ? request.get("method").getAsString()
                    : "";
            final JsonElement id = request.get("id");

            try {
                final JsonObject result = dispatch(method, request.get("params"));
                // no id => notification: nothing to reply to
                if (id == null || id.isJsonNull()) return;
                this.sendResult(id, result == null ? new JsonObject() : result);
            } catch (final McpToolException e) {
                if (id == null || id.isJsonNull()) return;
                this.sendError(id, e.getCode(), e.getMessage());
            } catch (final Exception e) {
                if (id == null || id.isJsonNull()) return;
                this.sendError(id, -32603, "Internal error: " + e.getMessage());
            }
        }

        private JsonObject dispatch(final String method, final JsonElement paramsElement) throws Exception {
            final JsonObject params = paramsElement != null && paramsElement.isJsonObject()
                    ? paramsElement.getAsJsonObject()
                    : new JsonObject();

            if ("initialize".equals(method)) {
                final JsonObject result = new JsonObject();
                result.addProperty("protocolVersion", PROTOCOL_VERSION);
                final JsonObject capabilities = new JsonObject();
                capabilities.add("tools", new JsonObject());
                result.add("capabilities", capabilities);
                final JsonObject serverInfo = new JsonObject();
                serverInfo.addProperty("name", Client.NAME);
                serverInfo.addProperty("version", Client.VERSION);
                result.add("serverInfo", serverInfo);
                result.addProperty("instructions",
                        "Tools for controlling the " + Client.NAME + " Minecraft client. "
                                + "MODULES: list_modules / ensure_modules_enabled (idempotent on/off, e.g. "
                                + "\"Sprint\" = autosprint) / enable_module / disable_module / "
                                + "list_module_settings / set_module_setting. "
                                + "SENSE: get_player_state (health/armor/held), get_inventory, "
                                + "get_players_nearby, get_chests_nearby, get_blocks_nearby, "
                                + "sense_surroundings (one-call snapshot), baritone_status. "
                                + "MOVE: baritone_goto / baritone_sprint_to (auto-sprints there), "
                                + "baritone_follow {player}, baritone_mine {blocks:[..],count}, "
                                + "baritone_stop; set hazards via baritone_set_avoid_blocks "
                                + "(e.g. [\"cobweb\",\"water\"]). "
                                + "BRIDGE: bridge_to {x,z,method} -- \"assist\" sneak-backward vanilla bridge "
                                + "(safest), \"native\" Baritone places throwaway blocks (set them first with "
                                + "baritone_set_throwaway_blocks), \"scaffold\" enables the Scaffold module to "
                                + "pair with hold_key(\"forward\"). Poll bridge_status. "
                                + "ACT: look_at, left_click, right_click (crosshair), use_item_named {name} "
                                + "(e.g. lobby \"Play Again\" items), select_hotbar_slot, hold_key, "
                                + "open_inventory (lets InvManager clean while open). "
                                + "CONTAINERS: get_open_container then container_click {slot,mode,button} for "
                                + "chests/crafting/furnace/inventory-grid interactions. "
                                + "Typical Skywars loop: sense_surroundings -> decide -> act -> re-sense.");
                return result;
            }

            if ("ping".equals(method)) {
                return new JsonObject();
            }

            if ("tools/list".equals(method)) {
                final JsonArray array = new JsonArray();
                for (final McpTool tool : tools.values()) {
                    final JsonObject descriptor = new JsonObject();
                    descriptor.addProperty("name", tool.name());
                    descriptor.addProperty("description", tool.description());
                    descriptor.add("inputSchema", tool.inputSchema());
                    array.add(descriptor);
                }
                final JsonObject result = new JsonObject();
                result.add("tools", array);
                return result;
            }

            if ("tools/call".equals(method)) {
                final String name = params.has("name") && params.get("name").isJsonPrimitive()
                        ? params.get("name").getAsString()
                        : null;
                final McpTool tool = name != null ? tools.get(name) : null;
                if (tool == null) throw new McpToolException("Unknown tool: " + name);

                final JsonElement arguments = params.get("arguments");
                final JsonObject args = arguments != null && arguments.isJsonObject()
                        ? arguments.getAsJsonObject()
                        : new JsonObject();

                try {
                    final Object out = tool.execute(args);
                    final JsonObject content = new JsonObject();
                    content.addProperty("type", "text");
                    content.addProperty("text", out == null ? "ok" : gson.toJson(out));

                    final JsonObject result = new JsonObject();
                    final JsonArray contentArray = new JsonArray();
                    contentArray.add(content);
                    result.add("content", contentArray);
                    result.addProperty("isError", false);
                    return result;
                } catch (final McpToolException e) {
                    return toolError(e.getMessage());
                } catch (final Exception e) {
                    final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    return toolError(message);
                }
            }

            // known notifications we deliberately ignore
            if (method.startsWith("notifications/")) {
                return null;
            }

            throw new McpToolException("Method not found: " + method.toLowerCase(Locale.ROOT), -32601);
        }

        private JsonObject toolError(final String message) {
            final JsonObject content = new JsonObject();
            content.addProperty("type", "text");
            content.addProperty("text", message == null ? "tool failed" : message);

            final JsonArray contentArray = new JsonArray();
            contentArray.add(content);

            final JsonObject result = new JsonObject();
            result.add("content", contentArray);
            result.addProperty("isError", true);
            return result;
        }

        private synchronized void sendResult(final JsonElement id, final JsonObject result) {
            final JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            response.add("result", result);
            this.write(gson.toJson(response));
        }

        private synchronized void sendError(final JsonElement id, final int code, final String message) {
            final JsonObject error = new JsonObject();
            error.addProperty("code", code);
            error.addProperty("message", message == null ? "error" : message);

            final JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            if (id != null) response.add("id", id);
            response.add("error", error);
            this.write(gson.toJson(response));
        }

        private void write(final String json) {
            synchronized (this.writeLock) {
                try {
                    if (this.writer == null) return;
                    this.writer.write(json);
                    this.writer.write("\n");
                    this.writer.flush();
                } catch (final IOException ignored) {
                    this.close();
                }
            }
        }

        private void close() {
            try {
                this.socket.close();
            } catch (final IOException ignored) {
            }
        }
    }

    private static String readBoundedLine(final BufferedReader reader) throws IOException {
        final StringBuilder line = new StringBuilder();
        while (true) {
            final int c = reader.read();
            if (c == -1) return line.length() == 0 ? null : line.toString();
            if (c == '\n') return line.toString();
            if (c != '\r') {
                if (line.length() >= MAX_LINE_CHARS) throw new IOException("Request exceeds maximum length");
                line.append((char) c);
            }
        }
    }
}
