/*
 * Humanized aim engine for the embedded Baritone port.
 *
 * Upstream Baritone (1.19.4 AimProcessor) smooths rotations by stepping one
 * mouse-pixel per tick toward a wrapAnglesToRelative-wrapped target. That is
 * seam-safe and mouse-grid-true, but at ~0.15 deg/tick a 180 deg turn takes
 * over a minute of game time -- which is why the previous KFC humanizer
 * (4 deg/tick linear cap + wobble) existed, and why it broke convergence and
 * got deleted, leaving a one-tick instant snap.
 *
 * This port keeps upstream's two load-bearing properties:
 *   1. the yaw delta is normalized to +-180 (never crosses the seam wrong), and
 *   2. every step is quantized to whole "mouse pixels" derived from the
 *      player's actual sensitivity, so outgoing deltas are exactly what a
 *      physical mouse produces (GCD-style checks see a native grid).
 *
 * and upgrades the motion profile to a human flick: speed ramps with the
 * smoothstep of the remaining distance (fast mid-turn, decelerating into the
 * target), per-tick speed jitter breaks constant-delta patterns, and a settle
 * band stops the chase instead of oscillating around the target. When the
 * target doesn't care about pitch (Baritone's convention: target pitch ==
 * current player pitch), the pitch drifts back toward a natural level with a
 * slow bounded wobble so long walks aren't pitch-frozen.
 */
package baritone.behavior.look;

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;

import java.util.Random;

public final class AimProcessor {

    private final IPlayerContext ctx;
    private final Random rand = new Random();

    /**
     * The persistent aim state. This is the rotation the processor is currently
     * holding; it survives across ticks so the wire rotation glides instead of
     * teleporting between per-tick targets (the old silent-mode bug).
     */
    private float aimYaw;
    private float aimPitch;
    private boolean engaged;

    // per-tick random offsets, rerolled once per tick (upstream randomLooking / randomLooking113)
    private double randomYawOffset;
    private double randomPitchOffset;

    // bounded slow pitch wobble state for don't-care pitch targets (AutoWalkA defense)
    private float wobbleOffset;
    private int wobbleDir = 1;

    // last step magnitudes, exposed for debug logging
    private float lastStepYaw;
    private float lastStepPitch;

