package redacted.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

/**
 * Bow ballistics: the charge->speed curve plus a solver that finds the launch (yaw,pitch) whose arc
 * actually passes through a point, and leads a moving target across the arrow's flight time.
 *
 * The arc model matches the in-game arrow exactly on the parts that matter: launch speed
 * strength*3.0 (ItemBow.onPlayerStoppedUsing), gravity 0.05/tick and drag 0.99/tick
 * (EntityArrow.onUpdate). The arrow spawns 0.1 below the eye; the tiny 0.16 lateral spawn jitter is
 * ignored. The server's own setThrowableHeading adds random spread, so a perfect solve is
 * impossible regardless -- we aim at the deterministic centre of that spread.
 */
public final class ProjectileUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static final double BOW_GRAVITY = 0.05D;
    public static final double AIR_DRAG = 0.99D;

    // EntityArrow leaves 0.1 below the shooter's eye
    private static final double LAUNCH_Y_OFFSET = -0.1D;

    // give up on a candidate pitch if the arc hasn't covered the horizontal distance by here (10s)
    private static final int MAX_FLIGHT_TICKS = 200;
    private static final float SCAN_STEP = 0.5F;
    private static final int BISECT_ITERS = 16;

    private ProjectileUtil() {
    }

    // charge -> launch speed for the bow in hand right now. 0 when not actually drawing this stack
    // or still under the 0.05 strength floor the game itself won't fire below.
    public static float bowVelocity(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBow)) return 0.0F;
        final EntityPlayer p = mc.thePlayer;
        if (p == null || !p.isUsingItem() || p.getItemInUse() != stack) return 0.0F;
        final float drawTicks = stack.getMaxItemUseDuration() - p.getItemInUseCount();
        return strengthToVelocity(drawTicks / 20.0F);
    }

    // speed of a fully-drawn shot -- what the arrow leaves at on a held-to-full release.
    public static float fullDrawVelocity() {
        return strengthToVelocity(1.0F);
    }

    private static float strengthToVelocity(final float t) {
        float strength = (t * t + t * 2.0F) / 3.0F;
        if (strength > 1.0F) strength = 1.0F;
        if (strength < 0.05F) return 0.0F;
        return strength * 3.0F;
    }

    public static final class Aim {
        public final float yaw;
        public final float pitch;
        public final int flightTicks;   // ticks for the arrow to reach the target's horizontal distance
        public final boolean reachable; // false => out of range for this speed; pitch is the best effort

        Aim(final float yaw, final float pitch, final int flightTicks, final boolean reachable) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.flightTicks = flightTicks;
            this.reachable = reachable;
        }
    }

    /** Launch rotation so a bow arrow from {@code eyes} at {@code speed} passes through {@code target}. */
    public static Aim solveBowAim(final Vec3 eyes, final Vec3 target, final double speed) {
        final double dx = target.xCoord - eyes.xCoord;
        final double dy = target.yCoord - eyes.yCoord;
        final double dz = target.zCoord - eyes.zCoord;
        final double horizDist = Math.sqrt(dx * dx + dz * dz);

        // yaw and the straight-at-target pitch come from the shared rotation math (clamped already)
        final float[] straight = RotationUtil.getRotations(eyes, target);
        final float yaw = straight[0];
        final float directPitch = straight[1];

        // degenerate / no-charge cases: just point straight at it
        if (horizDist < 1.0E-4D || speed <= 0.0D) {
            return new Aim(yaw, directPitch, 0, false);
        }

        // Aiming straight at the target the arrow always lands low (it starts below the eye and only
        // ever droops), so f = height-at-distance minus dy is <= 0 at directPitch. Tilting up
        // (pitch more negative) raises that height monotonically up to the max-range angle. So we
        // scan up from directPitch for the first crossing -- that's the low, flat, fast arc -- then
        // bisect it. Running out of reach (sim can't carry the distance) before the crossing means
        // the target is simply out of range.
        float prevPitch = directPitch;
        Double prevF = null;
        float bestPitch = directPitch;
        double bestErr = Double.MAX_VALUE;
        int bestFlight = 0;

        for (float p = directPitch; p >= -89.0F; p -= SCAN_STEP) {
            final double[] res = simulate(p, speed, horizDist);
            if (res == null) break;   // too steep to carry horizDist -> past the low arc, out of range
            final double f = res[0] - dy;
            final double err = Math.abs(f);
            if (err < bestErr) {
                bestErr = err;
                bestPitch = p;
                bestFlight = (int) Math.round(res[1]);
            }
            if (prevF != null && prevF <= 0.0D && f >= 0.0D) {
                final float solved = bisect(prevPitch, p, speed, horizDist, dy);
                final double[] sres = simulate(solved, speed, horizDist);
                final int flight = sres != null ? (int) Math.round(sres[1]) : bestFlight;
                return new Aim(yaw, RotationUtil.clampPitch(solved), flight, true);
            }
            prevF = f;
            prevPitch = p;
        }

        // never cleared the target height -> out of range; aim the closest arc we found
        return new Aim(yaw, RotationUtil.clampPitch(bestPitch), bestFlight, false);
    }

    /**
     * As {@link #solveBowAim} but leads a moving target: predicted = target + vel*flightTicks,
     * re-solved a few times so the flight time and the lead converge. {@code vel} is blocks/tick.
     */
    public static Aim solveLeadAim(final Vec3 eyes, final Vec3 target, final Vec3 vel, final double speed) {
        Aim aim = solveBowAim(eyes, target, speed);
        if (vel == null || (vel.xCoord == 0.0D && vel.yCoord == 0.0D && vel.zCoord == 0.0D)) {
            return aim;
        }
        for (int i = 0; i < 4; i++) {
            final double flight = aim.flightTicks;
            final Vec3 predicted = new Vec3(
                    target.xCoord + vel.xCoord * flight,
                    target.yCoord + vel.yCoord * flight,
                    target.zCoord + vel.zCoord * flight);
            final Aim next = solveBowAim(eyes, predicted, speed);
            if (next.flightTicks == aim.flightTicks) {
                return next;   // lead stopped moving -> converged
            }
            aim = next;
        }
        return aim;
    }

    // bisect for the pitch where height-at-distance == dy. on entry pDown has f<=0 and pUp has f>=0
    // (f rises as pitch decreases), so the root sits between them.
    private static float bisect(final float pDown, final float pUp, final double speed,
                                final double horizDist, final double dy) {
        float down = pDown;
        float up = pUp;
        for (int i = 0; i < BISECT_ITERS; i++) {
            final float mid = (down + up) * 0.5F;
            final double[] res = simulate(mid, speed, horizDist);
            if (res == null) {
                // mid is too steep to carry the distance -> pull the up bound back down
                up = mid;
                continue;
            }
            if (res[0] - dy >= 0.0D) {
                up = mid;
            } else {
                down = mid;
            }
        }
        return (down + up) * 0.5F;
    }

    // step the arc at this launch pitch (horizontal axis only, yaw is irrelevant to the drop) and
    // return {height, flightTicks} when it first covers horizDist horizontally, or null if it never
    // does within MAX_FLIGHT_TICKS. heights are relative to the eye.
    private static double[] simulate(final float pitch, final double speed, final double horizDist) {
        final double pitchRad = Math.toRadians(pitch);
        double hv = speed * Math.cos(pitchRad);   // horizontal speed (yaw-independent magnitude)
        double yv = -speed * Math.sin(pitchRad);  // up is negative pitch -> positive yv
        double h = 0.0D;
        double y = LAUNCH_Y_OFFSET;

        for (int t = 1; t <= MAX_FLIGHT_TICKS; t++) {
            final double prevH = h;
            final double prevY = y;
            h += hv;
            y += yv;
            yv -= BOW_GRAVITY;
            hv *= AIR_DRAG;
            yv *= AIR_DRAG;

            if (h >= horizDist) {
                final double span = h - prevH;
                final double frac = span > 0.0D ? (horizDist - prevH) / span : 0.0D;
                final double hitY = prevY + (y - prevY) * frac;
                return new double[] { hitY, (t - 1) + frac };
            }
        }
        return null;
    }
}
