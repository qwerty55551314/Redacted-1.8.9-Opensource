package redacted.client.mcp;

import com.google.gson.JsonObject;

/**
 * One callable MCP tool. Tools are registered into {@link McpServer} at start; the
 * registry is deliberately open so whole new tool families (baritone pathing, world
 * queries, ...) can be added later without touching the protocol layer.
 */
public interface McpTool {

    /** Tool name exposed over MCP (snake_case, e.g. "list_modules"). */
    String name();

    /** Human-readable description shown to the model. */
    String description();

    /** JSON Schema object describing the arguments. */
    JsonObject inputSchema();

    /**
     * Runs the tool. Implementations that touch live game state must wrap that work in
     * {@link MainThreadDispatcher#call(java.util.concurrent.Callable)} -- the MCP socket
     * threads must never mutate modules/values directly.
     *
     * @return any JSON-serializable result (Map, List, primitive, Gson JsonElement)
     */
    Object execute(JsonObject args) throws Exception;
}
