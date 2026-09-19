package redacted.client.util.misc;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

import java.util.Arrays;

public final class PacketUtils {
    private PacketUtils() {
    }

    public static void sendPacket(final Packet<?> packet) {
        final NetHandlerPlayClient netHandler = Minecraft.getMinecraft().getNetHandler();
        if (netHandler == null) return;
        final NetworkManager networkManager = netHandler.getNetworkManager();
        if (networkManager == null) return;
        networkManager.sendPacket(packet);
    }

    public static void sendSilentPacket(final Packet<?> packet) {
        final NetHandlerPlayClient netHandler = Minecraft.getMinecraft().getNetHandler();
        if (netHandler == null) return;
        final NetworkManager networkManager = netHandler.getNetworkManager();
        if (networkManager == null) return;
        networkManager.sendSilentPacket(packet);
    }

    public static void receivePacket(final Packet<?> packet) {
        final NetHandlerPlayClient netHandler = Minecraft.getMinecraft().getNetHandler();
        if (netHandler == null) return;
        final NetworkManager networkManager = netHandler.getNetworkManager();
        if (networkManager == null) return;
        networkManager.receivePacket(packet);
    }

    public static void receiveSilentPacket(final Packet<?> packet) {
        final NetHandlerPlayClient netHandler = Minecraft.getMinecraft().getNetHandler();
        if (netHandler == null) return;
        final NetworkManager networkManager = netHandler.getNetworkManager();
        if (networkManager == null) return;
        networkManager.receiveUnregisteredPacket(packet);
    }

    public static boolean isClientPacket(final Packet<?> packet) {
        return packet != null && Arrays.stream(PacketList.serverbound).anyMatch(clazz -> clazz == packet.getClass());
    }

    // the whole serverbound play stream -- the only AC-safe blink/choke predicate. holding a subset
    // (just C03, or letting C02/transactions sail through) is what trips Grim PacketOrder, so every
    // whole-stream holder (FakeLag's delay, AutoBlock's micro-blink) gates on this single test.
    public static boolean isPlayStreamPacket(final Packet<?> packet) {
        return packet != null && packet.getClass().getName().startsWith("net.minecraft.network.play.client.");
    }

    public static void queue(final Packet<?> packet) {
        if (packet == null) return;

        if (isClientPacket(packet)) {
            sendSilentPacket(packet);
        } else {
            receiveSilentPacket(packet);
        }
    }

    public static class TimedPacket {
        private final Packet<?> packet;
        private final long time;

        public TimedPacket(final Packet<?> packet, final long time) {
            this.packet = packet;
            this.time = time;
        }

        public TimedPacket(final Packet<?> packet) {
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        public Packet<?> getPacket() {
            return packet;
        }

        public long getTime() {
            return time;
        }
    }
}
