package redacted.client.command.impl;

import redacted.client.command.Argument;
import redacted.client.command.Command;
import redacted.client.mcp.McpManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Control the MCP (Model Context Protocol) bridge server.
 * <ul>
 *   <li>{@code .mcp} / {@code .mcp status} - show whether it is running and on which port</li>
 *   <li>{@code .mcp on} - start on the default port</li>
 *   <li>{@code .mcp on --port 9001} - start on a specific port</li>
 *   <li>{@code .mcp off} - stop it</li>
 * </ul>
 */
public class McpCommand extends Command {

    public McpCommand() {
        super("mcp", "Start/stop the MCP bridge server",
                Argument.literal("action", "what to do with the server",
                        new Argument.Option("on", "start the server (default port)"),
                        new Argument.Option("off", "stop the server"),
                        new Argument.Option("status", "show whether it is running and on which port"),
                        new Argument.Option("tools", "list the registered MCP tools")),
                Argument.optionalText("port", "--port N to pick a port instead of the default",
                        () -> Collections.singletonList("--port")));
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus();
            return;
        }

        final String action = args[0].toLowerCase();
        switch (action) {
            case "on":
            case "start": {
                int port = McpManager.DEFAULT_PORT;
                for (int i = 1; i < args.length - 1; i++) {
                    if ("--port".equalsIgnoreCase(args[i])) {
                        try {
                            port = Integer.parseInt(args[i + 1]);
                        } catch (final NumberFormatException e) {
                            sendMessage("§cInvalid port: §e" + args[i + 1]);
                            return;
                        }
                        if (port < 1 || port > 65535) {
                            sendMessage("§cPort must be between 1 and 65535.");
                            return;
                        }
                    }
                }

                final McpManager mcp = McpManager.getInstance();
                if (mcp.isRunning()) {
                    sendMessage("MCP is already running on port §e" + mcp.getPort()
                            + "§7. Use §e.mcp off§7 first.");
                    return;
                }

                final int bound = mcp.start(port);
                if (bound != -1 && bound != port) {
                    sendMessage("Bound to port §e" + bound + "§7 instead.");
                }
                return;
            }

            case "off":
            case "stop":
                McpManager.getInstance().stop();
                return;

            case "tools": {
                final List<String> names = new ArrayList<String>();
                if (McpManager.getInstance().getTools() != null) {
                    for (final String name : McpManager.getInstance().getTools().keySet()) {
                        names.add(name);
                    }
                }
                if (names.isEmpty()) {
                    sendMessage("No tools registered (server not running).");
                } else {
                    sendMessage("§aTools §7(§e" + names.size() + "§7)§a: §e" + String.join("§7, §e", names));
                }
                return;
            }

            default:
                sendMessage("§cUnknown action §e" + args[0] + "§c. Usage: §r" + getUsage());
        }
    }

    private void sendStatus() {
        final McpManager mcp = McpManager.getInstance();
        if (!mcp.isRunning()) {
            sendMessage("MCP server: §coff§7. Start it with §e.mcp on§7 (default port §e"
                    + McpManager.DEFAULT_PORT + "§7).");
            return;
        }
        sendMessage("MCP server: §arunning §7on §e127.0.0.1:" + mcp.getPort()
                + " §7(" + mcp.getConnectionCount() + " connection"
                + (mcp.getConnectionCount() == 1 ? "" : "s") + ")");
    }
}
