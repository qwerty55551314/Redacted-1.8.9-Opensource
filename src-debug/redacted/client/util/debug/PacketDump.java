package redacted.client.util.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dev-only packet dumper loaded via reflection from DebugUtil.
 * Only on the debugRunClient classpath (src-debug), never in the normal jar.
 * Logs yaw/pitch/onGround/ping/nanoTime per packet.
 */
public final class PacketDump {
    private static BufferedWriter writer;
    private static File logDir;

    private PacketDump() {}

    public static void init() {
        try {
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "logs/packets");
            if (!dir.exists()) dir.mkdirs();
            logDir = dir;
            File out = new File(dir, "packets-" + System.currentTimeMillis() + ".csv");
            writer = new BufferedWriter(new FileWriter(out, StandardCharsets.UTF_8));
            writer.write("nanoTime,direction,packet,yaw,pitch,onGround,posX,posY,posZ,ping"); writer.newLine();
            writer.flush();
            DebugSystem.log(DebugSystem.MOVEMENT, "PacketDump init -> " + out.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void log(String direction, Packet packet) {
        // Detailed movement CSV + DebugSystem
        try {
            long nano = System.nanoTime();
            Minecraft mc = Minecraft.getMinecraft();
            float yaw = 0, pitch = 0; boolean onGround = false;
            double px = 0, py = 0, pz = 0;
            int ping = 0;
            if (mc != null && mc.thePlayer != null) {
                yaw = mc.thePlayer.rotationYaw; pitch = mc.thePlayer.rotationPitch;
                px = mc.thePlayer.posX; py = mc.thePlayer.posY; pz = mc.thePlayer.posZ;
                try { ping = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getGameProfile().getId()).getResponseTime(); } catch (Throwable ignored) {}
            }
            if (packet instanceof C03PacketPlayer) {
                final C03PacketPlayer playerPacket = (C03PacketPlayer) packet;
                try { onGround = playerPacket.isOnGround(); } catch (Throwable ignored) {}
                if (playerPacket.getRotating()) {
                    try { yaw = playerPacket.getYaw(); pitch = playerPacket.getPitch(); } catch (Throwable ignored) {}
                }
            }
            String line = nano + "," + direction + "," + packet.getClass().getSimpleName() + "," + yaw + "," + pitch + "," + onGround + "," + px + "," + py + "," + pz + "," + ping;
            if (writer != null) { writer.write(line); writer.newLine(); writer.flush(); }
            DebugSystem.log(DebugSystem.MOVEMENT, line);
            // Also log to packet category if needed (kept for backward compat)
            if (packet instanceof C02PacketUseEntity || packet instanceof C03PacketPlayer || packet instanceof C07PacketPlayerDigging || packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C0APacketAnimation) {
                DebugSystem.log(DebugSystem.MOVEMENT, direction + " " + packet.getClass().getSimpleName() + " yaw=" + yaw + " pitch=" + pitch + " onGround=" + onGround + " ping=" + ping);
            }
        } catch (Throwable t) {
            try { DebugSystem.log(DebugSystem.MOVEMENT, direction + " " + packet.getClass().getSimpleName() + " err=" + t.getMessage()); } catch (Throwable ignored) {}
        }
    }
}
