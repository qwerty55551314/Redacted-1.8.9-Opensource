package redacted.client.util.optimization;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/*
 * EntityCulling port: RaytracingEngine (meldexun.entityculling.util.raytracing).
 * DDA voxel raytrace from the camera to a target block; "threshold" gives an
 * early-out (a target closer than the threshold counts as visible without
 * tracing). Result cache is keyed camera-relative and cleared every cycle;
 * opacity cache is a fixed 25x25x25-chunk block array (cacheSize = 12).
 * Pure CPU, safe to run off the render thread.
 */
public class RaytracingEngine {

    @FunctionalInterface
    public interface IntIntInt2BooleanFunction {
        boolean apply(int x, int y, int z);
    }

    private double camX;
    private double camY;
    private double camZ;
    private int camBlockX;
    private int camBlockY;
    private int camBlockZ;
    private final IRaytracingCache resultCache;
    private final IRaytracingCache opacityCache;
    private final IntIntInt2BooleanFunction isOpaqueFunction;
    private final IntSupplier renderDistSupplier;
    private final DoubleSupplier distanceLimitAdderSupplier;

    /* reusable arg holders so the BooleanSupplier adapters allocate once */
    private double argX, argY, argZ, argD;
    private final BooleanSupplier raytraceUncachedThresholdSupplier;
    private final BooleanSupplier isOpaqueSupplier;

    public RaytracingEngine(int cacheSize, IntIntInt2BooleanFunction isOpaqueFunction, IntSupplier renderDistSupplier, DoubleSupplier distanceLimitAdderSupplier) {
        this.resultCache = new RaytracingMapCache();
        this.opacityCache = new RaytracingArrayCache(cacheSize);
        this.isOpaqueFunction = isOpaqueFunction;
        this.renderDistSupplier = renderDistSupplier;
        this.distanceLimitAdderSupplier = distanceLimitAdderSupplier;
        this.raytraceUncachedThresholdSupplier = () -> this.raytraceUncachedThreshold(this.argX, this.argY, this.argZ, this.argD);
        this.isOpaqueSupplier = () -> this.isOpaqueFunction.apply((int) this.argX, (int) this.argY, (int) this.argZ);
    }

    public void setup(double x, double y, double z) {
        this.camX = x;
        this.camY = y;
        this.camZ = z;
        this.camBlockX = MathUtil.floor(x);
        this.camBlockY = MathUtil.floor(y);
        this.camBlockZ = MathUtil.floor(z);
    }

    public void clearCache() {
        this.resultCache.clearCache();
        this.opacityCache.clearCache();
    }

    public boolean raytraceCachedThreshold(int endX, int endY, int endZ, double threshold) {
        if (this.isTooFarAway(endX, endY, endZ)) {
            return true;
        }
        this.argX = endX;
        this.argY = endY;
        this.argZ = endZ;
        this.argD = threshold;
        return this.resultCache.getOrSetCachedValue(endX - this.camBlockX, endY - this.camBlockY, endZ - this.camBlockZ, this.raytraceUncachedThresholdSupplier);
    }

    public boolean raytraceUncachedThreshold(double endX, double endY, double endZ, double threshold) {
        if (this.isTooFarAway(endX, endY, endZ)) {
            return true;
        }
        return this.raytraceThreshold(this.camX, this.camY, this.camZ, endX, endY, endZ, threshold);
    }

    private boolean isTooFarAway(double x, double y, double z) {
        double distSqr = MathUtil.distSqr(this.camX, this.camY, this.camZ, x, y, z);
        double maxDist = (this.renderDistSupplier.getAsInt() << 4) + this.distanceLimitAdderSupplier.getAsDouble();
        return distSqr > maxDist * maxDist;
    }

