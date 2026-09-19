package redacted.client.util.optimization;

import java.util.function.BooleanSupplier;

/* EntityCulling port: IRaytracingCache (meldexun.entityculling.util.raytracing). */
interface IRaytracingCache {

    boolean getOrSetCachedValue(int x, int y, int z, BooleanSupplier function);

    void clearCache();

}
