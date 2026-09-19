/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import baritone.api.utils.Rotation;
import redacted.client.util.Randoms;
import redacted.util.RotationUtil;

/**
 * KFC port addition: makes the bot look and act like a human playing, instead of
 * like the textbook baritone fingerprints the BaritoneRemover plugin looks for:
 *
 *  - AutoWalkA   (moves with zero pitch change)        -> per-tick pitch noise
 *  - RepeatedA/C (identical rotation deltas repeating) -> GCD mouse-stepped turns
 *  - Cinematic   (too-smooth monotone rotation graph)  -> random jitter on every step
 *  - TargetLock* (canonical snap to block center)      -> aim noise, never a perfect lock
 *  - TimeBetween (constant dig/place cadence)          -> randomized delays
 *  - RepeatedB/D (identical movement deltas)           -> random sprint releases
 *
 * All values are small enough to not hurt pathing: the noise is within what a real
 * player's aim does while holding a direction key.
 */
public final class Humanizer {

    private Humanizer() {}

    /** Mouse-like turn speed in degrees per tick (GCD-corrected by RotationUtil).
     *  Kept under 5.0 so a single tick never registers as a "snap" (TruthfulAC BaritoneA
     *  keys on yaw snaps >= 5 landing on the 45-degree grid). */
    private static final float LOOK_SPEED = 4.0F;

    /** How far off the perfect aim we allow ourselves: breaks canonical aim-locks. */
    private static final float AIM_NOISE_YAW = 1.2F;
    private static final float AIM_NOISE_PITCH = 0.9F;

    /**
     * Steps the current look toward the target like a real mouse move (GCD-snapped,
     * capped per tick) and adds a small per-tick wobble so consecutive rotation
     * packets are never identical (Grim AimDuplicateLook flags zero-delta looks).
     *
     * @return {yaw, pitch} to apply this tick
     */
    public static float[] stepLook(float fromYaw, float fromPitch, float toYaw, float toPitch) {
        float[] stepped = RotationUtil.stepToward(fromYaw, fromPitch, toYaw, toPitch, LOOK_SPEED, 0.15F);
        boolean converged = stepped[0] == fromYaw && stepped[1] == fromPitch;
        // per-tick wobble: mostly tiny, occasionally a bit bigger, sometimes zero
        // but never a zero-delta packet once the look has settled on its target
        double roll = Randoms.between(0.0D, 1.0D);
        if (roll < 0.30D && !converged) {
            return stepped; // one clean step in three is fine while actually turning
        }
        float yaw = (float) (stepped[0] + Randoms.between(-0.55D, 0.55D));
        float pitch = (float) (stepped[1] + Randoms.between(-0.45D, 0.45D));
        return new float[]{yaw, pitch};
    }

    /**
     * Sticky per-target aim offset: regenerate only when the target block changes so
     * the look converges instead of dancing around a new random point every tick.
     */
    public static float[] aimNoiseOffset() {
        return new float[]{
                (float) Randoms.between(-AIM_NOISE_YAW, AIM_NOISE_YAW),
                (float) Randoms.between(-AIM_NOISE_PITCH, AIM_NOISE_PITCH)
        };
    }

    public static Rotation aimNoise(Rotation target, float[] offset) {
        return new Rotation(target.getYaw() + offset[0], target.getPitch() + offset[1]);
    }

    /**
     * Deliberately imperfect aim point for breaking/placing: never a canonical
     * lock onto the exact block center or face midpoint.
     */
    public static Rotation aimNoise(Rotation target) {
        return aimNoise(target, aimNoiseOffset());
    }

    /**
     * Random sprint releases: ~2.5% of ticks the bot drops sprint for a tick,
     * which varies the per-tick movement deltas (RepeatedB/D look for identical
     * delta series).
     */
    public static boolean sprintShouldHold() {
        return Randoms.between(0.0D, 1.0D) > 0.025D;
    }

    /**
     * Random delay before starting the next dig after finishing one.
     */
    public static int nextBreakDelayTicks() {
        return (int) Randoms.between(0, 6);
    }

    /**
     * Random delay before the next right-click place.
     */
    public static int nextPlaceDelayTicks(int baseSpeed) {
        return baseSpeed + (int) Randoms.between(0, 4);
    }
}