    private boolean raytraceThreshold(double startX, double startY, double startZ, double endX, double endY, double endZ, double threshold) {
        if (threshold <= 0.0D) {
            return this.raytrace(startX, startY, startZ, endX, endY, endZ);
        }

        double dirX = endX - startX;
        double dirY = endY - startY;
        double dirZ = endZ - startZ;

        if (dirX * dirX + dirY * dirY + dirZ * dirZ <= threshold * threshold) {
            return true;
        }

        int x = MathUtil.floor(startX);
        int y = MathUtil.floor(startY);
        int z = MathUtil.floor(startZ);
        int incX = MathUtil.signum(dirX);
        int incY = MathUtil.signum(dirY);
        int incZ = MathUtil.signum(dirZ);
        double dx = incX == 0 ? Double.MAX_VALUE : incX / dirX;
        double dy = incY == 0 ? Double.MAX_VALUE : incY / dirY;
        double dz = incZ == 0 ? Double.MAX_VALUE : incZ / dirZ;
        double percentX = dx * (incX > 0 ? 1.0D - MathUtil.frac(startX) : MathUtil.frac(startX));
        double percentY = dy * (incY > 0 ? 1.0D - MathUtil.frac(startY) : MathUtil.frac(startY));
        double percentZ = dz * (incZ > 0 ? 1.0D - MathUtil.frac(startZ) : MathUtil.frac(startZ));
        Axis axis;

        if (this.isOpaque(x, y, z)) {
            double d1 = Math.min(Math.min(Math.min(percentX, percentY), percentZ), 1.0D);
            double nextHitX = startX + dirX * d1;
            double nextHitY = startY + dirY * d1;
            double nextHitZ = startZ + dirZ * d1;

            threshold -= MathUtil.dist(startX, startY, startZ, nextHitX, nextHitY, nextHitZ);
            if (threshold <= 0.0D) {
                return false;
            }
        }

        while (percentX <= 1.0D || percentY <= 1.0D || percentZ <= 1.0D) {
            if (percentX < percentY) {
                if (percentX < percentZ) {
                    x += incX;
                    percentX += dx;
                    axis = Axis.X;
                } else {
                    z += incZ;
                    percentZ += dz;
                    axis = Axis.Z;
                }
            } else if (percentY < percentZ) {
                y += incY;
                percentY += dy;
                axis = Axis.Y;
            } else {
                z += incZ;
                percentZ += dz;
                axis = Axis.Z;
            }

            if (this.isOpaque(x, y, z)) {
                double d = Math.min(axis != Axis.X ? (axis != Axis.Y ? percentZ - dz : percentY - dy) : percentX - dx, 1.0D);
                double hitX = startX + dirX * d;
                double hitY = startY + dirY * d;
                double hitZ = startZ + dirZ * d;

                double d1 = Math.min(Math.min(Math.min(percentX, percentY), percentZ), 1.0D);
                double nextHitX = startX + dirX * d1;
                double nextHitY = startY + dirY * d1;
                double nextHitZ = startZ + dirZ * d1;

                threshold -= MathUtil.dist(hitX, hitY, hitZ, nextHitX, nextHitY, nextHitZ);
                if (threshold <= 0.0D) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean raytrace(double startX, double startY, double startZ, double endX, double endY, double endZ) {
        int x = MathUtil.floor(startX);
        int y = MathUtil.floor(startY);
        int z = MathUtil.floor(startZ);

        if (this.isOpaque(x, y, z)) {
            return false;
        }

        double dirX = endX - startX;
        double dirY = endY - startY;
        double dirZ = endZ - startZ;
        int incX = MathUtil.signum(dirX);
        int incY = MathUtil.signum(dirY);
        int incZ = MathUtil.signum(dirZ);
        double dx = incX == 0 ? Double.MAX_VALUE : incX / dirX;
        double dy = incY == 0 ? Double.MAX_VALUE : incY / dirY;
        double dz = incZ == 0 ? Double.MAX_VALUE : incZ / dirZ;
        double percentX = dx * (incX > 0 ? 1.0D - MathUtil.frac(startX) : MathUtil.frac(startX));
        double percentY = dy * (incY > 0 ? 1.0D - MathUtil.frac(startY) : MathUtil.frac(startY));
        double percentZ = dz * (incZ > 0 ? 1.0D - MathUtil.frac(startZ) : MathUtil.frac(startZ));

        while (percentX <= 1.0D || percentY <= 1.0D || percentZ <= 1.0D) {
            if (percentX < percentY) {
                if (percentX < percentZ) {
                    x += incX;
                    percentX += dx;
                } else {
                    z += incZ;
                    percentZ += dz;
                }
            } else if (percentY < percentZ) {
                y += incY;
                percentY += dy;
            } else {
                z += incZ;
                percentZ += dz;
            }

            if (this.isOpaque(x, y, z)) {
                return false;
            }
        }

        return true;
    }

    private boolean isOpaque(int x, int y, int z) {
        this.argX = x;
        this.argY = y;
        this.argZ = z;
        return this.opacityCache.getOrSetCachedValue(x - this.camBlockX, y - this.camBlockY, z - this.camBlockZ, this.isOpaqueSupplier);
    }

}
