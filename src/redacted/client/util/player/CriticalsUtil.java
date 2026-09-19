package redacted.client.util.player;

import net.minecraft.client.Minecraft;

public class CriticalsUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // is the player in a state where a jump would produce a crit?
    public static boolean canCrit() {
        return mc.thePlayer != null
            && mc.thePlayer.onGround
            && !mc.thePlayer.isOnLadder()
            && !mc.thePlayer.isInWater()
            && !mc.thePlayer.isRiding()
            && !mc.gameSettings.keyBindJump.isKeyDown();
    }
}
