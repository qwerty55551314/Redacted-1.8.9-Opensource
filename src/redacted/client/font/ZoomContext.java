package redacted.client.font;

/**
 * Per-frame zoom state for {@link ZoomAwareFont}. The zooming GUI (the redacted
 * clickgui) sets this each frame before drawing its content and clears it after;
 * everywhere else it stays inactive and zoom-aware fonts delegate 1:1.
 */
public final class ZoomContext {

    /** false = no zoom compensation anywhere (every other GUI, tooltip passes, etc.) */
    public static volatile boolean active;

    /** the combined matrix zoom currently applied to the GUI content (open anim * user scale) */
    public static volatile double eff = 1.0;

    /** the center the zoom is applied about, in GUI coordinates */
    public static volatile double centerX;
    public static volatile double centerY;

    private ZoomContext() {
    }

    public static void set(final double centerX, final double centerY, final double eff) {
        ZoomContext.centerX = centerX;
        ZoomContext.centerY = centerY;
        ZoomContext.eff = eff;
        ZoomContext.active = true;
    }

    public static void clear() {
        ZoomContext.active = false;
        ZoomContext.eff = 1.0;
    }
}
