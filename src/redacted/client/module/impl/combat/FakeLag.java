package redacted.client.module.impl.combat;

import net.minecraft.network.Packet;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.misc.PacketUtils;
import redacted.client.value.impl.NumberValue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FakeLag extends Module {
    private final NumberValue delay = new NumberValue("Delay", this, 300, 50, 2000, 5).describe("Ms to hold your outgoing packets");

    private final ConcurrentLinkedQueue<TimedPacket> queue = new ConcurrentLinkedQueue<>();
    private final Set<Packet<?>> bypass = ConcurrentHashMap.newKeySet();

    public FakeLag() {
        super("Fake Lag", "Delays your outgoing packets to raise your ping", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        queue.clear();
        bypass.clear();
    }

    @Override
    public void onDisable() {
        flushAll();
        queue.clear();
        bypass.clear();
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            setEnabled(false);
        }
    }

    // -100 runs us below the default-0 packet droppers (Freecam) so their cancels break the
    // dispatch loop before we'd hold one of their packets, and still well above AutoBlock's -2000.
    // (ClientSpoof no longer drops packets here -- its Cancel mode is handled at the join-game
    // send site in NetHandlerPlayClient, outside the event pipeline.)
    @EventTarget(priority = -100)
    public void onPacket(final PacketEvent event) {
        if (event.isCancelled()) return; // a dropper already claimed this packet -- don't hold+replay it
        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return;

        final Packet<?> packet = event.getPacket();

        // Replayed packets are let through untouched so they actually reach the server.
        if (bypass.remove(packet)) return;

        if (!shouldDelay(packet)) return;

        event.setCancelled(true);
        // pin the release deadline once at enqueue so release is just a cheap head check.
        final long holdUntil = System.currentTimeMillis() + (long) delay.doubleValue();
        queue.add(new TimedPacket(packet, holdUntil));
    }

    @EventTarget
    public void onMotion(final MotionEvent event) {
        if (event.isPre()) return;
        release();
    }

    private void release() {
        if (queue.isEmpty()) return;
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            queue.clear();
            bypass.clear();
            return;
        }

        final long now = System.currentTimeMillis();
        // pop strictly from the head so the FIFO never reorders -- matters if Delay is lowered at runtime,
        // which can hand a fresh packet a smaller holdUntil than one already waiting ahead of it.
        TimedPacket tp;
        while ((tp = queue.peek()) != null && now >= tp.holdUntil) {
            queue.poll();
            send(tp.packet);
        }
    }

    private void flushAll() {
        if (queue.isEmpty()) return;
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            queue.clear();
            return;
        }
        TimedPacket tp;
        while ((tp = queue.poll()) != null) {
            send(tp.packet);
        }
    }

    private void send(final Packet<?> packet) {
        bypass.add(packet);
        mc.thePlayer.sendQueue.addToSendQueue(packet);
    }

    private boolean shouldDelay(final Packet<?> packet) {
        // hold the WHOLE play stream, keepalive + transaction included. real lag is one pipe with one
        // latency; firing the clock instantly while movement sits stale leaves our measured ping low
        // against a stale position -- the exact mismatch an AC reads (Grim derives lag-comp from
        // transaction RTT, and its Post check flags actions that land after a held flying packet).
        // holding everything keeps the FIFO order intact, so attacks land and transaction ids stay in sequence.
        return PacketUtils.isPlayStreamPacket(packet);
    }

    private static final class TimedPacket {
        private final Packet<?> packet;
        private final long holdUntil;

        private TimedPacket(final Packet<?> packet, final long holdUntil) {
            this.packet = packet;
            this.holdUntil = holdUntil;
        }
    }
}
