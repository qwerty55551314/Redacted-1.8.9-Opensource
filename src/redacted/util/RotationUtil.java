package redacted.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.event.impl.player.EventMoveInput;
import redacted.client.event.impl.player.MotionEvent;

import java.util.concurrent.ThreadLocalRandom;


public final class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final RotationUtil INSTANCE = new RotationUtil();

    private static final long ACTIVE_TIMEOUT_MS = 500L;
    private static final float MANUAL_ATTACK_TOLERANCE = 10.0F;

    // stop fidgeting once we're this close (deg). stays under SilentAura's attack tolerance
    // so held aim still passes the hit checks.
    private static final float HOLD_BAND = 2.0F;

    // fraction of the remaining gap a single step may close -- see stepToward. rolled per step
    // across this band rather than held fixed: one constant makes every decay an exact halving
    // (deltaYaw 6.0 then 3.0 on the wire), and that ratio is itself a signature.
    private static final float EASE_MIN = 0.42F;
    private static final float EASE_SPAN = 0.26F;

    private boolean active;
    private boolean movementFix = true;
    private long lastRequest;
    private float serverYaw;
    private float serverPitch;

    // smooth-release: armed via beginRelease(), onTick eases the held rotation back to the real
    // camera BEFORE physics each tick (so wire yaw == physics frame on every release tick -- a
    // post-physics step would send a yaw the tick's movement was never simulated in and GrimAC's
    // Simulation check flags exactly that strafe mismatch). cleared the instant any module asks
    // for a rotation again, so a concurrent aura always wins it back.
    private boolean releasing;
    private float releaseSpeed;
    // while a release is easing out, hold the player still so we don't walk under the stale spoof yaw
    private boolean lockMovement;

    // opt-in per spoof: when set, the manual left-click attack raytrace is redirected to this rotation
    // (see EntityRenderer.getMouseOver's second pick). Only SilentAim asks for it -- Scaffold/BedAura/
    // AutoBlock/plain SilentAura leave it false so their world interactions stay camera-true.
    private boolean redirectClicks;

    // opt-in per spoof: render the spoof as a head-only turn -- the local body model holds its line
    private boolean headOnlyRender;

    // baritone wire bridge
    // while Baritone silently pathes (freeLook on, non-forced walking look), its humanized
    // AimProcessor owns the outgoing rotation. LookBehavior stamps the stepped aim here every
    // tick; onMotion puts it on the wire and the EntityPlayerSP movement-fix hooks run physics
    // at the same yaw, so wire and prediction stay identical without touching the camera.
    // A module spoof (any setRotation within the active timeout) always outranks it.
    private boolean baritoneWireActive;
    private float baritoneWireYaw;
    private float baritoneWirePitch;

    private RotationUtil() {}

    public static RotationUtil getInstance() {
        return INSTANCE;
    }

    public static void setRotation(final float yaw, final float pitch, final float speed) {
        setRotationImpl(yaw, pitch, speed, HOLD_BAND, false, false);
    }

    /** Same as setRotation but flags this spoof to redirect manual left-click attacks (SilentAim). */
    public static void setRotation(final float yaw, final float pitch, final float speed,
                                   final boolean redirectClicks) {
        setRotationImpl(yaw, pitch, speed, HOLD_BAND, redirectClicks, false);
    }

    /** Same as setRotation but without the HOLD_BAND; steps by speed even when close. */
    public static void setRotationDirect(final float yaw, final float pitch, final float speed) {
        setRotationImpl(yaw, pitch, speed, 0.0F, false, false);
    }

    /** Same as setRotationDirect but renders as a head-only turn -- the body model holds its line. */
    public static void setRotationDirect(final float yaw, final float pitch, final float speed,
                                         final boolean headOnly) {
        setRotationImpl(yaw, pitch, speed, 0.0F, false, headOnly);
    }

    private static void setRotationImpl(final float yaw, final float pitch, final float speed,
                                        final float holdBand, final boolean redirectClicks,
                                        final boolean headOnly) {
        final RotationUtil r = INSTANCE;
        r.releasing = false;   // a fresh request cancels any in-flight ease-back
        r.lockMovement = false; // ...and hands movement back, so a module taking the wire owns its own input
        if (!r.isActiveInternal()) {
            final EntityPlayerSP player = mc.thePlayer;
            if (player != null) {
                r.serverYaw = player.getLastReportedYaw();
                r.serverPitch = player.getLastReportedPitch();
            }
        }
        r.active = true;
        r.redirectClicks = redirectClicks;
        r.headOnlyRender = headOnly;
        r.lastRequest = System.currentTimeMillis();

        final float[] stepped = stepToward(r.serverYaw, r.serverPitch, yaw, pitch, speed, holdBand);
        if (stepped[0] == r.serverYaw && stepped[1] == r.serverPitch) {
            // a held spoof inside the hold band used to re-stamp a bit-identical rotation every
            // tick. onUpdateWalkingPlayer only emits a look packet when `d3 != 0 || d4 != 0`, so
            // the client sent NONE: server-side that reads as a head bolted to one exact angle for
            // 10-20 ticks while you strafe and land every hit (Grim AimDuplicateLook -- the same
            // check baritone's Humanizer already wobbles for). drift a mouse unit or three on one
            // axis instead. self-bounding: next tick's stepToward pulls it back once it leaves the
            // band. the 1-3 spread is not decoration -- a fixed one unit makes this, which is most
            // of the packet stream in a fight, a constant, and MX's RNN reads the magnitude series
            // through entropy/distinct/stddev/gini/IQR (StatisticalSequencePreprocessor).
            final float units = 1.0F + ThreadLocalRandom.current().nextInt(3);
            final float drift = getGCD() * units * (ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F);
            if (ThreadLocalRandom.current().nextBoolean()) r.serverYaw += drift;
            else r.serverPitch = clampPitch(r.serverPitch + drift);
            return;
        }
        r.serverYaw = stepped[0];
        r.serverPitch = stepped[1];
    }

    public static float[] stepToward(final float fromYaw, final float fromPitch,
                                     final float toYaw, final float toPitch, final float speed) {
        return stepToward(fromYaw, fromPitch, toYaw, toPitch, speed, HOLD_BAND);
    }

    public static float[] stepToward(final float fromYaw, final float fromPitch,
                                     final float toYaw, final float toPitch,
                                     final float speed, final float holdBand) {
        final float maxStep = Math.max(0.0F, speed);
        final float yawDiff = MathHelper.wrapAngleTo180_float(toYaw - fromYaw);
        final float pitchDiff = clampPitch(toPitch) - fromPitch;
        final float dist = MathHelper.sqrt_float(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // settled: hand the input straight back. callers detect convergence by identity on this
        // return (baritone's Humanizer.stepLook), so it has to stay an exact no-op.
        if (dist <= holdBand) {
            return new float[] { fromYaw, clampPitch(fromPitch) };
        }

        // one shared scale for both axes -- a mouse moves yaw and pitch along a single vector.
        // clamping each axis on its own let pitch (usually only a few degrees off) land in one
        // tick while yaw was still sweeping, so every acquisition traced an L instead of a line.
        //
        // EASE caps the step at a fraction of what is left, so the approach decays (30, 15, 7, 3)
        // instead of running flat out and stopping dead against the hold band. that stop was the
        // shape both MX's Factor check (a big yaw step with idle ticks either side) and FF's
        // jump/stutter (speed=0.15 right after lastSpeed=57) were reading -- a real mouse
        // decelerates onto a target, it does not arrive at full speed.
        final float ease = EASE_MIN + ThreadLocalRandom.current().nextFloat() * EASE_SPAN;
        final float t = Math.min(ease, maxStep / dist);
        return new float[] {
                applyGCD(fromYaw, fromYaw + yawDiff * t),
                clampPitch(applyGCD(fromPitch, fromPitch + pitchDiff * t))
        };
    }

    public static void reset() {
        INSTANCE.active = false;
        INSTANCE.releasing = false;
        INSTANCE.lockMovement = false;
        INSTANCE.redirectClicks = false;
        INSTANCE.headOnlyRender = false;
    }

    /**
     * Ease the held rotation back to the real camera over the next few ticks instead of the 500ms
     * timeout's freeze-then-snap. No-op when nothing is held. Any setRotation/reset call cancels it,
     * so a concurrent module keeps control.
     */
    public static void beginRelease(final float speed) {
        beginRelease(speed, false);
    }

    /** Same as beginRelease but also holds the player still until the ease-back finishes. */
    public static void beginRelease(final float speed, final boolean lock) {
        final RotationUtil r = INSTANCE;
        if (!r.isActiveInternal()) {
            return;
        }
        r.releasing = true;
        r.releaseSpeed = speed;
        r.lockMovement = lock;
        r.lastRequest = System.currentTimeMillis();   // keep alive while we ease out
    }

    public static void setMovementFix(final boolean value) {
        INSTANCE.movementFix = value;
    }

    public static boolean isMovementFix() {
        return INSTANCE.movementFix;
    }

    public static boolean isActive() {
        return INSTANCE.isActiveInternal();
    }

    // true while a click-redirect spoof (SilentAim) holds the wire -- EntityRenderer.getMouseOver
    // uses this to build the second, spoofed interaction pick.
    public static boolean redirectClicksActive() {
        return INSTANCE.isActiveInternal() && INSTANCE.redirectClicks;
    }

    public static boolean hasRenderRotations() {
        return INSTANCE.isActiveInternal();
    }

    public static boolean hasMovementFixRotations() {
        return INSTANCE.isActiveInternal() && INSTANCE.movementFix;
    }

    // baritone wire bridge API (driven by baritone.behavior.LookBehavior)

    /** Stamps baritone's stepped silent aim as this tick's wire rotation. */
    public static void setBaritoneWire(final float yaw, final float pitch) {
        INSTANCE.baritoneWireActive = true;
        INSTANCE.baritoneWireYaw = yaw;
        INSTANCE.baritoneWirePitch = pitch;
    }

    /** Releases the wire back to vanilla (baritone done pathing, world change, module took over). */
    public static void clearBaritoneWire() {
        INSTANCE.baritoneWireActive = false;
    }

    public static boolean isBaritoneWireActive() {
        return INSTANCE.baritoneWireActive;
    }

    /**
     * Whether physics should run at a server yaw other than the camera: a module spoof with
     * movement fix enabled, or baritone's silent wire hold. Used by the EntityPlayerSP
     * moveFlying/jump movement-fix hooks.
     */
    public static boolean hasEffectiveServerYaw() {
        return (INSTANCE.isActiveInternal() && INSTANCE.movementFix) || INSTANCE.baritoneWireActive;
    }

    /**
     * The yaw physics should run at: the module spoof when one is active, otherwise
     * baritone's silent wire hold. Only meaningful when {@link #hasEffectiveServerYaw()}
     * is true.
     */
    public static float getEffectiveServerYaw() {
        return INSTANCE.isActiveInternal() ? INSTANCE.serverYaw : INSTANCE.baritoneWireYaw;
    }

    public static float getServerYaw() {
        return INSTANCE.serverYaw;
    }

    public static float getServerPitch() {
        return INSTANCE.serverPitch;
    }

    private boolean isActiveInternal() {
        return active && (System.currentTimeMillis() - lastRequest) <= ACTIVE_TIMEOUT_MS;
    }

    // release ease-back owner: steps the held rotation toward the camera BEFORE physics runs.
    // priority -10 so every module's PRE handler has already spoken: a beginRelease() armed this
    // tick gets its first step immediately, and physics + the C03 stamp land on one aligned yaw.
    // stepping must never happen at wire time (onMotion) -- the packet would carry a rotation this
    // tick's movement was not simulated in and prediction ACs (GrimAC Simulation) flag the strafe.
    @EventTarget(priority = -10)
    public void onTick(final EventTick event) {
        if (!event.isPre() || !releasing || !isActiveInternal() || mc.thePlayer == null) {
            return;
        }
        // holdBand 0: ease all the way in. the old `<= HOLD_BAND` exit handed back from up to 2
        // degrees off the camera, and vanilla then closed that gap in one delta that is NOT a
        // multiple of the GCD -- one off-grid rotation on the wire per disengage, every disengage,
        // and Grim's AimProcessor derives your sensitivity from exactly those delta GCDs.
        final float[] stepped = stepToward(serverYaw, serverPitch,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, releaseSpeed, 0.0F);
        // no movement left == the residual is under one mouse unit, which no real mouse can close.
        // that is the handover point, and it also guarantees this terminates.
        final boolean settled = stepped[0] == serverYaw && stepped[1] == serverPitch;
        serverYaw = stepped[0];
        serverPitch = stepped[1];
        if (settled) {
            // back on the camera -- drop out and let vanilla own physics + wire from here
            active = false;
            releasing = false;
            lockMovement = false;
            redirectClicks = false;
            headOnlyRender = false;
        }
    }

    @EventTarget
    public void onMotion(final MotionEvent event) {
        if (!event.isPre() || mc.thePlayer == null) {
            return;
        }
        if (isActiveInternal()) {
            // release stepping already happened pre-physics in onTick -- serverYaw is final for
            // this tick, so the stamp always matches the frame the movement was simulated in.
            event.setYaw(serverYaw);
            event.setPitch(serverPitch);
            return;
        }
        if (baritoneWireActive) {
            // baritone's humanized silent aim: already stepped by its AimProcessor,
            // just stamp it on the wire so packet and physics agree
            redacted.client.util.debug.DebugSystem.log(redacted.client.util.debug.DebugSystem.BARITONE,
                    "wire: baritone hold yaw=" + String.format("%.2f", baritoneWireYaw)
                            + " pitch=" + String.format("%.2f", baritoneWirePitch));
            event.setYaw(baritoneWireYaw);
            event.setPitch(baritoneWirePitch);
        }
    }

    // priority -1 so we zero after every module handler -- nothing re-enables movement above the lock
    @EventTarget(priority = -1)
    public void onMoveInput(final EventMoveInput event) {
        if (!lockMovement || !isActiveInternal()) {
            return;
        }
        event.setMoveForward(0.0F);
        event.setMoveStrafe(0.0F);
        event.setJump(false);
    }

    @EventTarget
    public void onLeftClick(final LeftClickEvent event) {
        if (!isActiveInternal()) {
            return;
        }
        // aura raytraces against lastReportedYaw before firing, which is what the server actually
        // uses on the wire. our manual-tolerance check compares serverYaw to the target's center
        // and would happily kill a leading aura hit during strafe -- skip it for those.
        if (event.isFromAura()) {
            return;
        }
        final MovingObjectPosition hit = event.getHitResult();
        if (hit == null) {
            return;
        }
        // SilentAim already aimed this hit via the spoofed interaction pick (built off lastReported)
        // let entity attacks through without the manual-tolerance gate. BLOCK still falls to the cancel
        // below, so a spoofed-ray miss into a block never digs at the wrong angle (invariant #4).
        if (redirectClicks && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            event.setCancelled(true);
            return;
        }
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null && mc.thePlayer != null) {
            final float[] want = getRotations(mc.thePlayer, hit.entityHit.posX,
                    hit.entityHit.posY + hit.entityHit.getEyeHeight(), hit.entityHit.posZ);
            if (getRotationDifference(serverYaw, serverPitch, want[0], want[1]) > MANUAL_ATTACK_TOLERANCE) {
                event.setCancelled(true);
            }
        }
    }

    public static float[] getRotations(final EntityLivingBase from, final EntityLivingBase to) {
        final double diffX = to.posX - from.posX;
        final double diffZ = to.posZ - from.posZ;
        final double diffY = (to.posY + to.getEyeHeight()) - (from.posY + from.getEyeHeight());
        return rotationsFromDeltas(diffX, diffY, diffZ);
    }

    public static float[] getRotations(final Entity from, final double x, final double y, final double z) {
        final double diffX = x - from.posX;
        final double diffZ = z - from.posZ;
        final double diffY = y - (from.posY + from.getEyeHeight());
        return rotationsFromDeltas(diffX, diffY, diffZ);
    }

    public static float[] getRotations(final Vec3 from, final Vec3 to) {
        return rotationsFromDeltas(to.xCoord - from.xCoord, to.yCoord - from.yCoord, to.zCoord - from.zCoord);
    }

    private static float[] rotationsFromDeltas(final double diffX, final double diffY, final double diffZ) {
        final double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        final float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        final float pitch = (float) -(Math.atan2(diffY, dist) * 180.0 / Math.PI);
        return new float[] { yaw, clampPitch(pitch) };
    }

    public static float getYawDifference(final float fromYaw, final float toYaw) {
        return MathHelper.wrapAngleTo180_float(toYaw - fromYaw);
    }

    public static float getRotationDifference(final float fromYaw, final float fromPitch,
                                              final float toYaw, final float toPitch) {
        final float dYaw = MathHelper.wrapAngleTo180_float(toYaw - fromYaw);
        final float dPitch = toPitch - fromPitch;
        return MathHelper.sqrt_float(dYaw * dYaw + dPitch * dPitch);
    }

    public static float clampPitch(final float pitch) {
        return MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }

    // smallest yaw/pitch step a real mouse can produce at the player's sensitivity.
    public static float getGCD() {
        final float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return f * f * f * 1.2F;
    }

    // snap the step to a whole number of GCD units so rotations look mouse-driven. truncates
    // toward zero, so we never overshoot the target inside a single step. a remainder within
    // 0.1% of a whole unit is float noise from re-quantizing an already-snapped value (scaffold
    // routes its solved pitch through here twice) -- treat it as exact, or a full GCD step
    // silently vanishes and the re-trace tests a rotation one mouse-click off the wire.
    public static float applyGCD(final float current, final float target) {
        final float gcd = getGCD();
        if (gcd <= 0.0F) {
            return target;
        }
        float delta = target - current;
        float rem = delta % gcd;
        if (Math.abs(rem) < gcd * 0.001F || gcd - Math.abs(rem) < gcd * 0.001F) rem = 0.0F;
        delta -= rem;
        return current + delta;
    }

    public static float[] remapMovement(final float strafe, final float forward,
                                        final float realYaw, final float serverYaw) {
        // movement remap: given the real WASD input at the real camera yaw, find the (strafe,
        // forward) that, when run through moveFlying at the SERVER yaw, produces movement in the
        // closest possible direction to the real one. this is the standard 8-direction approach
        // used by Rise (MoveUtil.fixMovement), Moonlight (MovementUtil.fixMovement), and Augustus.
        //
        // the 8-direction quantization is NOT a precision loss - it's essential: the server
        // simulates ALL 8 possible WASD combos at the C03 yaw and checks if the actual movement
        // falls within one of them. the remap ensures the player's movement IS one of those 8,
        // so the Simulation offset is ~0. a continuous (sub-integer) remap would produce movement
        // that doesn't match ANY of the server's 8 predictions -> Simulation flag.
        final float mag = Math.max(Math.abs(strafe), Math.abs(forward));
        if (strafe == 0.0F && forward == 0.0F) {
            return new float[] { strafe, forward };
        }

        // target direction vector (from real yaw + real WASD): the movement the PLAYER intends
        final float rr = realYaw * (float) Math.PI / 180.0F;
        final float rts = MathHelper.sin(rr);
        final float rtc = MathHelper.cos(rr);
        final float targetDX = strafe * rtc - forward * rts;
        final float targetDZ = forward * rtc + strafe * rts;

        // find the (s, f) in {-1, 0, 1}² that produces the closest direction at the server yaw.
        // this matches the server's simulation of all 8 WASD combos at the C03 yaw.
        final float sr = serverYaw * (float) Math.PI / 180.0F;
        final float ss = MathHelper.sin(sr);
        final float sc = MathHelper.cos(sr);

        float bestS = strafe;
        float bestF = forward;
        float bestDot = -Float.MAX_VALUE;
        for (int s = -1; s <= 1; s++) {
            for (int f = -1; f <= 1; f++) {
                if (s == 0 && f == 0) continue;
                final float cdx = s * sc - f * ss;
                final float cdz = f * sc + s * ss;
                final float cl = (float) Math.sqrt(cdx * cdx + cdz * cdz);
                final float dot = (targetDX * cdx + targetDZ * cdz) / cl;
                if (dot > bestDot) {
                    bestDot = dot;
                    bestS = s;
                    bestF = f;
                }
            }
        }
        return new float[] { bestS * mag, bestF * mag };
    }

}
