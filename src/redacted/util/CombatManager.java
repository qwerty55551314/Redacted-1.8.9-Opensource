package redacted.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.util.entity.EntityUtil;
import redacted.client.util.world.BedTargetingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-tick combat cache. The valid-target scan over loadedEntityList and the attack-ray LOS check
 * are otherwise re-run a dozen-odd times a tick (SilentAura's findTargetData alone ~6x, plus
 * AutoBlock's oracle, plus SilentAim) on identical inputs. We compute the observer-independent
 * parts ONCE per tick here and hand them out.
 *
 * Only positional facts get cached: the entity, its eye-to-hitbox reachSq, and the aim-at-target
 * rotation. Range, FOV, the type filter and the FOV reference yaw differ per module and stay live
 * in each caller -- never cache those here (a cached selection would desync the swing when a
 * caller's reference yaw shifts mid-tick).
 *
 * PRE-cadence only. The tick epoch advances on EventTick.PRE; player + entity positions are frozen
 * across the whole PRE window (the world ticks afterwards, see Minecraft.runTick), so a snapshot
 * taken at high-priority PRE equals what every later PRE listener would read. Do NOT call
 * candidates()/attackRayHits() from EventFrame / EventMoveInput / POST -- a frame caller can't tell
 * the snapshot is a tick stale.
 */
public final class CombatManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final CombatManager INSTANCE = new CombatManager();

    private int tick;
    private int builtTick = -1;
    private final List<Candidate> candidates = new ArrayList<Candidate>();
    private final List<Candidate> candidatesView = Collections.unmodifiableList(candidates);

    // hand-coordination flags, stamped with the current tick by whatever hand-action module owns the
    // hand this tick (AutoHeal). the aura reads them so the two never reference each other directly.
    // wireHeld  = a rotation is parked on the wire (pot throw) -> aura must NOT release/wipe it.
    // healPaused = a heal is in progress (soup) -> aura releases its aim and skips the swing.
    // both self-expire: a read only counts if it matches the live tick, so a stale stamp reads false.
    private int wireHeldTick = -1;
    private int healPausedTick = -1;

    // attack-ray memo, valid for one tick. keyed on (entityId, maxRange): the yaw/pitch are always
    // the server-reported rotation (baked in by attackRayHits, never a flicked angle) and those are
    // frozen across the PRE window, so they can't vary between two calls in the same tick.
    private int rayTick = -1;
    private final Map<Long, Boolean> rayCache = new HashMap<Long, Boolean>();

    // own-bed tracking. at spawn the nearest bed is ours, so we snapshot it and BedAura skips it. runs
    // every tick regardless of BedAura's state so the foot is fresh whenever it toggles on. beds don't
    // move within a game, so we lock our bed ONCE per world and never re-acquire a different one mid-game
    // only a new world (== new game) re-opens the scan. teleport detection is GameStateTracker's job
    // (single source of truth); we read its transitionThisTick() at priority 20000, before our 10000.
    private static final int OWN_BED_SCAN_RANGE = 12;     // you spawn a few blocks from your bed
    private static final int SETTLE_TICKS = 5;            // after terrain loads, re-take the nearest a few more
                                                          // ticks so a closer bed streaming in late wins the lock
    private static final int SCAN_EVERY = 4;              // throttle the 25^3 scan -- the window can stay open a
                                                          // whole pre-game lobby now, so don't full-scan every tick
    private BlockPos ownBedFoot;
    private boolean capturing;          // window open: keep re-taking the nearest bed until a bed locks
    private int settleTicks;
    private int captureAge;             // ticks since the window opened; also throttles the scan
    private boolean bedLockedThisGame;  // locked our real bed this world -> don't re-scan on a respawn
    private WorldClient lastWorld;      // new world == new game: the one place a locked bed is forgotten

    private CombatManager() {
    }

    public static CombatManager getInstance() {
        return INSTANCE;
    }

    // advance the epoch and drop last tick's snapshot before any combat module runs. priority is
    // above AutoBlock (200) / AntiBot (300) so the bump lands first; the actual list build stays
    // lazy (first candidates() call), which is post-AntiBot and so matches SilentAura's own scan.
    @EventTarget(priority = 10000)
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        tick++;
        builtTick = -1;
        candidates.clear();
        rayTick = -1;
        rayCache.clear();
        trackOwnBed();
    }

    // beds don't move within a game, so we capture our bed ONCE per world and then trust that position.
    // a new world (== new game) is the only thing that forgets it and re-opens the scan. while the window
    // is open we re-take the nearest bed each scan (so a closer own bed that streams in late beats an enemy
    // bed that loaded first) and only lock once terrain has finished loading and the pick has held for
    // SETTLE_TICKS -- the window survives the whole pre-game lobby to catch the game-start teleport onto our
    // island (which may be <40 blocks and carry no packet). once locked we NEVER re-scan: a death-respawn is
    // the same world, and re-scanning there would lock the nearest enemy bed as ours once our own is gone.
    // the locked foot is re-validated each tick and dropped if our bed gets broken -- and stays dropped, so
    // BedAura breaks everyone for the rest of the game.
    private void trackOwnBed() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            ownBedFoot = null;          // no world -> no known bed; drop the stale foot from the last game
            capturing = false;
            bedLockedThisGame = false;  // next world is a fresh game
            lastWorld = null;
            return;
        }

        // new world == new game. the ONE place a locked bed is forgotten: beds don't move, so within a single
        // world there's never a new own bed to find. a death-respawn keeps the same world -> skips this.
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            ownBedFoot = null;
            bedLockedThisGame = false;
            capturing = true;
            settleTicks = SETTLE_TICKS;
            captureAge = 0;
        }

        // before the first lock (pre-game lobby -> island teleport) a transition re-opens the window. once
        // locked we don't -- so a respawn after our bed is broken can't re-scan and lock an enemy bed as ours.
        if (!bedLockedThisGame && GameStateTracker.getInstance().transitionThisTick()) {
            ownBedFoot = null;
            capturing = true;
            settleTicks = SETTLE_TICKS;
            captureAge = 0;
        }

        if (capturing) {
            // throttle the 25^3 scan: no give-up means the window can stay open a whole pre-game lobby,
            // so don't pay a full scan every tick. captureAge==0 on the first tick scans immediately.
            if (captureAge++ % SCAN_EVERY == 0) {
                final BedTargetingUtil.Bed bed = BedTargetingUtil.findNearestBed(OWN_BED_SCAN_RANGE, Double.MAX_VALUE);
                if (bed != null) ownBedFoot = bed.foot;   // hold the current nearest while the window is open
            }

            if (terrainReady() && ownBedFoot != null) {
                if (--settleTicks <= 0) {
                    capturing = false;
                    bedLockedThisGame = true;   // locked for the rest of this world -> no more re-scans
                }
            } else {
                settleTicks = SETTLE_TICKS;     // terrain still loading / no bed yet -> stay open until it locks
            }
        }

        // a locked foot whose block is no longer a bed means our bed got broken -- drop it (BedAura then
        // breaks everyone). guard on the chunk being loaded so roaming away from our bed doesn't false-trip.
        // we never re-scan within a game, so it stays dropped: a later respawn can't relock an enemy bed.
        if (!capturing && ownBedFoot != null
                && mc.theWorld.isBlockLoaded(ownBedFoot)
                && mc.theWorld.getBlockState(ownBedFoot).getBlock() != Blocks.bed) {
            ownBedFoot = null;
        }
    }

    private boolean terrainReady() {
        final NetHandlerPlayClient nh = mc.getNetHandler();
        return nh == null || nh.isDoneLoadingTerrain();   // null handler (singleplayer/none) -> treat as ready
    }

    // foot of the bed we spawned next to, or null before the first capture. BedAura excludes it.
    public static BlockPos ownBedFoot() {
        return INSTANCE.ownBedFoot;
    }

    public static int tick() {
        return INSTANCE.tick;
    }

    // a hand-action module parks a rotation this tick (AutoHeal pot throw) -- aura holds, never wipes it.
    // reads accept last tick's stamp too: the owner re-stamps every tick of an episode, so a reader that
    // runs BEFORE the owner (AutoBlock @200 vs AutoHeal @50) still sees a sustained hold instead of a
    // perpetually-stale false. self-expires one idle tick later -- no stuck flag.
    public static void holdWire()        { INSTANCE.wireHeldTick = INSTANCE.tick; }
    public static boolean wireHeld()     { return INSTANCE.wireHeldTick >= INSTANCE.tick - 1; }

    // stamps the tick whenever a C02 ATTACK goes out so combat modules can avoid sending a second
    // one in the same tick. strict match on purpose, no carry-over like wireHeld.
    private int attackTick = -1;
    public static void markEntityAttack()          { INSTANCE.attackTick = INSTANCE.tick; }
    public static boolean entityAttackedThisTick() { return INSTANCE.attackTick == INSTANCE.tick; }

    // a heal is underway this tick (AutoHeal soup) -- aura releases its aim and skips the swing,
    // but this is read SEPARATELY from the engage policy so bed modules keep yielding during the eat
    public static void pauseHealing()    { INSTANCE.healPausedTick = INSTANCE.tick; }
    public static boolean healingPaused(){ return INSTANCE.healPausedTick >= INSTANCE.tick - 1; }

    // every living entity that passes EntityUtil.isValidTarget, with cached reachSq (eager, the
    // range gate needs it for all) and aim-at rotation (lazy, only in-range candidates get FOV-
    // checked). NO range/type/FOV pre-filter -- those are per-module. built on first call each
    // tick, reused after; empty when there's no player/world.
    public static List<Candidate> candidates() {
        final CombatManager r = INSTANCE;
        if (mc.thePlayer == null || mc.theWorld == null) {
            r.candidates.clear();
            r.builtTick = r.tick;
            return r.candidatesView;
        }
        if (r.builtTick == r.tick) return r.candidatesView;

        r.candidates.clear();
        final Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        for (final Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityLivingBase)) continue;
            final EntityLivingBase e = (EntityLivingBase) o;
            if (!EntityUtil.isValidTarget(e)) continue;
            r.candidates.add(new Candidate(e, RaycastUtil.reachSq(e, eyes)));
        }
        r.builtTick = r.tick;
        return r.candidatesView;
    }

    // any valid candidate whose eye-to-hitbox reachSq is within rSq -- the "enemy in range" predicate,
    // shared so the candidate scan + range test live in one place instead of being re-looped per module.
    public static boolean anyWithinReachSq(final double rSq) {
        for (final Candidate c : candidates()) {
            if (c.reachSq() <= rSq) return true;
        }
        return false;
    }

    // memoized RaycastUtil.aimRayHitsEntity against the server-reported rotation -- bakes in
    // getLastReportedYaw/Pitch so the attack ray is judged on the frame the server uses (invariant
    // #1) and no caller can accidentally feed it the camera or a flicked yaw.
    public static boolean attackRayHits(final EntityLivingBase entity, final double maxRange) {
        if (entity == null || mc.thePlayer == null) return false;
        final CombatManager r = INSTANCE;
        if (r.rayTick != r.tick) {
            r.rayCache.clear();
            r.rayTick = r.tick;
        }
        final long key = (Math.round(maxRange * 1000.0D) << 32) | (entity.getEntityId() & 0xFFFFFFFFL);
        final Boolean cached = r.rayCache.get(key);
        if (cached != null) return cached;

        final boolean hit = RaycastUtil.aimRayHitsEntity(entity,
                mc.thePlayer.getLastReportedYaw(), mc.thePlayer.getLastReportedPitch(), maxRange);
        r.rayCache.put(key, hit);
        return hit;
    }

    public static final class Candidate {
        private final EntityLivingBase entity;
        private final double reachSq;
        private float[] rotations;   // lazy: getRotations(player, entity), computed on first access

        private Candidate(final EntityLivingBase entity, final double reachSq) {
            this.entity = entity;
            this.reachSq = reachSq;
        }

        public EntityLivingBase entity() {
            return entity;
        }

        public double reachSq() {
            return reachSq;
        }

        public float[] rotations() {
            if (rotations == null) {
                rotations = RotationUtil.getRotations(mc.thePlayer, entity);
            }
            return rotations;
        }
    }
}
