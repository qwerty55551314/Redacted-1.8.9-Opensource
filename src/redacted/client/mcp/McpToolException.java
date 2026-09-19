package redacted.client.mcp;

/**
 * Tool/protocol-level failure carrying the JSON-RPC error code to answer with.
 * Defaults to -32602 (Invalid params) which is also what unknown tools use;
 * dispatch-level failures like unknown methods pass -32601.
 */
public class McpToolException extends RuntimeException {

    private final int code;

    public McpToolException(String message) {
        this(message, -32602);
    }

    public McpToolException(String message, int code) {
        super(message);
        this.code = code;
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
        this.code = -32603;
    }

    public int getCode() {
        return this.code;
    }
}
