package redacted.client.mcp.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import redacted.client.mcp.MainThreadDispatcher;
import redacted.client.mcp.McpServer;
import redacted.client.mcp.McpTool;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Chat streaming for AI observation - lets the AI see what the player sees,
 * with server time and per-message timestamps, so it can correlate
 * Grim flags (e.g. Simulation x188 .060122 /gl 175) with its own actions.
 */
public final class ChatTools {
    private ChatTools() {}

    public static void registerAll(McpServer server, MainThreadDispatcher d) {
        server.registerTool(new GetChat());
        server.registerTool(new GetCurrentTime());
        server.registerTool(new ClearChat());
    }

    private static final class GetChat implements McpTool {
        public String name() { return "get_chat"; }
        public String description() { return "Get recent chat lines with timestamps and current time. Each line has 'time' (client nanoTime), 'ageMs' (how long ago it was sent), and 'text' (unformatted). Also returns 'now' (current nanoTime) and 'currentTime' (System.currentTimeMillis). Use this to correlate Grim flags with scaffold actions."; }
        public JsonObject inputSchema() {
            JsonObject props = new JsonObject();
            JsonObject limit = new JsonObject(); limit.addProperty("type","integer"); limit.addProperty("description","max lines to return, default 50"); limit.addProperty("default",50);
            props.add("limit", limit);
            JsonObject since = new JsonObject(); since.addProperty("type","integer"); since.addProperty("description","only return messages since this nanoTime (from previous get_chat.now)");
            props.add("sinceNano", since);
            return ModuleTools.schema(props, null);
        }
        public Object execute(JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                int limit = args.has("limit") ? args.get("limit").getAsInt() : 50;
                long sinceNano = args.has("sinceNano") ? args.get("sinceNano").getAsLong() : 0L;
                long nowNano = System.nanoTime();
                long nowMs = System.currentTimeMillis();
                Minecraft mc = Minecraft.getMinecraft();
                JsonObject out = new JsonObject();
                out.addProperty("nowNano", nowNano);
                out.addProperty("nowMs", nowMs);
                out.addProperty("currentTime", nowMs);
                JsonArray lines = new JsonArray();
                if (mc != null && mc.ingameGUI != null) {
                    GuiNewChat chat = mc.ingameGUI.getChatGUI();
                    try {
                        // GuiNewChat has field_146253_i / chatLines / drawnChatLines - use reflection to be version-agnostic
                        Field f = null;
                        for (Field field : GuiNewChat.class.getDeclaredFields()) {
                            if (List.class.isAssignableFrom(field.getType())) {
                                field.setAccessible(true);
                                Object v = field.get(chat);
                                if (v instanceof List) {
                                    List<?> list = (List<?>) v;
                                    if (!list.isEmpty() && list.get(0) instanceof IChatComponent) {
                                        f = field;
                                        break;
                                    }
                                    // Also try ChatLine wrapper
                                    if (!list.isEmpty()) {
                                        Object first = list.get(0);
                                        try {
                                            Field compField = first.getClass().getDeclaredField("chatComponent");
                                            compField.setAccessible(true);
                                            if (compField.get(first) instanceof IChatComponent) {
                                                f = field;
                                                break;
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                        List<?> chatLines = null;
                        if (f != null) {
                            @SuppressWarnings("unchecked")
                            List<?> l = (List<?>) f.get(chat);
                            chatLines = l;
                        }
                        if (chatLines != null) {
                            int count = 0;
                            // Walk newest to oldest, but return oldest-first for streaming
                            for (int i = chatLines.size() - 1; i >= 0 && count < limit; i--) {
                                Object line = chatLines.get(i);
                                IChatComponent comp = null;
                                long updatedCounter = 0;
                                int chatLineId = 0;
                                if (line instanceof IChatComponent) {
                                    comp = (IChatComponent) line;
                                } else {
                                    try {
                                        Field cf = line.getClass().getDeclaredField("chatComponent");
                                        cf.setAccessible(true);
                                        comp = (IChatComponent) cf.get(line);
                                        try {
                                            Field uf = line.getClass().getDeclaredField("updateCounter");
                                            uf.setAccessible(true);
                                            updatedCounter = uf.getInt(line);
                                        } catch (Throwable ignored) {}
                                        try {
                                            Field idf = line.getClass().getDeclaredField("chatLineId");
                                            idf.setAccessible(true);
                                            chatLineId = idf.getInt(line);
                                        } catch (Throwable ignored) {}
                                    } catch (Throwable ignored) { continue; }
                                }
                                if (comp == null) continue;
                                String text = comp.getUnformattedText();
                                // Filter to last N seconds if sinceNano provided - we don't have per-line nano, so use updateCounter ticks
                                JsonObject o = new JsonObject();
                                o.addProperty("text", text);
                                o.addProperty("formatted", comp.getFormattedText());
                                o.addProperty("updateCounter", updatedCounter);
                                o.addProperty("chatLineId", chatLineId);
                                // Age estimate: updateCounter is tick count when line was added
                                if (mc.theWorld != null) {
                                    long ageTicks = mc.theWorld.getTotalWorldTime() - updatedCounter;
                                    o.addProperty("ageTicks", ageTicks);
                                    o.addProperty("ageMs", ageTicks * 50);
                                }
                                lines.add(o);
                                count++;
                            }
                        }
                    } catch (Throwable t) {
                        JsonObject err = new JsonObject();
                        err.addProperty("error", t.getMessage());
                        lines.add(err);
                    }
                }
                out.add("lines", lines);
                out.addProperty("count", lines.size());
                return out;
            });
        }
    }

    private static final class GetCurrentTime implements McpTool {
        public String name() { return "get_current_time"; }
        public String description() { return "Get current client time (nanoTime and currentTimeMillis) for correlating with chat timestamps and Grim flags."; }
        public JsonObject inputSchema() { return ModuleTools.schema(new JsonObject(), null); }
        public Object execute(JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                JsonObject out = new JsonObject();
                out.addProperty("nanoTime", System.nanoTime());
                out.addProperty("currentTimeMillis", System.currentTimeMillis());
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.theWorld != null) {
                    out.addProperty("worldTime", mc.theWorld.getTotalWorldTime());
                    out.addProperty("tickCount", mc.theWorld.getTotalWorldTime());
                }
                return out;
            });
        }
    }

    private static final class ClearChat implements McpTool {
        public String name() { return "clear_chat"; }
        public String description() { return "Clear chat (useful before a test to get clean Grim flag stream)."; }
        public JsonObject inputSchema() { return ModuleTools.schema(new JsonObject(), null); }
        public Object execute(JsonObject args) throws Exception {
            return MainThreadDispatcher.dispatch(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.ingameGUI != null) {
                    mc.ingameGUI.getChatGUI().clearChatMessages();
                }
                JsonObject out = new JsonObject();
                out.addProperty("cleared", true);
                out.addProperty("nowNano", System.nanoTime());
                return out;
            });
        }
    }
}
