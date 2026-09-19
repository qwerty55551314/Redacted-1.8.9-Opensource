package redacted.client.util.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

import java.awt.*;

public class ColorUtil {
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static Color withAlpha(Color color, int alpha) {
        if (alpha == color.getAlpha()) return color;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static void glColor(int hex) {
        GlStateManager.color(
                (hex >> 16 & 0xFF) / 255.0F,
                (hex >> 8 & 0xFF) / 255.0F,
                (hex & 0xFF) / 255.0F,
                (hex >> 24 & 0xFF) / 255.0F
        );
    }

    // returns {r, g, b, a} in 0..1; use when you need the channels for something other than glColor4f (e.g. a per-vertex gradient)
    public static float[] rgba(int hex) {
        return new float[] {
                (hex >> 16 & 0xFF) / 255.0F,
                (hex >> 8 & 0xFF) / 255.0F,
                (hex & 0xFF) / 255.0F,
                (hex >> 24 & 0xFF) / 255.0F
        };
    }

    public static void glColor(Color color) {
        GlStateManager.color(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
    }

    public static int withAlpha(int color, float alpha) {
        int baseAlpha = (color >> 24) & 0xFF;
        int scaled = Math.min(255, (int) (baseAlpha * alpha));
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    // rainbow that cycles ~once a second; shared by the ESP "Sync" color modes
    public static Color sync() {
        return Color.getHSBColor((System.currentTimeMillis() % 3600L) / 3600.0F, 0.6F, 1.0F);
    }

    // random fixed sat/brightness hue; for the "Random" per-entity color modes
    public static Color randomHue() {
        return Color.getHSBColor((float) Math.random(), 0.6F, 1.0F);
    }

    public static int healthColor(float health, float maxHealth) {
        float ratio = healthRatio(health, maxHealth);
        int r = (int) (255 * (1F - ratio));
        int g = (int) (255 * ratio);
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    public static float healthRatio(float health, float maxHealth) {
        return maxHealth > 0F ? MathHelper.clamp_float(health / maxHealth, 0F, 1F) : 0F;
    }

    public static Color lerpColor(Color a, Color b, float t) {
        if (t <= 0F) return a;
        if (t >= 1F) return b;
        return new Color(
                lerpInt(a.getRed(), b.getRed(), t),
                lerpInt(a.getGreen(), b.getGreen(), t),
                lerpInt(a.getBlue(), b.getBlue(), t),
                lerpInt(a.getAlpha(), b.getAlpha(), t)
        );
    }

    // int-packed twin of the Color overload; lerps all four channels
    public static int lerpColor(int from, int to, float t) {
        if (t <= 0F) return from;
        if (t >= 1F) return to;
        return (lerpInt(from >>> 24, to >>> 24, t) << 24)
                | (lerpInt(from >> 16 & 0xFF, to >> 16 & 0xFF, t) << 16)
                | (lerpInt(from >> 8 & 0xFF, to >> 8 & 0xFF, t) << 8)
                | lerpInt(from & 0xFF, to & 0xFF, t);
    }

    public static int lerpInt(int from, int to, float t) {
        return Math.max(0, Math.min(255, (int) (from + (to - from) * t)));
    }

    // below this straight RGB distance two colors read as "the same" at a glance (max distance is ~441, black vs white)
    private static final double SIMILAR_THRESHOLD = 60.0;

    // RGB-only distance (ignores alpha); 0 = identical
    public static double colorDistance(Color a, Color b) {
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    // true when two colors are close enough you couldn't tell them apart (e.g. team vs enemy ESP)
    public static boolean colorsTooSimilar(Color a, Color b) {
        return colorDistance(a, b) < SIMILAR_THRESHOLD;
    }
}
