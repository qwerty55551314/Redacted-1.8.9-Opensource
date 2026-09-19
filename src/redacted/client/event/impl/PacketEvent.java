package redacted.client.event.impl;

import redacted.client.event.CancellableEvent;
import net.minecraft.network.Packet;

public class PacketEvent extends CancellableEvent {
    private final Packet packet;
    private final Direction direction;

    public PacketEvent(final Packet packet, final Direction direction) {
        this.packet = packet;
        this.direction = direction;
    }

    public Packet getPacket() {
        return this.packet;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public enum Direction {
        INBOUND,
        OUTBOUND
    }
}
