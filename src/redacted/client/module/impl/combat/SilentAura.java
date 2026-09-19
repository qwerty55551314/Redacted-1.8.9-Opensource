package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.client.util.Randoms;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.Render3DUtil;
import redacted.client.value.impl.RangeValue;
import redacted.util.CombatManager;
import redacted.util.RaycastUtil;
import redacted.util.RotationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SilentAura extends Module {

    private static final int   DEFAULT_ATTACK_COOLDOWN_TICKS = 4;

    // Random aim point: offset around the eye-center aim point, re-rolled on target change and
    // every few seconds so the look never rests pixel-perfect on the dead-center tell
    private static final double AIM_JITTER_XZ        = 0.30D;  // +- fraction of hitbox width/depth
    private static final int    JITTER_REROLL_MIN    = 10;     // ticks before a re-roll
    private static final int    JITTER_REROLL_SPAN   = 11;

    private static final double DEBUG_MARKER_HALF    = 0.05D;  // 10cm debug cube

    // margin (blocks) the current Closest-mode target keeps over a rival. deliberately linear, not
    // squared: a squared slack shrinks to ~0.2 blocks at 3m, under the amplitude of two players
    // jostling, and the pick still ping-ponged. a real margin is proper hysteresis -- B must be
    // 0.5 closer to take the lock from A, and A must be 0.5 closer to take it back.
    private static final double STICKY_MARGIN        = 0.5D;

    private final NumberValue range   = new NumberValue("Range", this, 3.0, 1.0, 6.0, 0.1).describe("How far to target and swing");
    private final NumberValue fov     = new NumberValue("FOV", this, 90.0, 5.0, 360.0, 1.0).describe("Cone width to pick a target in");
    private final ModeValue   switchMode = new ModeValue("Switch Mode", this, "Closest", "Closest", "Switch").describe("Closest = nearest target. Switch = rotates targets periodically for GrimAC simulation stability");
    private final NumberValue switchDelay = new NumberValue("Switch Delay", this, 20.0, 1.0, 100.0, 1.0, switchMode, "Switch").describe("Ticks between target switches");
    private final ModeValue   aimPoint = new ModeValue("Aim Point", this, "Random", "Random", "Locked").describe("Where on the target the aim ray lands. Random = a shifting point inside the hitbox, like a player who doesn't hit the same pixel twice. Locked = dead-center eye level");
    private final RangeValue  rotationSpeed = new RangeValue("Rotation Speed", this, 18.0, 30.0, 10.0, 34.0, 5.0).describe("Turn speed (deg/tick), rolled between min and max each tick so it never sits at one constant. Stay under 35: MX's Factor check flags a single-tick yaw step above that when the ticks either side are idle, which is every acquisition");
    private final ModeValue   cpsMode = new ModeValue("CPS Mode", this, "Adaptive", "Custom", "Adaptive").describe("Custom = fixed CPS band. Adaptive = AutoBlock lowers CPS under fire for block uptime");
    private final RangeValue  cps     = new RangeValue("CPS", this, 8.0, 12.0, 1.0, 20.0, 1.0).describe("Attack clicks-per-second range");

    // entity types come from the shared Target Filter module (disabled filter = target everything)
    private final BooleanValue debug = new BooleanValue("Debug", this, false)
            .describe("Draw a cube where the aim ray ends -- green when it lands on the target, red when it misses");

    private final Random random = new Random();
    // sibling modules, fetched lazily
    private AntiFireBall antiFireBall;
    private Reach reach;
    private long nextAttack;
    private long clientTick;              // advances once per runTick (per batched tick), unlike the frozen wall clock
    private long lastAttackTick;
    private int  attackCooldownTicks = DEFAULT_ATTACK_COOLDOWN_TICKS;
    private EntityLivingBase switchTarget;   // sticky Switch-mode pick -- survives list membership shifts
    private EntityLivingBase stickyTarget;   // sticky Closest-mode pick -- see selectTarget
    private int switchTimer;
    private boolean useKeyDownLastTick;   // carry: was the use-key down last tick? (covers the block-release tick)
    private boolean wasAiming;            // true while actively tracking -- guards releaseAim() from wiping another module's rotation
    private int targetMemoTick = Integer.MIN_VALUE;
    private TargetData targetMemo;

    // Switch-mode scratch: filtered candidate buffer + stable order, reused across ticks
    private final List<CombatManager.Candidate> switchBuf = new ArrayList<CombatManager.Candidate>();
    private static final Comparator<CombatManager.Candidate> ENTITY_ID_ORDER =
            new Comparator<CombatManager.Candidate>() {
                @Override
                public int compare(final CombatManager.Candidate a, final CombatManager.Candidate b) {
                    return Integer.compare(a.entity().getEntityId(), b.entity().getEntityId());
                }
            };

    // Random aim-point scratch: current offset + when to re-roll it
    private int  jitterEntityId = -1;
    private long jitterRerollTick = -1L;
    private double jitOffX, jitOffY, jitOffZ;

    public SilentAura() {
        super("Silent Aura", "Silently rotates to the nearest target and attacks it",
                Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        nextAttack = 0L;
        clientTick = 0L;
        lastAttackTick = -DEFAULT_ATTACK_COOLDOWN_TICKS;   // first swing ready immediately on the tick gate too
        attackCooldownTicks = DEFAULT_ATTACK_COOLDOWN_TICKS;
        useKeyDownLastTick = false;
        wasAiming = false;
        targetMemoTick = Integer.MIN_VALUE;
        targetMemo = null;
        switchTarget = null;
        stickyTarget = null;
        switchTimer = 0;
        jitterEntityId = -1;
        jitterRerollTick = -1L;
    }

    // never persists enabled -- always starts off on launch; GameStateTracker stands it down on game transitions
    @Override
    public boolean persistEnabled() { return false; }

    @Override
    public void onDisable() {
        releaseAim();
        nextAttack = 0L;
        useKeyDownLastTick = false;
        targetMemoTick = Integer.MIN_VALUE;
        targetMemo = null;
        switchTarget = null;
        stickyTarget = null;
    }

    // drop our claim on the wire only if we own it -- an idle aura must never wipe
    // another module's held rotation (scaffold mid-bridge etc)
    private void releaseAim() {
        if (!wasAiming) return;
        wasAiming = false;
        // ownership transferred away mid-claim (AutoHeal parked a pot rotation via holdWire):
        // our claim is stale, but the wire is NOT ours to touch anymore
        if (CombatManager.wireHeld()) return;
        if (isAntiFireBallHandlingThreat()) return;
        // ease back to the camera like Scaffold/AntiFireBall instead of the timeout freeze-then-snap
        RotationUtil.beginRelease(rollRotationSpeed());
    }

    // healing wins the hand THIS tick: drop the claim outright instead of easing back, so no
    // spoofed rotation rides along AutoHeal's swap/eat packets (the eased release kept steering
    // the wire for several ticks into the soup swap and flagged under KB). same ownership rules
    // as releaseAim -- only touch the wire if WE were aiming, never if a hand-action module
    // parked it (holdWire). one clean snap back to camera; a single flick is human.
    private void forceStandDown() {
        final boolean owned = wasAiming;
        wasAiming = false;
        if (owned && !CombatManager.wireHeld()) RotationUtil.reset();
    }

    private enum Engage { FIGHT, YIELD_RELEASE, YIELD_HOLD }

    // single source for the engage guards. onTick and the prediction predicate (willAttackThisTick,
    // which AutoBlock reads) both run through this, so AutoBlock's C07-release oracle can't drift
    // from the real swing. no cooldown/use-key/soup checks here -- those differ per caller
    private Engage engagePolicy() {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return Engage.YIELD_RELEASE;
        if (mc.currentScreen != null || !mc.inGameHasFocus) return Engage.YIELD_RELEASE;
        if (isHoldingHypixelMenuItem()) return Engage.YIELD_RELEASE;
        if (CombatManager.wireHeld()) return Engage.YIELD_HOLD;   // a hand-action module parked the wire -- don't wipe it
        if (isAntiFireBallHandlingThreat()) return Engage.YIELD_RELEASE;
        return Engage.FIGHT;
    }

    private boolean isHoldingHypixelMenuItem() {
        final ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        if (held.getItem() != Items.compass && held.getItem() != Items.nether_star) return false;
        if (!held.hasDisplayName()) return true;
        final String name = held.getDisplayName().toLowerCase();
        return name.contains("game menu")
                || name.contains("selector")
                || name.contains("navigator")
                || name.contains("profile")
                || name.contains("lobby");
    }

    private boolean baseEngageGuards() {
        return engagePolicy() == Engage.FIGHT;
    }

    // soup-pause deliberately NOT gated here: bed modules keep yielding off this during an eat,
    // which keeps their C08s from cancelling the soup use
    public boolean hasActiveRotationTarget() {
        return baseEngageGuards() && findTargetData() != null;
    }

    // any valid enemy merely within reach -- no FOV/engage gate. BedAura's "Pause On Enemy" yields off
    // this so it stands down before the target even enters the aim cone.
    public boolean hasEnemyInRange() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        final double maxRange = getMaxRange(reachModule());
        return CombatManager.anyWithinReachSq(maxRange * maxRange);
    }

    // engage guards + soup gates; null when the aura shouldn't be fighting at all
    private TargetData combatTarget() {
        if (!baseEngageGuards()) return null;
        if (CombatManager.healingPaused()) return null;
        return findTargetData();
    }

    // ms clock for full CPS variance -- AutoBlock's real block releases for the swing on its own.
    private boolean swingCooldownReady() {
        return System.currentTimeMillis() >= nextAttack;
    }

    public boolean willAttackThisTick() {
        final TargetData target = combatTarget();
        if (target == null || !swingCooldownReady() || manualUseBlocksAttack()) return false;
        // mirror onTick exactly (AutoBlock predicts off this)
        return CombatManager.attackRayHits(target.entity, target.maxRange)
                && reachGate(target.entity, target.reachModule)
                && !CombatManager.entityAttackedThisTick();
    }

    // current aim target, exposed for the "Only Aura" gates on other modules
    public EntityLivingBase getTarget() {
        final TargetData target = findTargetData();
        return target == null ? null : target.entity;
    }

    @EventTarget
    public void onTick(final EventTick event) {
        // POST: advance the use-key carry after PRE has run, so it captures what AutoBlock set this tick
        if (event.isPost()) { useKeyDownLastTick = InventoryUtil.isUseKeyDown(); clientTick++; return; }
        if (!event.isPre()) return;

        final Engage policy = engagePolicy();
        if (policy != Engage.FIGHT) {
            if (policy == Engage.YIELD_RELEASE) releaseAim();
            return;
        }
        if (CombatManager.healingPaused()) { forceStandDown(); return; }

        final TargetData target = findTargetData();
        if (target == null) {
            releaseAim();
            return;
        }

        final boolean swingReady = swingCooldownReady();

        final float[] rot = aimRotations(target);
        // positions are frozen across the PRE window, so the scan-time rotation is still exact
        RotationUtil.setRotation(rot[0], rot[1], rollRotationSpeed());
        wasAiming = true;   // after the claim, so the flag never says "owned" without a held wire

        if (!swingReady) return;
        if (manualUseBlocksAttack()) return;

        if (CombatManager.attackRayHits(target.entity, target.maxRange)
                && reachGate(target.entity, target.reachModule)
                && !CombatManager.entityAttackedThisTick()) {
            executeAttack(target.entity);
        }
    }

    @EventTarget
    public void onRender3D(final Render3DEvent event) {
        if (!debug.getValue()) return;
        if (mc.thePlayer == null) return;
        final TargetData target = combatTarget();
        if (target == null) return;   // debug cube only while targeting an entity
        Render3DUtil.start3D();
        drawAimRayMarker(target);
        Render3DUtil.end3D();
    }

    // debug marker: the exact ray attackRayHits judges -- server-reported rotation, from the eye out to
    // maxRange, same inputs RaycastUtil.aimRayHitsEntity uses (NOT the camera, and not the pending
    // RotationUtil.getServerYaw, which sits a step ahead of what the server believes). green = that ray
    // lands on the target. calls aimRayHitsEntity directly on purpose: CombatManager.attackRayHits
    // memoizes into a per-tick cache onTick reads, and a render-thread call must not seed it.
    private void drawAimRayMarker(final TargetData target) {
        final float  yaw      = mc.thePlayer.getLastReportedYaw();
        final float  pitch    = mc.thePlayer.getLastReportedPitch();
        final double maxRange = getMaxRange(reachModule());
        final Vec3   eyes     = mc.thePlayer.getPositionEyes(1.0F);
        final Vec3   look     = mc.thePlayer.getVectorForRotation(pitch, yaw);
        final double[] v = Render3DUtil.viewerOffset();
        final double x = eyes.xCoord + look.xCoord * maxRange - v[0];
        final double y = eyes.yCoord + look.yCoord * maxRange - v[1];
        final double z = eyes.zCoord + look.zCoord * maxRange - v[2];
        final int color = RaycastUtil.aimRayHitsEntity(target.entity, yaw, pitch, maxRange) ? 0xFF55FF55 : 0xFFFF5555;

        Render3DUtil.drawBox(new AxisAlignedBB(
                        x - DEBUG_MARKER_HALF, y - DEBUG_MARKER_HALF, z - DEBUG_MARKER_HALF,
                        x + DEBUG_MARKER_HALF, y + DEBUG_MARKER_HALF, z + DEBUG_MARKER_HALF),
                ColorUtil.withAlpha(color, 90), color);
    }

    private boolean isAntiFireBallHandlingThreat() {
        if (antiFireBall == null) {
            antiFireBall = Client.getInstance().getModuleManager().getModule(AntiFireBall.class);
        }
        return antiFireBall != null && antiFireBall.isEnabled() && antiFireBall.isHandlingThreat();
    }

    private Reach reachModule() {
        if (reach == null) {
            reach = Client.getInstance().getModuleManager().getModule(Reach.class);
        }
        return reach;
    }

    // yield while the use-key is down this tick or last. the carry covers the block-release tick: the
    // swing lands AFTER vanilla's C07 release with a C03 between them, which keeps Grim's MultiActionsE
    // and PacketOrderI quiet. raw keybind only, never isUsingItem() -- AutoBlock owns that state.
    private boolean manualUseBlocksAttack() {
        return InventoryUtil.isUseKeyDown() || useKeyDownLastTick;
    }

    private void executeAttack(final EntityLivingBase target) {
        final MovingObjectPosition hit   = new MovingObjectPosition(target);
        final LeftClickEvent       click = Client.getInstance().getEventManager().call(new LeftClickEvent(hit, true));
        if (click.isCancelled()) return;   // no swing fired -- retry next tick instead of burning the cycle
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        CombatManager.markEntityAttack();   // keep triggerbot from stacking a second attack this tick
        final long delay = cpsDelay();   // roll the cooldown ONCE -> the committed deadline
        nextAttack = System.currentTimeMillis() + delay;
        lastAttackTick = clientTick;
        attackCooldownTicks = Math.max(1, (int) Math.round(delay / 50.0));
    }

    private long cpsDelay() {
        // bell-shaped CPS roll: a flat uniform click stream is exactly what statistical checks
        // (Photon-style) call "bot"; the gaussian keeps delay analyzers fed instead (see Randoms).
        final double rolledCps = Math.max(1.0D, Randoms.gaussian(random, cps.getRangeMin(), cps.getRangeMax()));
        final long rolled = (long) (1000.0D / rolledCps);
        if (!cpsMode.getValue().equals("Adaptive")) return rolled;   // Custom = gaussian-jittered band
        // Adaptive: honor AutoBlock's under-fire floor -- ~200ms when a threat is up and the block
        // needs its uptime, 0 when safe / no live threat. inert unless AutoBlock is up in Hypixel mode.
        final long floor = AutoBlock.swingCpsDelayFloorMs();
        return floor > 0L ? Math.max(rolled, floor) : rolled;
    }

    private float rollRotationSpeed() {
        return (float) Randoms.gaussian(random, rotationSpeed.getRangeMin(), rotationSpeed.getRangeMax());
    }

    private double getMaxRange(final Reach reachModule) {
        double maxRange = range.getValue();
        if (reachModule != null && reachModule.isEnabled()) {
            maxRange = reachModule.isVulcan() ? reachModule.getEffectiveReach() : maxRange + reachModule.getExtension();
        }
        return maxRange;
    }

    // Reach safe-gate. With the Reach module off this used to return true outright, leaving the
    // attack ray as the only limit -- and aimRayHitsEntity measures to the entry point on the
    // 0.1-EXPANDED hitbox, so it clears at 3.0 while Grim, which measures to the unexpanded box,
    // reads up to ~0.17 further on a grazing edge hit. The Random aim point makes grazing hits the
    // norm, not the exception, so Grim was cancelling those C02s: swing plays, nothing lands.
    // Fall back to Grim's own metric, one constant under its budget (shared with Reach).
    private boolean reachGate(final EntityLivingBase target, final Reach reachModule) {
        if (reachModule == null || !reachModule.isEnabled()) {
            return RaycastUtil.reach(target, mc.thePlayer.getPositionEyes(1.0F)) <= Reach.GRIM_SAFE_REACH;
        }
        return reachModule.canHit(target);
    }

    // rotations toward the aim point. Locked = dead-center eye (target.rotations, already computed by
    // CombatManager). Random = an offset picked once per target engagement and re-rolled every few
    // seconds: the smoothing still converges (offsets stay inside the hitbox), but the look never
    // rests pixel-perfect on the eye-center lock ACs flag as machine aim.
    private float[] aimRotations(final TargetData target) {
        if ("Locked".equals(aimPoint.getValue())) return target.rotations;
        final EntityLivingBase e = target.entity;
        if (e.getEntityId() != jitterEntityId || clientTick >= jitterRerollTick) {
            jitterEntityId = e.getEntityId();
            jitterRerollTick = clientTick + JITTER_REROLL_MIN + random.nextInt(JITTER_REROLL_SPAN);
            final AxisAlignedBB box = e.getEntityBoundingBox();
            jitOffX = (random.nextDouble() * 2.0D - 1.0D) * (box.maxX - box.minX) * AIM_JITTER_XZ;
            jitOffZ = (random.nextDouble() * 2.0D - 1.0D) * (box.maxZ - box.minZ) * AIM_JITTER_XZ;
            // scale off the room ABOVE/BELOW the eye, not the full box height: the eye sits at ~0.9h,
            // so a symmetric fraction of the height puts the top of the roll above the hitbox, the
            // attack ray passes over the head and attackRayHits stays false for the whole 10-20 tick
            // lifetime of the offset (a half-to-full second of no swings, not one dropped tick).
            final double eyeY = e.posY + e.getEyeHeight();
            final double roomY = Math.min(box.maxY - eyeY, eyeY - box.minY);
            jitOffY = (random.nextDouble() * 2.0D - 1.0D) * roomY;
        }
        return RotationUtil.getRotations(mc.thePlayer,
                e.posX + jitOffX,
                e.posY + e.getEyeHeight() + jitOffY,
                e.posZ + jitOffZ);
    }

    // one selection per tick -- inputs are frozen across the PRE window (see CombatManager), and every
    // rider (AutoBlock, Criticals, the bed modules) must agree with the swing on the same target
    private TargetData findTargetData() {
        final int tick = CombatManager.tick();
        if (targetMemoTick == tick) return targetMemo;
        targetMemoTick = tick;
        targetMemo = selectTarget();
        return targetMemo;
    }

    private TargetData selectTarget() {
        final Reach      reachModule   = reachModule();
        final double     maxRange      = getMaxRange(reachModule);
        final double     maxRangeSq    = maxRange * maxRange;
        final boolean    wireRef       = RotationUtil.isActive();
        final float      refYaw        = wireRef ? RotationUtil.getServerYaw()   : mc.thePlayer.rotationYaw;
        final float      refPitch      = wireRef ? RotationUtil.getServerPitch() : mc.thePlayer.rotationPitch;
        final double     fovValue      = fov.getValue();

        if (switchMode.getValue().equals("Switch")) {
            return switchSelect(reachModule, maxRange, maxRangeSq, refYaw, refPitch, fovValue);
        }

        // Closest mode. ranked, not raw-nearest: two players within a few centimetres of each other
        // used to swap the pick tick by tick, which splits one 8-12 CPS attack stream across two
        // entities. server-side that is multi-aura -- no human alternates targets at click speed
        // and it is the one thing here an AC can call with certainty. the incumbent gets slack so a
        // rival has to be genuinely closer to take the lock. Switch mode has been sticky by entity
        // reference since it was written (see switchSelect); this is the same idea for Closest.
        EntityLivingBase closest     = null;
        float[]          closestRot  = null;
        double           closestRank = Double.MAX_VALUE;

        for (final CombatManager.Candidate c : CombatManager.candidates()) {
            final EntityLivingBase e = c.entity();
            if (!TargetFilter.allows(e)) continue;
            final double reachSq = c.reachSq();
            if (reachSq > maxRangeSq) continue;
            final double dist = Math.sqrt(reachSq);
            final double rank = (e == stickyTarget) ? dist - STICKY_MARGIN : dist;
            // skips the gate/lazy-rotation work for anyone ranked worse than the current best
            if (rank > closestRank) continue;
            // never lock an out-of-budget target: without this the aura aims at someone the Reach
            // safe-gate will never let land while a closer hittable enemy exists (frozen aura)
            if (!reachGate(e, reachModule)) continue;
            final float[] rot = c.rotations();
            if (RotationUtil.getRotationDifference(refYaw, refPitch, rot[0], rot[1]) > fovValue) continue;
            closestRank = rank;
            closest = e;
            closestRot = rot;
        }
        stickyTarget = closest;
        return closest == null ? null : new TargetData(closest, closestRot, maxRange, reachModule);
    }

    // sticky Switch selection: hold the picked ENTITY across ticks even as candidates enter/leave
    // range/FOV, advancing only on death/despawn/lost-validity or when switchDelay elapses. the old
    // index-into-a-shifting-list made the target hop whenever anyone joined or left the cone.
    private TargetData switchSelect(final Reach reachModule, final double maxRange, final double maxRangeSq,
                                    final float refYaw, final float refPitch,
                                    final double fovValue) {
        switchBuf.clear();
        for (final CombatManager.Candidate c : CombatManager.candidates()) {
            final EntityLivingBase e = c.entity();
            if (!TargetFilter.allows(e)) continue;
            final double reachSq = c.reachSq();
            if (reachSq > maxRangeSq) continue;
            if (!reachGate(e, reachModule)) continue;   // same no-starvation gate as Closest mode
            final float[] rot = c.rotations();
            if (RotationUtil.getRotationDifference(refYaw, refPitch, rot[0], rot[1]) > fovValue) continue;
            switchBuf.add(c);
        }

        if (switchBuf.isEmpty()) {
            switchTarget = null;
            switchTimer = 0;
            return null;
        }

        Collections.sort(switchBuf, ENTITY_ID_ORDER);

        int idx = -1;
        if (switchTarget != null) {
            for (int i = 0; i < switchBuf.size(); i++) {
                if (switchBuf.get(i).entity() == switchTarget) { idx = i; break; }
            }
        }
        if (idx == -1 || switchTimer >= switchDelay.intValue()) {
            // lost the old target -> take the lowest id; delay elapsed -> next in sorted order
            idx = idx == -1 ? 0 : (idx + 1) % switchBuf.size();
            switchTimer = 0;
        } else {
            switchTimer++;
        }

        final CombatManager.Candidate cur = switchBuf.get(idx);
        switchTarget = cur.entity();
        return new TargetData(cur.entity(), cur.rotations(), maxRange, reachModule);
    }

    private static final class TargetData {
        private final EntityLivingBase entity;
        private final float[] rotations;
        private final double maxRange;
        private final Reach reachModule;

        private TargetData(final EntityLivingBase entity, final float[] rotations,
                           final double maxRange, final Reach reachModule) {
            this.entity = entity;
            this.rotations = rotations;
            this.maxRange = maxRange;
            this.reachModule = reachModule;
        }
    }

}
