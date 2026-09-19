package baritone.cache;

/**
 * Gate for Baritone's noisy cache/save logging ("Baritone world data dir",
 * "Saving region ...", "World save took ...", etc). Quiet by default; the client
 * can flip {@link #setQuiet(boolean)} from a debug setting if the noise is wanted.
 */
public final class CacheLog {
    private static volatile boolean quiet = true;

    private CacheLog() {
    }

    public static void setQuiet(final boolean quiet) {
        CacheLog.quiet = quiet;
    }

    public static boolean isQuiet() {
        return quiet;
    }

    public static void print(final String message) {
        if (!quiet) {
            System.out.println(message);
        }
    }
}