    public AimProcessor(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEngaged() {
        return this.engaged;
    }

    public float getAimYaw() {
        return this.aimYaw;
    }

    public float getAimPitch() {
        return this.aimPitch;
    }

    public float getLastStepYaw() {
        return this.lastStepYaw;
    }

    public float getLastStepPitch() {
        return this.lastStepPitch;
    }

    /** Seeds the aim state from a known rotation (camera or last-reported wire yaw). */
    public void syncTo(final float yaw, final float pitch) {
        this.aimYaw = yaw;
        this.aimPitch = Rotation.clampPitch(pitch);
        this.engaged = true;
        this.lastStepYaw = 0.0F;
        this.lastStepPitch = 0.0F;
    }

    public void disengage() {
        this.engaged = false;
        this.wobbleOffset = 0.0F;
    }

    /** Rerolls the per-tick aim noise. Call exactly once per game tick. */
    public void tick() {
        this.randomYawOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;
        this.randomPitchOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;

        double random = this.rand.nextDouble() - 0.5;
        if (Math.abs(random) < 0.1) {
            random *= 4;
        }
        this.randomYawOffset += random * Baritone.settings().randomLooking113.value;
    }

    /**
     * Advances the persistent aim one tick toward the target and returns the new aim.
     *
     * @param target       the desired rotation (raw; noise is applied internally)
     * @param wobblePitch  enables the micro pitch wobble when the target doesn't care
     *                     about pitch (walking look -- keeps long walks from being pitch-frozen)
     */
    public Rotation advance(final Rotation target, final boolean wobblePitch) {
        final Rotation next = this.peek(target, wobblePitch);
        this.aimYaw = next.getYaw();
        this.aimPitch = next.getPitch();
        return next;
    }

    /**
     * Computes the next aim from the current state toward the target without
     * mutating the persistent state.
     */
    public Rotation peek(final Rotation target, final boolean wobblePitch) {
        final float minStep = getMinStep();

        float desiredPitch = target.getPitch();
        // Baritone marks "pitch doesn't matter" by passing the player's current
        // pitch as the target (see MovementHelper.moveTowards). Detect that and
        // substitute a natural level + wobble instead of holding rock-steady.
        final boolean pitchDoesntMatter = this.engaged
                && Math.abs(target.getPitch() - this.ctx.player().rotationPitch) < 1.0E-4F;
        if (pitchDoesntMatter) {
            desiredPitch = nudgeToLevel(this.aimPitch);
            if (wobblePitch && Baritone.settings().humanizedPitchWobble.value) {
                this.wobbleOffset += this.wobbleDir * minStep * (0.5F + this.rand.nextFloat());
                if (this.wobbleOffset > 1.2F) {
                    this.wobbleOffset = 1.2F;
                    this.wobbleDir = -1;
                } else if (this.wobbleOffset < -1.2F) {
                    this.wobbleOffset = -1.2F;
                    this.wobbleDir = 1;
                } else if (this.rand.nextDouble() < 0.2) {
                    this.wobbleDir = -this.wobbleDir;
                }
                desiredPitch += this.wobbleOffset;
            }
        } else {
            this.wobbleOffset = 0.0F;
        }
        desiredPitch += this.randomPitchOffset;

        final float yawDelta = Rotation.normalizeYaw(target.getYaw() + (float) this.randomYawOffset - this.aimYaw);
        final float pitchDelta = Rotation.clampPitch(desiredPitch) - this.aimPitch;

        // human flick profile: speed scales with the smoothstep of the remaining
        // distance, so a 180 stays a ~10-tick flick while fine aim decelerates
        // into the target. per-tick jitter keeps the delta stream non-constant.
        final float dist = Math.max(Math.abs(yawDelta), Math.abs(pitchDelta));
        final double speedCap = Math.max(1.0D, Baritone.settings().humanizedTurnSpeed.value);
        final double easeDist = Math.max(1.0D, Baritone.settings().humanizedTurnEaseDistance.value);
        final float w = (float) Math.min(1.0D, dist / easeDist);
        final float ease = w * w * (3.0F - 2.0F * w);
        final double jitter = 0.85D + this.rand.nextDouble() * 0.30D;
        final double maxStep = Math.max(minStep, speedCap * ease * jitter);

        final float settle = (float) Math.max(0.05D, Baritone.settings().humanizedAimSettle.value);

        this.lastStepYaw = quantizedStep(yawDelta, maxStep, minStep, settle);
        this.lastStepPitch = quantizedStep(pitchDelta, maxStep, minStep, settle);

        return new Rotation(
                this.aimYaw + this.lastStepYaw,
                Rotation.clampPitch(this.aimPitch + this.lastStepPitch)
        );
    }

    /**
     * Clamps the delta to the flick speed cap, then quantizes it to a whole
     * number of mouse pixels. While outside the settle band the step is never
     * quantized to zero (a real mouse always moves in whole pixels), and inside
     * the band the chase stops entirely so the aim rests instead of oscillating.
     */
    private float quantizedStep(final float delta, final double maxStep, final float minStep, final float settle) {
        if (Math.abs(delta) <= settle) {
            return 0.0F;
        }
        float stepped = delta;
        if (Math.abs(stepped) > maxStep) {
            stepped = Math.signum(stepped) * (float) maxStep;
        }
        int pixels = Math.round(stepped / minStep);
        if (pixels == 0) {
            pixels = delta > 0.0F ? 1 : -1;
        }
        return pixels * minStep;
    }

    /** Smallest rotation step a physical mouse can produce at the current sensitivity. */
    private float getMinStep() {
        final float f = Minecraft.getMinecraft().gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return Math.max(0.001F, f * f * f * 8.0F * 0.15F);
    }

    /** Nudges the pitch toward a natural walking level, 1 degree per tick (upstream behavior). */
    private float nudgeToLevel(final float pitch) {
        if (pitch < -20.0F) {
            return pitch + 1.0F;
        }
        if (pitch > 10.0F) {
            return pitch - 1.0F;
        }
        return pitch;
    }
}
