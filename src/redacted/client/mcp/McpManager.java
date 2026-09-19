package redacted.client.mcp;

import redacted.client.Client;
import redacted.client.event.EventManager;
import redacted.client.mcp.impl.BaritoneTools;
import redacted.client.mcp.impl.BridgeTools;
import redacted.client.mcp.impl.ChatTools;
import redacted.client.mcp.impl.ConfigTools;
import redacted.client.mcp.impl.ContainerTools;
import redacted.client.mcp.impl.DebugTools;
// import redacted.client.mcp.impl.HumanTools;
import redacted.client.mcp.impl.InfoTools;
import redacted.client.mcp.impl.ModuleTools;
import redacted.client.mcp.impl.PlayerTools;
import redacted.client.mcp.impl.RenderTools;
import redacted.client.mcp.impl.WorldTools;
import redacted.client.util.ChatUtil;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.net.BindException;
import java.util.Map;

/**
 * Owns the optional MCP server lifecycle. The server only ever exists because the user
 * ran {@code .mcp on} -- nothing starts it automatically, and it is fully torn down on
 * client shutdown.
 */
public final class McpManager {

    public static final int DEFAULT_PORT = 25585;

    private static final McpManager INSTANCE = new McpManager();

    private MainThreadDispatcher dispatcher;
    private McpServer server;

    private McpManager() {
    }

    public static McpManager getInstance() {
        return INSTANCE;
    }

    /** @return the port the server actually bound, or -1 on failure (reason is chatted). */
    public synchronized int start(int port) {
        if (this.server != null && this.server.isRunning()) {
            return this.server.getPort();
        }

        final EventManager events = Client.getInstance().getEventManager();
        if (this.dispatcher == null) {
            this.dispatcher = new MainThreadDispatcher();
            events.register(this.dispatcher);
        }
        MainThreadDispatcher.setActive(this.dispatcher);

        final McpServer newServer = new McpServer(port);
        ModuleTools.registerAll(newServer, this.dispatcher);
        ConfigTools.registerAll(newServer, this.dispatcher);
        InfoTools.registerAll(newServer, this.dispatcher);
        BaritoneTools.registerAll(newServer, this.dispatcher);
        BridgeTools.registerAll(newServer, this.dispatcher);
        WorldTools.registerAll(newServer, this.dispatcher);
        PlayerTools.registerAll(newServer, this.dispatcher);
        ContainerTools.registerAll(newServer, this.dispatcher);
        DebugTools.registerAll(newServer, this.dispatcher);
        RenderTools.registerAll(newServer, this.dispatcher);
        // HumanTools.registerAll(newServer, this.dispatcher); // removed: depends on deleted HumanMovementLogger
        ChatTools.registerAll(newServer, this.dispatcher);

        try {
            newServer.start();
        } catch (final BindException e) {
            chat("§cMCP failed to start: port §e" + port + "§c is already in use.");
            return -1;
        } catch (final Exception e) {
            chat("§cMCP failed to start: §7" + e.getMessage());
            return -1;
        }

        this.server = newServer;
        chat("§aMCP server started §7- §f127.0.0.1:§e" + newServer.getPort()
                + " §7(" + newServer.getTools().size() + " tools, JSON-RPC/MCP over TCP)");

        final String endpoint = "tcp://127.0.0.1:" + newServer.getPort();
        final IChatComponent link = new ChatComponentText(endpoint);
        link.setChatStyle(new ChatStyle()
                .setColor(EnumChatFormatting.YELLOW)
                .setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, endpoint))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("§fClick to copy §e" + endpoint))));
        final IChatComponent line = new ChatComponentText("§7Connect an MCP client to §e");
        line.appendSibling(link);
        line.appendSibling(new ChatComponentText("§7. Use §e.mcp off§7 to stop."));
        chatComponent(line);
        return newServer.getPort();
    }

    public synchronized void stop() {
        if (this.server == null) {
            chat("MCP server is not running.");
            return;
        }

        this.server.stop();
        this.server = null;

        MainThreadDispatcher.setActive(null);
        if (this.dispatcher != null) {
            Client.getInstance().getEventManager().unregister(this.dispatcher);
            this.dispatcher = null;
        }
        chat("§aMCP server stopped.");
    }

    /** Silent variant used during client shutdown (chat may already be going away). */
    public synchronized void shutdownQuietly() {
        if (this.server != null) {
            this.server.stop();
            this.server = null;
        }
        MainThreadDispatcher.setActive(null);
        if (this.dispatcher != null) {
            try {
                Client.getInstance().getEventManager().unregister(this.dispatcher);
            } catch (final Exception ignored) {
            }
            this.dispatcher = null;
        }
    }

    public synchronized boolean isRunning() {
        return this.server != null && this.server.isRunning();
    }

    public synchronized int getPort() {
        return this.server == null ? -1 : this.server.getPort();
    }

    public synchronized int getConnectionCount() {
        return this.server == null ? 0 : this.server.getConnectionCount();
    }

    public synchronized Map<String, McpTool> getTools() {
        return this.server == null ? null : this.server.getTools();
    }

    private static void chat(final String message) {
        ChatUtil.say(message);
    }

    /** ChatUtil.say for a pre-built component (keeps the [Redacted] prefix, preserves click/hover styles). */
    private static void chatComponent(final IChatComponent component) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§7[§8" + Client.NAME + "§7] §f").appendSibling(component));
        }
    }
}
