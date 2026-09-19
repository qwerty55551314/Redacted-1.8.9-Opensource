package redacted.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import java.util.List;
import java.util.Locale;

// shared game-end parsing + the are-we-even-on-hypixel check
public class HypixelUtil {

    public static boolean isOnHypixel() {
        final ServerData server = Minecraft.getMinecraft().getCurrentServerData();
        if (server == null || server.serverIP == null) return false;
        return server.serverIP.toLowerCase(Locale.ROOT).contains("hypixel");
    }

    // the VICTORY / DEFEAT / GAME OVER titles a match ends on
    public static boolean isGameEnd(String title) {
        if (title == null) return false;
        String upper = title.toUpperCase();
        return upper.contains("VICTORY") || upper.contains("DEFEAT")
                || upper.contains("GAME OVER") || upper.contains("WON") || upper.contains("LOST");
    }

    // chat lines that signal a match ended -- pass an already-lowercased line
    public static boolean isGameEndChat(String lower) {
        if (lower == null) return false;
        return lower.contains("you won!") || lower.contains("you win!")
                || lower.contains("you lose!") || lower.contains("you lost!")
                || lower.contains("the game has ended") || lower.contains("game over")
                || lower.contains("has won the game") || lower.contains("won the match")
                || lower.contains("died! want to play again");
    }

    // pull the clickable /play (or /join) out of an end-screen chat line
    public static void collectPlayCommands(IChatComponent component, List<String> out) {
        if (component == null) return;
        List<IChatComponent> siblings = component.getSiblings();
        if (siblings == null) return;
        for (IChatComponent sibling : siblings) {
            ChatStyle style = sibling.getChatStyle();
            if (style != null && style.getChatClickEvent() != null) {
                String value = style.getChatClickEvent().getValue();
                if ("RUN_COMMAND".equals(style.getChatClickEvent().getAction().name())
                        && value != null && (value.contains("/play") || value.contains("/join"))
                        && !out.contains(value)) {
                    out.add(value);
                }
            }
            collectPlayCommands(sibling, out);
        }
    }
}
