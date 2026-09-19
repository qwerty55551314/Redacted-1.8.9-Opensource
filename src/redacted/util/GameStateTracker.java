package redacted.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.module.Module;
import redacted.client.module.impl.combat.SilentAura;
import redacted.client.module.impl.world.Scaffold;

// always-on, like CombatManager/RotationUtil -- registered once in Client.start(), runs regardless of
// any module's state. sends no packets and never rotates. one job: on a game transition (respawn/join,
// a teleport-sized position jump, or a big absolute S08) force SilentAura and Scaffold off so they stop
// acting on the next world -- a "we changed games" event no single module can own.
public final class GameStateTracker {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final GameStateTracker INSTANCE = new GameStateTracker();

    private static final double TELEPORT_DIST_SQ = 40.0D * 40.0D;

    // an ABSOLUTE S08 this far off is a region/game teleport that carries no S07/S01 (same-server
    // game -> lobby hops ride plain S08s). relative-flagged axes are offsets, not a move -- ignored.
    private static final double S08_TELEPORT_DIST_SQ = 32.0D * 32.0D;

    // S07/S01 arrive on the netty thread -- the packet hook can't safely touch the world or modules, so
    // it just trips this and the main-thread tick consumes it.
    private volatile boolean transitionPending;

    // the single "a game transition happened this tick" signal -- set every onTick, read by other
    // always-on trackers (CombatManager re-arms its own-bed capture off this) so teleport detection
    // lives in exactly one place. valid for the tick because onTick runs at 20000, before them.
    private boolean transitionThisTick;

    private double lastPosX, lastPosY, lastPosZ;
    private boolean hasLastPos;        // false until the first sample

    private GameStateTracker() {
    }

    public static GameStateTracker getInstance() {
        return INSTANCE;
    }

    // true on the tick a game transition was detected (respawn/join packet or a teleport-sized jump).
    // read by CombatManager at priority 10000, after this tracker's onTick at 20000 has set it.
    public boolean transitionThisTick() {
        return transitionThisTick;
    }

    @EventTarget
    public void onPacket(final PacketEvent e) {
        if (e.getDirection() != PacketEvent.Direction.INBOUND) return;
        final Packet<?> packet = e.getPacket();
        // netty thread -- flag only, no disable()/world reads here. the tick handler does the work.
        if (packet instanceof S07PacketRespawn || packet instanceof S01PacketJoinGame) {
            transitionPending = true;
            return;
        }
        // big absolute S08: a region/game teleport with no respawn marker. single source of truth for
        // teleport detection -- modules must NOT keep their own S08 scanners. the player-pos reads are
        // racy off-netty, but this only sizes a boolean flag; worst case the main thread compares
        // against a tick-old position.
        if (packet instanceof S08PacketPlayerPosLook && mc.thePlayer != null) {
            final S08PacketPlayerPosLook p = (S08PacketPlayerPosLook) packet;
            if (p.func_179834_f().isEmpty()) {
                final double dx = p.getX() - mc.thePlayer.posX;
                final double dy = p.getY() - mc.thePlayer.posY;
                final double dz = p.getZ() - mc.thePlayer.posZ;
                if (dx * dx + dy * dy + dz * dz >= S08_TELEPORT_DIST_SQ) transitionPending = true;
            }
        }
    }

    // high priority so the disable lands before the combat handlers run this tick (else SilentAura could
    // get one swing in on the detection tick). PRE only -- positions are frozen across the PRE window.
    @EventTarget(priority = 20000)
    public void onTick(final EventTick e) {
        if (!e.isPre()) return;
        transitionThisTick = false;   // clear first so a stale true can't leak past an early return
        if (mc.thePlayer == null || mc.theWorld == null) return;

        final double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;

        final boolean respawn = transitionPending;
        transitionPending = false;

        // a 40+ block single-tick jump is a spawn teleport that carries no S07/S01 (same-server game->game)
        boolean jump = false;
        if (hasLastPos) {
            final double dx = px - lastPosX, dy = py - lastPosY, dz = pz - lastPosZ;
            jump = dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ;
        }

        transitionThisTick = respawn || jump;

        // a game transition parks the two acting modules so they stop acting on the next world.
        if (transitionThisTick) {
            disableModule(SilentAura.class);
            disableModule(Scaffold.class);
        }

        // sample every tick (GUI open included) -- jumps are measured against this, and S07 + the spawn
        // S08 can land in the same tick.
        lastPosX = px;
        lastPosY = py;
        lastPosZ = pz;
        hasLastPos = true;
    }

    private void disableModule(final Class<? extends Module> type) {
        final Module m = Client.getInstance().getModuleManager().getModule(type);
        if (m != null) m.disable();   // no-op if already disabled
    }
}
