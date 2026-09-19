package redacted.client.module.impl.combat;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.render.Render3DUtil;
import redacted.client.util.entity.EntityUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public class BackTrack extends Module {
    // soak up sub-tick jitter so a basically-stationary target doesn't flip hold/release every tick
    private static final double EPS = 0.01;
    // hardcoded combat window: drop the target once we stop swinging at it
    private static final long COMBAT_MS = 1500L;

    private final NumberValue delay = new NumberValue("Delay", this, 100.0, 50.0, 1000.0, 1.0).describe("Ms to lag the target's position back");
    private final BooleanValue render = new BooleanValue("Render", this, true).describe("Show the target's real position box");

    // target packets we're sitting on, replayed in arrival order once each one's hold expires
    private final Queue<HeldPacket> held = new ConcurrentLinkedQueue<>();

    // the player we're actually fighting -- set off our own outbound attack (manual OR aura, both send
    // C02), read on the main thread
    private volatile EntityPlayer combatTarget;
    private volatile long lastCombat;

    // holding/target: written on the main thread (onTick), read on the Netty thread (onPacket).
    private volatile boolean holding;
    private volatile EntityPlayer target;
    // distance to the target when the combat window opened -- the fixed reference we lag against. only
    // touched on the main thread.
    private double startDistance;
    // accumulated on the Netty thread, reseeded on the main thread when the target changes -- atomic
    // so a delta landing mid-reseed can't be lost or piled onto the stale base
    private final AtomicReference<Vec3> realPosition = new AtomicReference<>(new Vec3(0.0, 0.0, 0.0));

    public BackTrack() {
        super("BackTrack", "Lags a fought target's position back as they retreat for extra reach", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onDisable() {
        flush();
        held.clear();
        target = null;
        combatTarget = null;
    }

    @EventTarget
    public void onPacket(final PacketEvent event) {
        if (mc.theWorld == null) return;

        // remember who we just swung at -- that's the only player we backtrack. catches manual clicks
        // and the aura the same way, since both fire a C02 attack.
        if (event.getDirection() == PacketEvent.Direction.OUTBOUND) {
            final Packet<?> out = event.getPacket();
            if (out instanceof C02PacketUseEntity) {
                final C02PacketUseEntity use = (C02PacketUseEntity) out;
                if (use.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    final Entity e = use.getEntityFromWorld(mc.theWorld);
                    if (e instanceof EntityPlayer && EntityUtil.isValidTarget((EntityPlayer) e)) {
                        combatTarget = (EntityPlayer) e;
                        lastCombat = System.currentTimeMillis();
                    }
                }
            }
            return;
        }

        if (event.getDirection() != PacketEvent.Direction.INBOUND) return;

        final EntityPlayer t = this.target;
        if (t == null) return;

        final Packet<?> packet = event.getPacket();

        // track where the server really has the target, even while we lag it, so the rendered box
        // and the hold/release call use the live position
        if (packet instanceof S14PacketEntity) {
            final S14PacketEntity s14 = (S14PacketEntity) packet;
            if (s14.getEntity(mc.theWorld) == t) {
                realPosition.updateAndGet(p -> p.addVector(
                        s14.func_149062_c() / 32.0,
                        s14.func_149061_d() / 32.0,
                        s14.func_149064_e() / 32.0
                ));
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            final S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
            if (s18.getEntityId() == t.getEntityId()) {
                realPosition.set(new Vec3(
                        s18.getX() / 32.0,
                        s18.getY() / 32.0,
                        s18.getZ() / 32.0
                ));
            }
        }

        if (!holding) return;
        if (!isHeldPositionPacket(packet, t)) return;

        // per-packet deadline: the client replays the target exactly Delay ms in the past, smoothly,
        // instead of freezing and snapping. one shared delay keeps arrival order == release order.
        event.setCancelled(true);
        held.add(new HeldPacket(packet, System.currentTimeMillis() + delayMillis()));
    }

    @EventTarget
    public void onTick(final EventTick event) {
        // POST: decide hold/release and replay after this tick's inbound packets have settled, so the
        // next tick's PRE attack raytraces the freshly-applied position. nothing here leaves the client.
        if (event.isPre()) return;

        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            flush();
            target = null;
            return;
        }

        final EntityPlayer t = activeTarget();
        if (t == null) {
            flush();
            target = null;
            return;
        }

        if (t != target) {
            // reseed before publishing so the packet thread can't pile deltas onto a stale base.
            // seed from serverPos* (last applied packet pos), not the interpolated posX. the window just
            // opened, so nothing's held yet -- real == live, which is exactly our start reference.
            flush();
            realPosition.set(appliedPosition(t));
            target = t;
            startDistance = realPosition.get().distanceTo(mc.thePlayer.getPositionVector());
        }

        final double realDistance = realPosition.get().distanceTo(mc.thePlayer.getPositionVector());

        // lag only while they're further out than where the fight started; the moment they're back at
        // or inside that distance, go live. every flush collapses the gap to ~0, so this self-corrects.
        if (realDistance > startDistance + EPS) {
            holding = true;
            drainDue(System.currentTimeMillis());
        } else {
            // also self-heals the rare straggler the netty thread enqueues mid-flush
            flush();
        }
    }

    @EventTarget
    public void onRender3D(final Render3DEvent event) {
        if (!render.getValue()) return;

        final EntityPlayer t = this.target;
        if (t == null || mc.thePlayer == null) return;

        final Vec3 real = realPosition.get();
        final double x = real.xCoord - mc.getRenderManager().viewerPosX;
        final double y = real.yCoord - mc.getRenderManager().viewerPosY;
        final double z = real.zCoord - mc.getRenderManager().viewerPosZ;

        final AxisAlignedBB axis = t.getEntityBoundingBox().expand(0.1, 0.1, 0.1)
                .offset(x - t.posX, y - t.posY, z - t.posZ);

        final float red = t.hurtTime != 0 ? 1.0F : 0.0F;
        final float green = t.hurtTime != 0 ? 0.0F : 1.0F;

        Render3DUtil.start3D();
        GlStateManager.color(red, green, 0.0F, 0.63F);
        Render3DUtil.drawBoundingBox(axis);
        Render3DUtil.end3D();
    }

    // the player we're fighting, while the swing is recent and it's alive
    private EntityPlayer activeTarget() {
        final EntityPlayer ct = combatTarget;
        if (ct == null || ct.isDead || !EntityUtil.isValidTarget(ct)) return null;
        if (System.currentTimeMillis() - lastCombat > COMBAT_MS) return null;
        return ct;
    }

    // last position the client actually applied from a packet (fixed-point), untouched by render lerp
    private static Vec3 appliedPosition(final EntityPlayer t) {
        return new Vec3(t.serverPosX / 32.0, t.serverPosY / 32.0, t.serverPosZ / 32.0);
    }

    // drop everything we're holding right now, in order
    private void flush() {
        holding = false;
        for (HeldPacket hp; (hp = held.poll()) != null; ) {
            replay(hp.packet);
        }
    }

    // release the packets whose hold has expired, oldest first. head-only so lowering Delay at
    // runtime can't reorder the FIFO.
    private void drainDue(final long now) {
        HeldPacket hp;
        while ((hp = held.peek()) != null && now >= hp.releaseAt) {
            held.poll();
            replay(hp.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void replay(final Packet<?> packet) {
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return;
        try {
            ((Packet) packet).processPacket(mc.thePlayer.sendQueue);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    // only freeze packets that carry position -- let look-only/head/velocity through so the body keeps
    // turning naturally and knockback is untouched
    private boolean isHeldPositionPacket(final Packet<?> packet, final EntityPlayer t) {
        if (packet instanceof S14PacketEntity.S15PacketEntityRelMove
                || packet instanceof S14PacketEntity.S17PacketEntityLookMove) {
            return ((S14PacketEntity) packet).getEntity(mc.theWorld) == t;
        }
        if (packet instanceof S18PacketEntityTeleport) {
            return ((S18PacketEntityTeleport) packet).getEntityId() == t.getEntityId();
        }
        return false;
    }

    private static final class HeldPacket {
        private final Packet<?> packet;
        private final long releaseAt;

        private HeldPacket(final Packet<?> packet, final long releaseAt) {
            this.packet = packet;
            this.releaseAt = releaseAt;
        }
    }

    private long delayMillis() {
        return (long) delay.doubleValue();
    }
}
