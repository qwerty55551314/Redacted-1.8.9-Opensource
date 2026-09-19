package redacted.client.util;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Randoms {

    // inclusive on both ends; normalises so callers can pass a RangeValue min/max in either order
    public static int between(final Random rnd, final int a, final int b) {
        final int lo = Math.min(a, b);
        final int hi = Math.max(a, b);
        return lo == hi ? lo : lo + rnd.nextInt(hi - lo + 1);
    }

    public static double between(final double a, final double b) {
        final double lo = Math.min(a, b);
        final double hi = Math.max(a, b);
        return lo == hi ? lo : ThreadLocalRandom.current().nextDouble(lo, hi);
    }

    // bell-shaped roll around the range midpoint (clamped to the range): a healthy std-dev keeps
    // Intave's DelayAnalyzer fed and the stream is never the flat uniform distribution that
    // Photon's statistical checks call "bot". normalises so either endpoint order works.
    public static double gaussian(final Random rnd, final double a, final double b) {
        final double lo = Math.min(a, b);
        final double hi = Math.max(a, b);
        if (lo == hi) return lo;
        final double mid = (lo + hi) / 2.0;
        // +-3 sigma spans the range, so the clamp below almost never bites (~0.3%). a wider spread
        // clamps hard and piles probability on the two endpoints -- two spikes in a delay histogram
        // is exactly the machine signature this is meant to avoid.
        final double spread = (hi - lo) / 6.0;
        final double d = mid + rnd.nextGaussian() * spread;
        return Math.max(lo, Math.min(hi, d));
    }
}
