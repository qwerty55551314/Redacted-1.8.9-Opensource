package celeritas;

/**
 * How many finished section meshes one {@code uploadChunks} call may upload. Port of Natron's
 * {@code NatronUploadBudget} - see that class for why this bounds the drain (heavier per-section
 * meshing than Angelica's, so results arrive in clumps).
 */
public final class CeleritasUploadBudget {

    private static final int CAP_MAPPED = 192;
    private static final int CAP_FALLBACK = 48;
    private static final int BACKLOG_DIVISOR = 4;

    private CeleritasUploadBudget() {
    }

    public static int capFor(int backlog) {
        final int base = CeleritasGlCaps.hasPersistentMapping() ? CAP_MAPPED : CAP_FALLBACK;
        return Math.max(base, backlog / BACKLOG_DIVISOR);
    }
}
