package redacted.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import redacted.client.Client;

// client-side chat printing only -- never sends anything to the server.
public class ChatUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void say(final String message) {
        print("§7[§8" + Client.NAME + "§7] §f" + message);
    }

    public static void print(final String message) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(message));
    }
}
