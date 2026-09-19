package redacted.client.module.impl.combat;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.misc.PacketUtils;
import redacted.client.util.player.InventoryUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.CombatManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AutoBlock extends Module {

    private static final int PRIORITY = 200;       // release decision lands before SilentAura's PRE (prio 0)
    private static final int CHOKE_PRIORITY = -2000; // below FakeLag/PingSpoof: when one is live it owns the whole stream and we cede; otherwise our blink owns the stream
    private static final int MAX_TICKS_WITHOUT_POSITION = 20;
    private static final double REAL_POSITION_DELTA_SQ = 1.0E-10D;
    private static final long HYPIXEL_VISUAL_GRACE_MS = 220L;
    private static final long HYPIXEL_VISUAL_FADE_MS = 140L;
    private static final long PREDICTED_THREAT_HOLD_MS = 450L;
    private static final long BLOCKED_ATTACK_BLINK_MS = 600L;
    private static final double PREDICTED_THREAT_RANGE_SQ = 5.25D * 5.25D;
    private static final double IMMEDIATE_THREAT_RANGE_SQ = 3.85D * 3.85D;
    private static final double CLOSING_DELTA_SQ = 0.015D;
    private static AutoBlock instance;
    
    private final ModeValue mode = new ModeValue("Mode", this, "Hypixel", "Hypixel", "Fake");
    private final NumberValue hypixelMaxHurtTime = new NumberValue("Maximum Hurt Time", this, 200, 0, 500, 10, mode, "Hypixel");
    private final NumberValue hypixelMaxHoldDuration = new NumberValue("Maximum Hold Duration", this, 250, 50, 1000, 10, mode, "Hypixel");
    private final NumberValue hypixelLagChance = new NumberValue("Lag Chance", this, 0, 0, 100, 1, mode, "Hypixel");
    private final NumberValue hypixelMaxLagDuration = new NumberValue("Maximum Lag Duration", this, 120, 0, 400, 10, mode, "Hypixel");

    private final BooleanValue hypixelPreventDelayingAttacks = new BooleanValue("Prevent Delaying Attacks", this, true, mode, "Hypixel");
    private final BooleanValue hypixelBlockAgainImmediately = new BooleanValue("Block Again Immediately", this, true, mode, "Hypixel");
    private final BooleanValue hypixelSimultaneousAttackBlock = new BooleanValue("Attack Through Block", this, false, mode, "Hypixel");

    // micro-blink FIFO: held holds the choked play stream, bypass lets our own replays reach the wire.
    private final ConcurrentLinkedQueue<Packet<?>> held = new ConcurrentLinkedQueue<>();
    private final Set<Packet<?>> bypass = ConcurrentHashMap.newKeySet();
    private volatile long chokeUntil;

    // DOWN: no live block. BLOCKED: keybind held, vanilla owns the live block. RELEASED: keybind
    // dropped, the aura's swing is due, re-raise follows it.
    private enum BlockState { DOWN, BLOCKED, RELEASED }

    private BlockState state = BlockState.DOWN;
    private int releasedTick = Integer.MIN_VALUE;      // tick the RELEASED state was entered
    private int lastReleaseTick = Integer.MIN_VALUE;   // any release we sent -- no C02 may ever share its tick
    private boolean swingFiredThisTick;
    private boolean fakeBlocking;                       // Fake mode: we hold a client-only itemInUse block
    private boolean chokedClientUsingItem;              // choked ATB: pin client itemInUse so it slows (matches Grim)
    private boolean chokedFightActive;                  // ATB engaged with a live target this tick
    private long hypixelBlockStartedMs;
    private boolean hypixelHoldExpired;
    private long hypixelVisualStartedMs;
    private long hypixelVisualUntilMs;
    private volatile boolean hypixelVisualBlocking;


    private volatile boolean hurtPacket;
    private long underFireUntilMs;
    private int prevHurtTime;
    private boolean underFireNow;
    private boolean predictedThreatNow;
    private static final double UNDER_FIRE_HOLD_MIN_MS = 900.0;
    private static final double UNDER_FIRE_HOLD_MAX_MS = 1600.0;
    private int lastAttackPacketTick = Integer.MIN_VALUE;
    private int lastInteractPacketTick = Integer.MIN_VALUE;
    private int lastRealPositionPacketTick = Integer.MIN_VALUE;
    private boolean hasLastRealPosition;
    private C02PacketUseEntity pendingQueuedAttackPacket;
    private C0APacketAnimation pendingQueuedSwingPacket;
    private boolean pendingQueuedAttackReady;
    private boolean reblockKeybindPending;
    private boolean pendingRelease;          // true when blockAfterAttack stored an attack; next pre-tick releases keybind (C07)
    private boolean releaseSentThisTick;     // true during the tick the keybind was released; prevents tickHypixel from pressing hold
    private boolean attackSentThisTick;      // true during the tick bypass C0A+C02 were sent; prevents duplicate aura packets
    private boolean chokedReleasePending;     // 2-tick attack-through-block cycle: release on tick A, attack+reblock on tick B (Grim resets order flags per flying packet, so they can't share a tick)
    private boolean chokedDoRelease;           // set in tickHypixel, consumed when the vanilla C03 leaves (burst injected before it)
    private boolean chokedDoAttack;            // set in tickHypixel, consumed when the vanilla C03 leaves
    private boolean chokedDoReblock;           // set in tickHypixel to raise the block (C08) once when idle-blocking between attacks
    private boolean chokedBlockUp;             // server-side block currently raised (no C07 since last reblock)
    private Entity chokedTarget;               // target captured in tickHypixel for the attack-tick burst
    private double lastRealPositionX;
    private double lastRealPositionY;
    private double lastRealPositionZ;

    // lazy module caches
    private SilentAura silentAura;
    private FakeLag fakeLag;

    public AutoBlock() {
        super("AutoBlock", "Real sword block for 1.8", Keyboard.KEY_NONE, Category.COMBAT);
        instance = this;
    }

    // we are synthetically holding the use keybind -- InventoryUtil.isUseKeyDown() subtracts this so
    // our own hold isn't mistaken for a manual right-click (which would freeze the cycle / stop swings).
    public static boolean ownsUseKey() {
        return instance != null && instance.isEnabled() && instance.state == BlockState.BLOCKED;
    }

    private boolean isFake() {
        return mode.getValue().equals("Fake");
    }

    public static boolean isEnabledStatic() {
        return instance != null && instance.isEnabled();
    }

    public static boolean isFakeMode() {
        return instance != null && instance.isFake();
    }

    public static boolean stripsUseSlow() {
        return instance != null
                && instance.isEnabled()
                && instance.fakeBlocking
                && instance.isFake();
    }

    // read by the Minecraft.runTick guard: keep client-only itemInUse alive for visuals.
    public static boolean isFakeVisualActive() {
        return instance != null
                && instance.isEnabled()
                && (instance.fakeBlocking && instance.isFake()
                    || instance.chokedClientUsingItem);
    }

    // read by EntityPlayerSP's sprint-start guards: while Hypixel-mode AutoBlock is enabled, ALL
    // sprint is blocked -- vanilla, the Sprint module's keybind driving, and double-tap-W alike.
    public static boolean isSprintSuppressed() {
        return instance != null
                && instance.isEnabled()
                && !instance.isFake();
    }

    // READ-ONLY first/third-person render hook -- pins Hypixel's block pose to Fake's display condition so the
    // per-swing release/reblock churn renders as one continuous block. NEVER wire into EntityPlayerSP movement,
    // isBlocking(), or any packet path -- it must stay cosmetic-only.
    public static boolean isVisualBlocking() {
        return instance != null
                && instance.isEnabled()
                && !instance.isFake()
                && instance.hypixelVisualBlocking;
    }

    public static float visualBlockStrength() {
        if (instance == null || !instance.isEnabled() || instance.isFake() || !instance.hypixelVisualBlocking) return 0.0F;
        final long now = System.currentTimeMillis();
        final float raiseProg = clamp01((now - instance.hypixelVisualStartedMs) / (float) HYPIXEL_VISUAL_FADE_MS);
        final float dropProg = clamp01((instance.hypixelVisualUntilMs - now) / (float) HYPIXEL_VISUAL_FADE_MS);
        final float raw = Math.min(raiseProg, dropProg);
        return raw * raw * (3.0F - 2.0F * raw);
    }

    private static float clamp01(final float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    // log helpers -- no-op unless Debug's "AutoBlock" toggle is live, so nothing queues while off
    private static void note(final String msg, final boolean ok) {
        // logging hook was removed from this checkout
    }

    private static void logState(final String msg, final boolean ok) {
        // logging hook was removed from this checkout
    }

    private static void logPacket(final String msg, final boolean ok) {
        // logging hook was removed from this checkout
    }

    private void logSnapshot(final String stage, final boolean ok) {
        // logging hook was removed from this checkout
    }

    @Override
    public void onEnable() {
        state = BlockState.DOWN;
        releasedTick = Integer.MIN_VALUE;
        lastReleaseTick = Integer.MIN_VALUE;
        swingFiredThisTick = false;
        chokeUntil = 0L;
        held.clear();
        bypass.clear();
        fakeBlocking = false;
        chokedClientUsingItem = false;
        chokedFightActive = false;
        chokedDoReblock = false;
        chokedBlockUp = false;
        chokedReleasePending = false;
        hypixelBlockStartedMs = 0L;
        hypixelHoldExpired = false;
        hypixelVisualStartedMs = 0L;
        hypixelVisualUntilMs = 0L;
        hypixelVisualBlocking = false;
        lastAttackPacketTick = Integer.MIN_VALUE;
        lastInteractPacketTick = Integer.MIN_VALUE;
        lastRealPositionPacketTick = Integer.MIN_VALUE;
        hasLastRealPosition = false;
        lastRealPositionX = 0.0D;
        lastRealPositionY = 0.0D;
        lastRealPositionZ = 0.0D;
        pendingQueuedAttackPacket = null;
        pendingQueuedSwingPacket = null;
        pendingQueuedAttackReady = false;
        reblockKeybindPending = false;
        pendingRelease = false;
        releaseSentThisTick = false;
        attackSentThisTick = false;
        resetPosture();
        note("enabled (mode " + mode.getValue() + ")", true);
    }

    @Override
    public void onDisable() {
        note("disabled", true);
        chokedDoReblock = false;
        chokedBlockUp = false;
        chokedFightActive = false;
        chokedReleasePending = false;
        if (chokedClientUsingItem && mc.thePlayer != null) mc.thePlayer.clearItemInUse();
        chokedClientUsingItem = false;
        hardRelease();
    }

    @EventTarget(priority = PRIORITY)
    public void onPacket(final PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.INBOUND) return;
        final EntityPlayerSP player = mc.thePlayer;   // snapshot -- main thread can null it mid-disconnect
        if (player == null) return;
        if (!(event.getPacket() instanceof S12PacketEntityVelocity)) return;
        if (((S12PacketEntityVelocity) event.getPacket()).getEntityID() != player.getEntityId()) return;
        hurtPacket = true;
    }

    // release-decision site: runs before SilentAura's PRE so a release lands a tick before the swing.
    @EventTarget(priority = PRIORITY)
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        swingFiredThisTick = false;
        releaseSentThisTick = false;
        attackSentThisTick = false;
        logSnapshot("tick-pre", true);

        if (mc.thePlayer == null || mc.theWorld == null) {
            logSnapshot("tick-null", false);
            hardRelease();
            return;
        }

        // Hypixel mode: FULL sprint lockout for as long as the module is enabled. Sprint+block is
        // impossible in vanilla; the C0B START/STOP churn, 1.3x velocity and Grim's sprint-attack-slow
        // candidate are all desync sources while blocking. The EntityPlayerSP sprint-start guards
        // (isSprintSuppressed) keep vanilla / the Sprint module / WTap from re-engaging it. Sprint
        // comes back the moment AutoBlock is disabled.
        if (!isFake() && mc.thePlayer.isSprinting()) mc.thePlayer.setSprinting(false);

        // RELEASE TICK: release keybind so vanilla sends C07 this tick.
        // No C0A/C02/C08 may share this tick (PacketOrderI / MultiActions).
        if (pendingRelease) {
            final int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(useCode, false);
            releaseSentThisTick = true;
            pendingRelease = false;
            logSnapshot("tick-release-keybind", true);
        }

        // stale swing: queued swing had no matching attack (prediction miss). send it as-is.
        if (pendingQueuedSwingPacket != null && pendingQueuedAttackPacket == null) {
            sendBypass(pendingQueuedSwingPacket);
            pendingQueuedSwingPacket = null;
        }
        // ATTACK TICK: C07 already went via vanilla keybind release (separate tick).
        // Send queued swing + attack via bypass here, before C03.
        // C0A+C02 together in one tick satisfy PacketOrderB; C07 in a prior tick
        // satisfies PacketOrderI.  Skip if we just released (releaseSentThisTick)
        // to keep C07 and C02 in different ticks.
        if (pendingQueuedAttackPacket != null && !pendingQueuedAttackReady && !releaseSentThisTick) {
            if (pendingQueuedSwingPacket != null) {
                sendBypass(pendingQueuedSwingPacket);
                pendingQueuedSwingPacket = null;
            }
            final Entity deferredTarget = pendingQueuedAttackPacket.getEntityFromWorld(mc.theWorld);
            if (deferredTarget != null && deferredTarget.isEntityAlive()) {
                sendBypass(pendingQueuedAttackPacket);
                reblockKeybindPending = true;
            }
            pendingQueuedAttackReady = true;
            attackSentThisTick = true;
            logSnapshot("tick-send-deferred-attack", true);
        }

        if (isFake()) {
            hypixelVisualBlocking = false;
            tickFake();
            return;
        }
        tickHypixel();   // Hypixel mode
        refreshHypixelVisual();
    }

    private void tickHypixel() {
        refreshUnderFire();
        chokedDoRelease = false;   // consumed when the vanilla C03 leaves this tick
        chokedDoAttack = false;
        chokedTarget = null;
        // if choked ATB is off, make sure any pinned client itemInUse is dropped so the client un-slows
        if (!hypixelSimultaneousAttackBlock.getValue()) {
            chokedDoReblock = false;
            chokedBlockUp = false;
            chokedFightActive = false;
            if (chokedClientUsingItem) {
                chokedClientUsingItem = false;
                if (mc.thePlayer != null) mc.thePlayer.clearItemInUse();
            }
        }
        logSnapshot("hypixel-start", true);

        if (mc.currentScreen != null || !InventoryUtil.isHoldingSword() || mc.thePlayer.isRiding()) {
            final boolean manualUse = mc.currentScreen == null && !mc.thePlayer.isRiding() && InventoryUtil.isUseKeyDown();
            logSnapshot("hypixel-release-gate", false);
            forceRelease();
            if (manualUse) restorePhysicalUseKey();
            return;
        }

        if (InventoryUtil.isUseKeyDown()) {
            logSnapshot("hypixel-manual-use", true);
            forceRelease();
            restorePhysicalUseKey();
            return;
        }

        // only block while SilentAura actually has a locked target this tick. the old nearby-entity scan
        // raised the block at villagers / armor-stands parked by chests even with no real target.
        if (!silentAuraEngaged()) {
            hypixelHoldExpired = false;
            logSnapshot("hypixel-no-threat", true);
            forceRelease();
            return;
        }

        // During release/attack ticks, don't touch the use keybind - the pre-tick owns it.
        // C07 goes via vanilla keybind release (release tick); C0A+C02 go via bypass
        // (attack tick).  Pressing hold here would emit C08 in the wrong tick.
        if (releaseSentThisTick || attackSentThisTick) return;

        // Safety: if block has been held past max duration, release -- but ONLY to unstick a
        // pending attack. The max-hold timer exists so attackNow() never sits true with no C02
        // firing (a stuck block). Releasing on the raw timer regardless of attack state blinked
        // the block every maxHold ms (default 250ms) between swings -- with Block Again Immediately
        // it re-raised next tick, so it churned on/off constantly. Gate it on attackNow(): if no
        // swing is due, keep holding; the deferred attack path drops the block for the swing itself.
        if (state == BlockState.BLOCKED) {
            final long now = System.currentTimeMillis();
            final long maxHold = (long) hypixelMaxHoldDuration.doubleValue();
            if (hypixelBlockStartedMs > 0L && now - hypixelBlockStartedMs >= maxHold) {
                if (!attackNow()) {
                    pressHold();
                    logSnapshot("hypixel-hold-through-attack", true);
                    return;
                }
                logSnapshot("hypixel-max-hold-release", true);
                releaseReal();
                state = BlockState.DOWN;
                hypixelHoldExpired = !hypixelBlockAgainImmediately.getValue();
                requestHypixelLag();
                return;
            }
        }

        // "Attack through block": release and attack must land on separate ticks (each lead by a
        // flying packet that resets Grim's order flags). The block is held up continuously between
        // attacks (one C08 keeps the server blocking until the next C07), only dropping for the single
        // release tick right before an attack. The burst is emitted before the vanilla C03 (see
        // onOutboundPacket) so Vulcan's flight prediction and "Post" checks stay clean.
        if (hypixelSimultaneousAttackBlock.getValue()) {
            final SilentAura a = aura();
            final Entity target = a != null ? a.getTarget() : null;
            if (target != null) {
                chokedFightActive = true;
                if (chokedReleasePending) {
                    // attack tick (follows a release): attack + reblock. block stays up afterwards.
                    chokedReleasePending = false;
                    chokedDoAttack = true;
                    // stamp at schedule time (we tick at priority 200, before AutoClicker/Triggerbot
                    // at 0) so they skip this tick; the burst itself goes out later, at C03-send
                    CombatManager.markEntityAttack();
                    chokedTarget = target;
                    chokedBlockUp = true;
                    pinChokedClientBlock(true);
                } else if (attackNow() && chokedBlockUp) {
                    // release tick: drop the block so the attack next tick isn't flagged (Grim MultiActionsA)
                    chokedReleasePending = true;
                    chokedDoRelease = true;
                    chokedBlockUp = false;
                    pinChokedClientBlock(false);
                } else {
                    // idle while target present: keep the block raised (raise it the first time). The
                    // server keeps the block up after a single C08 until a C07, so we only reblock on the
                    // transition - that's what keeps the 50% damage-reduction debuff applied. The block
                    // stays up even while taking knockback (that's exactly when you want the reduction);
                    // the client is pinned slow (0.2x) in lock-step so Grim's prediction still matches
                    // and NoSlow/Simulation stay clean.
                    if (!chokedBlockUp) { chokedBlockUp = true; chokedDoReblock = true; }
                    pinChokedClientBlock(true);
                }
                return;
            }
            chokedFightActive = false;
            chokedReleasePending = false;   // lost the target mid-cycle; restart cleanly next time
            if (chokedBlockUp) { chokedBlockUp = false; chokedDoRelease = true; }
            pinChokedClientBlock(false);
        }

        // Reaching here means simultaneous mode is off (or had no target) - make sure a stale
        // pending-release flag from a previous toggle can't desync the normal block path.
        if (!hypixelSimultaneousAttackBlock.getValue()) chokedReleasePending = false;

        if (hypixelPreventDelayingAttacks.getValue() && attackNow()) {
            if (state == BlockState.BLOCKED) pressHold();
            hypixelHoldExpired = false;
            logSnapshot("hypixel-hold-for-post-attack-block", true);
            return;
        }

        if (!hypixelDamageWindowOpen()) {
            hypixelHoldExpired = false;
            logSnapshot("hypixel-hurt-window-wait", true);
            if (state == BlockState.BLOCKED) pressHold();
            return;
        }

        if (state == BlockState.BLOCKED) {
            pressHold();
            logSnapshot("hypixel-hold", true);
            return;
        }

        if (hypixelHoldExpired) {
            logSnapshot("hypixel-hold-expired", true);
            return;
        }

        if (mc.thePlayer.isUsingItem()) {
            logSnapshot("hypixel-using-item", false);
            return;
        }

        logSnapshot("hypixel-block", true);
        if (attackNow()) {
            logSnapshot("hypixel-block-suppressed-attack", false);
            return;
        }
        blockReal();
        hypixelBlockStartedMs = System.currentTimeMillis();
    }

    // Fake block: maintain a client-only itemInUse so isBlocking()/the block pose render, WITHOUT ever
    // pressing the use-key or sending a packet. the Minecraft.runTick guard keeps vanilla from
    // auto-releasing it (that would emit a C07 and clear the pose). nothing here touches the wire, so
    // there's no server-side block and no damage reduction -- it's purely the visual, only you see it.
    private void tickFake() {
        if (!canBlockVisual()) { setFakeVisual(false); return; }
        setFakeVisual(true);
    }

    // shared by Fake mode's own pose and Hypixel's visual pin -- one condition, can't drift apart.
    // mc.thePlayer/theWorld null-guarded because isVisualBlocking() reads this from the render thread.
    private boolean canBlockVisual() {
        return mc.thePlayer != null && mc.theWorld != null
                && mc.currentScreen == null
                && InventoryUtil.isHoldingSword()
                && !mc.thePlayer.isRiding()
                && silentAuraEngaged()
                && !InventoryUtil.isUseKeyDown();   // physical RMB -> let vanilla own the item-use
    }

    private boolean canHoldHypixelVisualGrace() {
        return mc.thePlayer != null && mc.theWorld != null
                && mc.currentScreen == null
                && InventoryUtil.isHoldingSword()
                && !mc.thePlayer.isRiding()
                && !InventoryUtil.isUseKeyDown();
    }

    private void refreshHypixelVisual() {
        final long now = System.currentTimeMillis();
        if (canBlockVisual()) {
            if (!hypixelVisualBlocking) hypixelVisualStartedMs = now;
            hypixelVisualUntilMs = now + HYPIXEL_VISUAL_GRACE_MS;
            hypixelVisualBlocking = true;
            return;
        }

        hypixelVisualBlocking = canHoldHypixelVisualGrace() && now < hypixelVisualUntilMs;
        if (!hypixelVisualBlocking) {
            hypixelVisualStartedMs = 0L;
            hypixelVisualUntilMs = 0L;
        }
    }

    private void setFakeVisual(final boolean enabled) {
        if (!enabled) {
            if (!fakeBlocking) return;
            if (mc.thePlayer != null) mc.thePlayer.clearItemInUse();
            fakeBlocking = false;
            note("fake block dropped", true);
            return;
        }
        final ItemStack inHand = mc.thePlayer.getHeldItem();
        if (inHand == null) {
            if (fakeBlocking) setFakeVisual(false);
            return;
        }
        // setItemInUse is a no-op when the same stack is already in use, so re-arm only when needed
        if (!fakeBlocking || !mc.thePlayer.isUsingItem()) {
            mc.thePlayer.setItemInUse(inHand, inHand.getMaxItemUseDuration());
            fakeBlocking = true;
            note("fake block raised (client-side, 0 packets)", true);
        }
    }

    private void clearFake() {
        if (!fakeBlocking) return;
        setFakeVisual(false);
    }

    // no C02 may ever share a release's tick -- vanilla swallows attack presses on its release tick too.
    // the aura treats a cancelled click as retry-next-tick without burning its cooldown, so the swing
    // lands exactly one C03 after the release. steady state never trips this; it catches the
    // overdue-cooldown edge where the aura would swing the instant we release.
    @EventTarget(priority = 500)
    public void onLeftClickEarly(final LeftClickEvent event) {
        if (!isEnabled()) return;
        if (CombatManager.tick() == lastReleaseTick) {
            event.setCancelled(true);
            logState("held C02 off the release tick (ok)", true);
        }
    }

    // dispatch stops on cancel, so getting here uncancelled means the swing fires -- covers the
    // aura and manual clicks without touching SilentAura
    @EventTarget(priority = -10000)
    public void onLeftClickDone(final LeftClickEvent event) {
        final MovingObjectPosition hit = event.getHitResult();
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            swingFiredThisTick = true;
            logSnapshot("leftclick-fire", true);
        }
    }

    // consume the hit signals and refresh the posture. main thread; the packet flag came off the netty thread.
    private void refreshUnderFire() {
        final int hurtTime = mc.thePlayer.hurtTime;
        final boolean hurtEdge = hurtTime > prevHurtTime;
        prevHurtTime = hurtTime;
        final long now = System.currentTimeMillis();
        if (hurtPacket || hurtEdge) {
            hurtPacket = false;
            underFireUntilMs = now + (long) Randoms.between(UNDER_FIRE_HOLD_MIN_MS, UNDER_FIRE_HOLD_MAX_MS);
        }
        predictedThreatNow = threatFacingUs();
        if (predictedThreatNow) {
            underFireUntilMs = Math.max(underFireUntilMs, now + PREDICTED_THREAT_HOLD_MS);
        }
        underFireNow = now < underFireUntilMs;
    }

    private boolean threatFacingUs() {
        final SilentAura aura = aura();
        if (aura == null || !aura.hasActiveRotationTarget()) return false;

        final EntityLivingBase target = aura.getTarget();
        if (!isAliveThreat(target)) return false;

        final double toPlayerX = mc.thePlayer.posX - target.posX;
        final double toPlayerZ = mc.thePlayer.posZ - target.posZ;
        final double currentDistSq = toPlayerX * toPlayerX + toPlayerZ * toPlayerZ;
        if (currentDistSq > PREDICTED_THREAT_RANGE_SQ) return false;
        if (!isThreatFacingPlayer(target, toPlayerX, toPlayerZ, currentDistSq)) return false;

        return currentDistSq <= IMMEDIATE_THREAT_RANGE_SQ
                || isThreatClosing(target, currentDistSq)
                || isThreatSwinging(target);
    }

    private boolean isAliveThreat(final EntityLivingBase target) {
        return target != null
                && mc.thePlayer != null
                && !target.isDead
                && target.isEntityAlive()
                && target != mc.thePlayer;
    }

    private boolean isThreatFacingPlayer(final EntityLivingBase target,
                                         final double toPlayerX,
                                         final double toPlayerZ,
                                         final double currentDistSq) {
        if (currentDistSq < 1.0E-4D) return true;
        final float yawToPlayer = (float) (Math.toDegrees(Math.atan2(toPlayerZ, toPlayerX)) - 90.0D);
        final float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(target.rotationYaw - yawToPlayer));
        return yawDiff <= 85.0F || (currentDistSq <= IMMEDIATE_THREAT_RANGE_SQ && yawDiff <= 110.0F);
    }

    private boolean isThreatClosing(final EntityLivingBase target, final double currentDistSq) {
        final double predictedToPlayerX = (mc.thePlayer.posX + mc.thePlayer.motionX)
                - (target.posX + target.motionX * 1.5D);
        final double predictedToPlayerZ = (mc.thePlayer.posZ + mc.thePlayer.motionZ)
                - (target.posZ + target.motionZ * 1.5D);
        final double predictedDistSq = predictedToPlayerX * predictedToPlayerX
                + predictedToPlayerZ * predictedToPlayerZ;
        return currentDistSq - predictedDistSq > CLOSING_DELTA_SQ;
    }

    private boolean isThreatSwinging(final EntityLivingBase target) {
        return target.isSwingInProgress || target.swingProgress > 0.0F;
    }

    private SilentAura aura() {
        if (silentAura == null) silentAura = Client.getInstance().getModuleManager().getModule(SilentAura.class);
        return silentAura;
    }

    private FakeLag fakeLag() {
        if (fakeLag == null) fakeLag = Client.getInstance().getModuleManager().getModule(FakeLag.class);
        return fakeLag;
    }

    // FakeLag holds the whole serverbound play stream when it's live, so we cede everything to it and
    // never re-hold its replays. when it's off our blink owns the stream during the choke window.
    private boolean fakeLagActive() {
        final FakeLag fl = fakeLag();
        return fl != null && fl.isEnabled();
    }

    // raise: hold the real use-item keybind and let vanilla's runTick air-use it into a C08 block.
    // Attack ticks use the outbound packet path below so C02 lands before the same-tick C08.
    private void blockReal() {
        flushAll();
        if (mc.playerController.getIsHittingBlock()) mc.playerController.resetBlockRemoving();
        final int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useCode, true);
        KeyBinding.onTick(useCode);
        state = BlockState.BLOCKED;
        logState("C08 raise (real block up) @t" + CombatManager.tick(), true);
    }

    private void pressHold() {
        final int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useCode, true);
        KeyBinding.onTick(useCode);
    }

    // release: drop the keybind. vanilla's runTick (use-key no longer down) calls onStoppedUsingItem
    // -> C07 the same tick, before C03. The short choke keeps release bundles ordered.
    private void releaseReal() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        hypixelBlockStartedMs = 0L;
        lastReleaseTick = CombatManager.tick();
        requestChoke(BLOCKED_ATTACK_BLINK_MS);
        logState("C07 release (block down) @t" + lastReleaseTick, true);
    }

    private void restorePhysicalUseKey() {
        if (mc.gameSettings == null || !GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem)) return;
        final int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useCode, true);
        KeyBinding.onTick(useCode);
    }

    private boolean isManualUsePacket(final Packet<?> packet) {
        return InventoryUtil.isUseKeyDown()
                && (packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C07PacketPlayerDigging);
    }

    private boolean isAttackPacket(final Packet<?> packet) {
        return packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK;
    }

    private boolean isInteractPacket(final Packet<?> packet) {
        return packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.INTERACT;
    }

    private boolean isBlockPlacementPacket(final Packet<?> packet) {
        return packet instanceof C08PacketPlayerBlockPlacement;
    }

    private boolean isBlockDigPacket(final Packet<?> packet) {
        return packet instanceof C07PacketPlayerDigging;
    }

    private boolean impossibleAttackThenBlockPlacement(final Packet<?> packet) {
        final int tick = CombatManager.tick();
        return isBlockPlacementPacket(packet)
                && lastAttackPacketTick == tick
                && lastInteractPacketTick != tick;
    }

    private boolean shouldBlockAfterAttack(final Packet<?> packet) {
        if (isFake() || mc.thePlayer == null || mc.theWorld == null || !InventoryUtil.isHoldingSword()) return false;
        if (InventoryUtil.isUseKeyDown()) return false;
        // Only defer when the block is currently up - if it's DOWN there's nothing to release first.
        if (state != BlockState.BLOCKED) return false;
        if (pendingQueuedAttackPacket != null || pendingRelease || attackSentThisTick) return false;
        if (!(packet instanceof C02PacketUseEntity)) return false;
        final Entity target = ((C02PacketUseEntity) packet).getEntityFromWorld(mc.theWorld);
        final SilentAura aura = aura();
        return target != null && aura != null && aura.isEnabled() && target == aura.getTarget();
    }

    private void sendBypass(final Packet<?> packet) {
        bypass.add(packet);
        PacketUtils.sendSilentPacket(packet);
    }

    private void blockAfterAttack(final C02PacketUseEntity attack) {
        final Entity target = attack.getEntityFromWorld(mc.theWorld);
        final ItemStack inHand = mc.thePlayer.getHeldItem();
        if (target == null || inHand == null) return;

        pendingQueuedAttackPacket = attack;
        pendingQueuedAttackReady = false;
        if (pendingQueuedSwingPacket == null) {
            pendingQueuedSwingPacket = new C0APacketAnimation();
        }

        pendingRelease = true;

        state = BlockState.BLOCKED;
        // Arm the hold timer on the first block-after-attack (after a release it's 0).
        // Don't reset it on subsequent deferrals so the safety check can still fire.
        if (hypixelBlockStartedMs == 0L) {
            hypixelBlockStartedMs = System.currentTimeMillis();
        }
    }

    // "Attack through block" for Hypixel/1.8 - must satisfy BOTH Grim and Vulcan.
    //
    // Grim resets its packet-order flags (attacking/interacting/using/releasing/...) on EVERY flying
    // (C03) packet.  So a release (C07) and an attack (C02) cannot share a tick: whichever comes
    // second always sees the first's flag still set and PacketOrderI flags it.  The only order that
    // passes is release on one tick and attack+reblock on the next, each lead by a flying packet.
    //
    // Vulcan reads C07 as blocking=true/sendingDig=true and only a flying packet clears those, so the
    // flying must sit between the release and the attack.  AutoBlockD flags C08 while attacking &&
    // !interacting, so we INTERACT_AT before reblocking (a bare INTERACT would trip Grim PacketOrderC).
    // AutoBlockA flags C02 while placing - the per-tick flying already cleared that.
    //
    // The real vanilla C03 carries the correct position for Vulcan's flight prediction - we inject the
    // burst immediately BEFORE it (see onOutboundPacket) rather than faking a C03, so Vulcan's flight
    // prediction never disagrees with our position and its "Post" checks never arm.
    //
    // Tick A (release):  C07 -> C03(vanilla)
    // Tick B (attack):   C0A -> C02(ATTACK) -> C02(INTERACT_AT) -> C08(reblock, vanilla) -> C03(vanilla)
    //
    // The burst is emitted BEFORE the vanilla C03 (see onOutboundPacket) so Vulcan's "Post" checks never
    // arm and Grim sees isSlowedByUsingItem flip true before the C03 it predicts.
    private void pinChokedClientBlock(final boolean up) {
        // keep the client's own using-item state in lock-step with the choked block so it slows 0.2x
        // (EntityPlayerSP ~675-678) exactly when the server sees isSlowedByUsingItem (from our C08).  That
        // makes Grim's 0.2x movement prediction match the client's actual (slowed) movement, so
        // NoSlow/Simulation stay clean even while strafing.  Runs on the main thread before movement.
        if (up) {
            if (!chokedClientUsingItem && mc.thePlayer != null) {
                final ItemStack held = mc.thePlayer.getHeldItem();
                if (held != null) {
                    mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
                    chokedClientUsingItem = true;
                }
            }
        } else if (chokedClientUsingItem) {
            chokedClientUsingItem = false;
            if (mc.thePlayer != null) mc.thePlayer.clearItemInUse();
        }
    }

    private void injectChokedRelease() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return;
        sendBypass(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
        ));
        releaseSentThisTick = true;
        state = BlockState.DOWN;
        hypixelBlockStartedMs = 0L;
        logSnapshot("hypixel-choked-release", true);
    }

    private void injectChokedAttack(final Entity target) {
        if (target == null || target.isDead || !target.isEntityAlive()
                || mc.thePlayer == null || mc.theWorld == null) return;
        final ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null) return;

        // C0A: swing - isSlowedByUsingItem already false (released last tick) -> Grim MultiActionsE clean.
        sendBypass(new C0APacketAnimation());

        // C02 ATTACK - Vulcan flags clear (C03 reset them); Grim MultiActionsA clean (released).
        sendBypass(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

        // C02 INTERACT_AT - satisfies Vulcan AutoBlockD (C08 while attacking && !interacting) and avoids
        // Grim PacketOrderC (a bare INTERACT without a prior INTERACT_AT is "Skipped Interact-At").
        sendBypass(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT_AT));

        // C08 reblock - use the EXACT vanilla item-use constructor so the position (-1,-1,-1) matches
        // what a real 1.8 client sends.  Grim and Vulcan both accept it; a hand-built (-1,4095,-1) is
        // what Vulcan rejected (kick).  The block goes back up for damage reduction.  chokedBlockUp is
        // true on the attack tick (set in tickHypixel), so the reblock always lands here.
        //
        // The client-side slow that makes Grim's 0.2x prediction match is NOT done here (this burst is sent
        // before the vanilla C03, off the movement path); it's pinned on the main thread in tickHypixel via
        // mc.thePlayer.setItemInUse so the client slows 0.2x in lock-step with this C08.
        if (chokedBlockUp) {
            sendBypass(new C08PacketPlayerBlockPlacement(heldItem));
            state = BlockState.BLOCKED;
        } else {
            state = BlockState.DOWN;
        }
        hypixelBlockStartedMs = 0L;
        lastAttackPacketTick = CombatManager.tick();
        pendingQueuedSwingPacket = null;
        attackSentThisTick = true;
        logSnapshot("hypixel-choked-attack", true);
    }

    private void injectChokedReblock() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return;
        // raise the block (C08, before the C03) so the server keeps it up between attacks - that's what
        // keeps the 50% damage-reduction debuff applied while idle-blocking.
        sendBypass(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        state = BlockState.BLOCKED;
        hypixelBlockStartedMs = 0L;
        logSnapshot("hypixel-choked-reblock", true);
    }

    private boolean isPositionPacket(final Packet<?> packet) {
        return packet instanceof C03PacketPlayer && ((C03PacketPlayer) packet).isMoving();
    }

    private void notePositionPacket(final C03PacketPlayer packet) {
        final double dx = packet.getPositionX() - lastRealPositionX;
        final double dy = packet.getPositionY() - lastRealPositionY;
        final double dz = packet.getPositionZ() - lastRealPositionZ;
        final boolean realDelta = !hasLastRealPosition || dx * dx + dy * dy + dz * dz > REAL_POSITION_DELTA_SQ;

        if (!realDelta) {
            logSnapshot("position-zero-delta", false);
            return;
        }

        hasLastRealPosition = true;
        lastRealPositionX = packet.getPositionX();
        lastRealPositionY = packet.getPositionY();
        lastRealPositionZ = packet.getPositionZ();
        lastRealPositionPacketTick = CombatManager.tick();
    }

    private boolean positionCadenceAtRisk() {
        return lastRealPositionPacketTick != Integer.MIN_VALUE
                && CombatManager.tick() - lastRealPositionPacketTick >= MAX_TICKS_WITHOUT_POSITION - 1;
    }

    // extend an active choke, never shrink it -- a fresh release mid-bundle just lengthens the hold.
    private void requestChoke(final long windowMs) {
        if (windowMs <= 0L) return;
        // FakeLag already holds the whole stream when it's live, so our blink is redundant -- and arming it
        // would strand packets in `held` if FakeLag gets disabled mid-window. don't even open the window.
        if (fakeLagActive()) return;
        final long until = System.currentTimeMillis() + windowMs;
        if (until > chokeUntil) chokeUntil = until;
    }

    private boolean isChoking() {
        return System.currentTimeMillis() < chokeUntil;
    }

    // hold the WHOLE serverbound play stream while the blink is open (invariant #2 -- selective
    // holding trips Grim PacketOrder). low priority: when FakeLag/PingSpoof are live they own the whole
    // stream and cancel fresh packets ahead of us (the bus breaks on cancel), so we never run; we also
    // wave their replays through here, so the two FIFOs never fight over a packet.
    @EventTarget(priority = CHOKE_PRIORITY)
    public void onOutboundPacket(final PacketEvent event) {
        if (event.isCancelled()) return;
        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return;
        final Packet<?> packet = event.getPacket();
        logPacket("OUT " + describePacket(packet), true);
        final boolean attackPacket = isAttackPacket(packet);
        if (isInteractPacket(packet)) lastInteractPacketTick = CombatManager.tick();
        if (bypass.remove(packet)) {
            if (attackPacket) lastAttackPacketTick = CombatManager.tick();
            logSnapshot("packet-bypass", true);
            return;                       // our own replay -- let it reach the wire
        }
        if (attackPacket && pendingQueuedAttackPacket != null) {
            event.setCancelled(true);
            logSnapshot("packet-aura-attack-queued-duplicate", true);
            return;
        }
        // Cancel aura's C02 on the release tick: C07 is also arriving this tick (from
        // the pre-tick keybind release) and PacketOrderI flags C07 when attacking=true.
        if (attackPacket && releaseSentThisTick) {
            event.setCancelled(true);
            logSnapshot("packet-cancel-attack-on-release-tick", true);
            return;
        }
        // Cancel aura's C0A when:
        //   - queued attack pending (deferred to next tick)
        // - release tick (C07 same tick -> PacketOrderI)
        // - attack tick (bypass C0A already sent -> duplicate swing)
        //   - BLOCKED and !attackSentThisTick: the block is up server-side (isSlowedByUsingItem=true).
        //     Any vanilla C0A here would flag MultiActionsE.  The bypass sequence (or deferred path)
        //     sends its own C0A when appropriate, so suppressing vanilla's is safe.
        if (packet instanceof C0APacketAnimation) {
            final boolean blocked = state == BlockState.BLOCKED && !isFake();
            if (pendingQueuedAttackPacket != null
                    || releaseSentThisTick
                    || attackSentThisTick
                    || blocked) {
                event.setCancelled(true);
                // Only queue for later when there's no bypass swing already sent this tick
                // and we're not BLOCKED (when BLOCKED the bypass/deferred path owns the swing).
                if (!attackSentThisTick && !blocked) {
                    pendingQueuedSwingPacket = (C0APacketAnimation) packet;
                }
                logSnapshot("packet-defer-swing-for-block", true);
                return;
            }
        }
        // Cancel aura's C02 when:
        //   - bypass attack already went out this tick (choked sequence), OR
        //   - block is up server-side (isSlowedByUsingItem=true). Any vanilla C02 here would flag
        //     MultiActionsA. The bypass sequence sends its own C02 when appropriate, so suppressing
        //     vanilla's is safe.
        if (attackPacket && (attackSentThisTick || (state == BlockState.BLOCKED && !isFake()))) {
            event.setCancelled(true);
            logSnapshot("packet-cancel-duplicate-attack-this-tick", true);
            return;
        }
        if (impossibleAttackThenBlockPlacement(packet) && !attackSentThisTick && state != BlockState.BLOCKED) {
            event.setCancelled(true);
            logSnapshot("packet-drop-impossible-attack-c08", false);
            return;
        }
        if (attackPacket && shouldBlockAfterAttack(packet)) {
            if (hypixelSimultaneousAttackBlock.getValue()) {
                event.setCancelled(true);
                logSnapshot("packet-simultaneous-duplicate-c02-cancelled", true);
                return;
            }
            event.setCancelled(true);
            blockAfterAttack((C02PacketUseEntity) packet);
            logSnapshot("packet-attack-interact-block", true);
            return;
        }
        if (attackPacket && !isFake() && isChoking() && !hypixelSimultaneousAttackBlock.getValue()) {
            event.setCancelled(true);
            held.add(packet);
            flushAll();
            logSnapshot("packet-flush-blink-attack-bundle", true);
            return;
        }
        if (attackPacket) lastAttackPacketTick = CombatManager.tick();
        if (packet instanceof C03PacketPlayer) {
            if (((C03PacketPlayer) packet).isMoving()) notePositionPacket((C03PacketPlayer) packet);
            // attack-through-block: emit the release/attack burst BEFORE the REAL vanilla C03.
            //   release tick: C07(release) -> C03
            //   attack tick:  C0A -> C02(ATTACK) -> C02(INTERACT_AT) -> C08(reblock) -> C03
            // Sending the burst *before* the flying is what makes both ACs happy:
            //   * Vulcan's "Post" checks (KillAuraA / BadPacketsM / BadPacketsX) only arm when an
            //     attack / swing / block-place lands <10ms AFTER a flying (C03).  A burst that precedes the
            //     flying can never arm them, so there is no kick.
            //   * Grim sees isSlowedByUsingItem flip true (C08) BEFORE the C03 it belongs to, so its 0.2x
            // movement prediction matches the client (pinned slow on the main thread in tickHypixel)
            //     NoSlow/Simulation stay clean.  The vanilla C03 still carries the position for Vulcan's
            //     flight prediction and resets Grim's per-flying order flags before the burst lands.
            if (chokedDoRelease) {
                chokedDoRelease = false;
                releaseSentThisTick = true;
                injectChokedRelease();
            } else if (chokedDoAttack) {
                chokedDoAttack = false;
                attackSentThisTick = true;   // suppress Aura's own attack this tick
                final Entity t = chokedTarget;
                chokedTarget = null;
                if (t != null) injectChokedAttack(t);
            } else if (chokedDoReblock) {
                chokedDoReblock = false;
                injectChokedReblock();
            }
            if (isChoking()) return;
            flushAll();
            return;
        }
        if (isBlockDigPacket(packet)) {
            return;   // C07 release must not be bundled with C0A/C02 attacks.
        }
        if (packet instanceof net.minecraft.network.play.client.C00PacketKeepAlive
                || packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction) {
            return;   // keep the connection alive; Blink leaves these alone too
        }
        if (isManualUsePacket(packet)) {
            flushAll();
            return;
        }
        if (positionCadenceAtRisk()) {
            flushAll();
            return;
        }
        if (System.currentTimeMillis() >= chokeUntil) return;    // blink closed -- pure pass-through
        if (fakeLagActive()) return;                             // FakeLag owns the whole stream -- cede everything (incl. its replays)
        if (!PacketUtils.isPlayStreamPacket(packet)) return;
        event.setCancelled(true);
        held.add(packet);
        logPacket("HOLD " + describePacket(packet), true);
    }

    // post-tick choke drain: replays held packets in FIFO order.
    @EventTarget(priority = CHOKE_PRIORITY)
    public void onMotion(final MotionEvent event) {
        if (event.isPre()) return;
        // Re-press use keybind after attack: C03 in this tick cleared attacking, so the
        // next tick's C08 won't flag PacketOrderJ. Swing+attack are dispatched in pre-tick.
        if (reblockKeybindPending) {
            reblockKeybindPending = false;
            pressHold();
        }
        // C02 is dispatched in pre-tick alongside C0A (no C07 -- that comes from vanilla
        // keybind release in the prior tick). Cleanup any stale state.
        if (pendingQueuedAttackPacket != null && pendingQueuedAttackReady) {
            pendingQueuedAttackPacket = null;
            pendingQueuedAttackReady = false;
        }
        if (System.currentTimeMillis() < chokeUntil) return;     // still bundling
        flushHeld();
    }

    // drain in FIFO order so the server reads the same sequence we held. replays go out serverbound
    // via sendSilentPacket (the held set is play-stream by construction), re-firing OUTBOUND -> the
    // bypass check above waves each one through.
    private void flushHeld() {
        if (held.isEmpty()) return;
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
            held.clear();
            bypass.clear();
            return;
        }
        Packet<?> packet;
        while ((packet = held.poll()) != null) {
            bypass.add(packet);
            logPacket("REPLAY " + describePacket(packet), true);
            PacketUtils.sendSilentPacket(packet);
        }
    }

    // unconditional drain -- never strand the FIFO on release/disable/null player.
    private void flushAll() {
        chokeUntil = 0L;
        flushHeld();
    }

    private boolean attackNow() {
        final SilentAura aura = aura();
        return aura != null && aura.willAttackThisTick();
    }

    private boolean damagePriority() {
        return predictedThreatNow || (state == BlockState.BLOCKED && underFireNow);
    }

    private boolean hypixelDamageWindowOpen() {
        final int maxHurtTicks = msToTicks((long) hypixelMaxHurtTime.doubleValue());
        return mc.thePlayer.hurtTime <= maxHurtTicks || underFireNow;
    }

    private void requestHypixelLag() {
        if (hypixelMaxLagDuration.doubleValue() <= 0.0D) return;
        if (hypixelLagChance.doubleValue() <= 0.0D) return;
        if (hypixelPreventDelayingAttacks.getValue() && attackNow()) return;
        if (Randoms.between(0.0D, 100.0D) > hypixelLagChance.doubleValue()) return;
        requestChoke((long) Randoms.between(0.0D, hypixelMaxLagDuration.doubleValue()));
    }

    // SilentAura's Adaptive CPS mode clamps its swing delay up to this floor. under fire we cap CPS
    // ~5 so the real block keeps its uptime; 0 when safe (or no real block up) = normal band speed.
    public static long swingCpsDelayFloorMs() {
        if (instance == null || !instance.isEnabled() || instance.isFake()) return 0L;
        return instance.underFireNow ? 200L : 0L;
    }

    private static int msToTicks(final long ms) {
        return (int) Math.max(1L, (long) Math.ceil(ms / 50.0));
    }

    private String describePacket(final Packet<?> packet) {
        if (packet instanceof net.minecraft.network.play.client.C03PacketPlayer) {
            return "C03";
        }
        if (packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging) {
            return "C07";
        }
        if (packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement) {
            return "C08";
        }
        if (packet instanceof net.minecraft.network.play.client.C00PacketKeepAlive) {
            return "C00";
        }
        if (packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction) {
            return "C0F";
        }
        return packet.getClass().getSimpleName();
    }

    // mid-operation release: defer the C07 one tick when a swing already rode, so it won't trail this
    // tick's C02 (release-after-swing is backwards). the held keybind drops next tick instead.
    private void forceRelease() {
        final boolean released = state == BlockState.BLOCKED;
        if (state == BlockState.BLOCKED) {
            if (swingFiredThisTick) { resetPosture(); return; }   // an open choke drains on its own next tick
            releaseReal();
        }
        state = BlockState.DOWN;
        hypixelBlockStartedMs = 0L;
        hypixelHoldExpired = false;
        pendingRelease = false;
        releaseSentThisTick = false;
        attackSentThisTick = false;
        clearFake();
        resetPosture();
        if (!released) flushAll();   // a real unblock intentionally starts blink; don't cancel it here
    }

    // unconditional drop -- disable, null player. never leaves the keybind synthetically held.
    private void hardRelease() {
        if (mc.gameSettings != null)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        clearFake();
        state = BlockState.DOWN;
        hypixelBlockStartedMs = 0L;
        hypixelHoldExpired = false;
        hypixelVisualStartedMs = 0L;
        hypixelVisualUntilMs = 0L;
        hypixelVisualBlocking = false;
        pendingQueuedAttackPacket = null;
        pendingQueuedSwingPacket = null;
        pendingQueuedAttackReady = false;
        reblockKeybindPending = false;
        pendingRelease = false;
        releaseSentThisTick = false;
        attackSentThisTick = false;
        resetPosture();
        flushAll();
    }

    private void resetPosture() {
        hurtPacket = false;
        underFireUntilMs = 0L;
        prevHurtTime = 0;
        underFireNow = false;
        predictedThreatNow = false;
    }

    private boolean silentAuraEngaged() {
        final SilentAura aura = aura();
        return aura != null && aura.hasActiveRotationTarget();
    }
}
