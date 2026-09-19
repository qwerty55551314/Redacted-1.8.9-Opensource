package redacted.client.util.optimization;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/*
 * EntityCulling port: Int2BoolMap (meldexun.entityculling.util.raytracing).
 * Fastutil-free open-addressing int->boolean map (fastutil HashCommon.mix inlined).
 * Keys are packed camera-relative block coords (10 bits per axis, never negative),
 * so Integer.MIN_VALUE is a safe "empty" sentinel. Only used by RaytracingMapCache,
 * cleared every culling cycle.
 */
class Int2BoolMap {

    private static final int DEFAULT_INITIAL_SIZE = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75F;
    private static final int EMPTY = Integer.MIN_VALUE;

    private int[] key;
    private boolean[] value;
    private int mask;
    private int size;
    private int maxFill;
    private final float f;

    Int2BoolMap() {
        this(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    Int2BoolMap(int expected, float f) {
        this.f = f;
        int n = arraySize(expected, f);
        this.key = new int[n];
        Arrays.fill(this.key, EMPTY);
        this.value = new boolean[n];
        this.mask = n - 1;
        this.maxFill = maxFill(n, f);
    }

    public boolean computeIfAbsent(final int k, final BooleanSupplier mappingFunction) {
        final int pos = find(k);
        if (pos >= 0) {
            return this.value[pos];
        }
        final boolean newValue = mappingFunction.getAsBoolean();
        insert(-pos - 1, k, newValue);
        return newValue;
    }

    public void clear() {
        Arrays.fill(this.key, EMPTY);
        Arrays.fill(this.value, false);
        this.size = 0;
    }

    private int find(final int k) {
        int curr;
        final int[] key = this.key;
        int pos;
        if ((curr = key[pos = mix(k) & this.mask]) == EMPTY) {
            return -(pos + 1);
        }
        if (k == curr) {
            return pos;
        }
        while (true) {
            if ((curr = key[pos = pos + 1 & this.mask]) == EMPTY) {
                return -(pos + 1);
            }
            if (k == curr) {
                return pos;
            }
        }
    }

    private void insert(final int pos, final int k, final boolean v) {
        this.key[pos] = k;
        this.value[pos] = v;
        if (this.size++ >= this.maxFill) {
            rehash(arraySize(this.size + 1, this.f));
        }
    }

    private void rehash(final int newN) {
        int[] key = this.key;
        boolean[] value = this.value;
        int mask = newN - 1;
        int[] newKey = new int[newN];
        Arrays.fill(newKey, EMPTY);
        boolean[] newValue = new boolean[newN];
        for (int i = key.length; i-- != 0; ) {
            int curr = key[i];
            if (curr != EMPTY) {
                int newPos = mix(curr) & mask;
                while (newKey[newPos] != EMPTY) {
                    newPos = newPos + 1 & mask;
                }
                newKey[newPos] = curr;
                newValue[newPos] = value[i];
            }
        }
        this.key = newKey;
        this.value = newValue;
        this.mask = mask;
        this.maxFill = maxFill(newN, this.f);
    }

    /* fastutil HashCommon.mix(int) */
    private static int mix(int x) {
        x = (x ^ (x >>> 16)) * -2048144789;
        x = (x ^ (x >>> 16)) * -1028477387;
        return x ^ (x >>> 16);
    }

    private static int arraySize(final int expected, final float f) {
        long s = Math.max(2L, nextPowerOfTwo((long) Math.ceil(expected / f)));
        if (s > 1073741824L) {
            throw new IllegalStateException("Too large (" + expected + " expected elements with load factor " + f + ")");
        }
        return (int) s;
    }

    private static int maxFill(final int n, final float f) {
        return Math.min((int) Math.ceil(n * f), n - 1);
    }

    private static long nextPowerOfTwo(long x) {
        if (x == 0L) {
            return 1L;
        }
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return (x | x >> 32) + 1;
    }

}
