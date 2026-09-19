package redacted.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import redacted.client.font.ZoomContext;

import java.util.Collections;
import java.util.Set;

public class RenderUtil {

    // fine arc step: small pills/sliders render smooth instead of faceted. combined with
    // device-pixel snapping in the draw methods above, corners are crisp AND round.
    private static final double ARC_STEP = 1;

    // unit-circle tables (identical double values to the Math.sin/cos calls they
    // replace, just computed once): rounded rects emit 364 vertices and circles 361
    // per call, and HUD/nametag panels call them every frame
    private static final double[] QUARTER_SIN = new double[91];   // sin(0..90 deg)
    private static final double[] QUARTER_COS = new double[91];   // cos(0..90 deg)
    private static final double[] SECOND_SIN = new double[91];    // sin(90..180 deg)
    private static final double[] SECOND_COS = new double[91];    // cos(90..180 deg)
    private static final double[] CIRCLE_SIN = new double[361];   // sin(0..360 deg)
    private static final double[] CIRCLE_COS = new double[361];   // cos(0..360 deg)

    static {
        for (int i = 0; i <= 360; ++i) {
            final double rad = Math.toRadians(i);
            CIRCLE_SIN[i] = Math.sin(rad);
            CIRCLE_COS[i] = Math.cos(rad);
        }
        for (int i = 0; i <= 90; ++i) {
            final double rad = Math.toRadians(i);
            QUARTER_SIN[i] = Math.sin(rad);
            QUARTER_COS[i] = Math.cos(rad);
            final double rad2 = Math.toRadians(90 + i);
            SECOND_SIN[i] = Math.sin(rad2);
            SECOND_COS[i] = Math.cos(rad2);
        }
    }

    public static void drawRect(double x, double y, double width, double height, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        ColorUtil.glColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y + height);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x + width, y);
        GL11.glEnd();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawGradientRect(double x, double y, double width, double height, int colorLeft, int colorRight) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);

        final float[] l = ColorUtil.rgba(colorLeft);
        final float[] r = ColorUtil.rgba(colorRight);

        wr.pos(x, y, 0.0D).color(l[0], l[1], l[2], l[3]).endVertex();
        wr.pos(x, y + height, 0.0D).color(l[0], l[1], l[2], l[3]).endVertex();
        wr.pos(x + width, y + height, 0.0D).color(r[0], r[1], r[2], r[3]).endVertex();
        wr.pos(x + width, y, 0.0D).color(r[0], r[1], r[2], r[3]).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    public static void drawGradientRectVertical(double x, double y, double width, double height, int colorTop, int colorBottom) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);

        final float[] t = ColorUtil.rgba(colorTop);
        final float[] b = ColorUtil.rgba(colorBottom);

        wr.pos(x, y, 0.0D).color(t[0], t[1], t[2], t[3]).endVertex();
        wr.pos(x, y + height, 0.0D).color(b[0], b[1], b[2], b[3]).endVertex();
        wr.pos(x + width, y + height, 0.0D).color(b[0], b[1], b[2], b[3]).endVertex();
        wr.pos(x + width, y, 0.0D).color(t[0], t[1], t[2], t[3]).endVertex();
        tessellator.draw();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    public static void drawRoundedRect(double x, double y, double width, double height, double radius, int color) {
        if (width <= 0 || height <= 0) return;
        radius = clampRadius(radius, width, height);
        if (radius <= 0) { drawRect(x, y, width, height, color); return; }

        // snap to the device pixel grid so fractional gui positions don't smear the
        // edge (the same fix that made the cog crisp); fine arc steps kill any facets
        final int sf = cachedScaleFactor();
        final double sx = Math.round(x * sf) / (double) sf;
        final double sy = Math.round(y * sf) / (double) sf;
        final double sw = Math.round((x + width) * sf) / (double) sf - sx;
        final double sh = Math.round((y + height) * sf) / (double) sf - sy;
        if (sw <= 0 || sh <= 0) { drawRect(x, y, width, height, color); return; }
        radius = clampRadius(radius, sw, sh);

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        final float[] c = ColorUtil.rgba(color);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_POLYGON, DefaultVertexFormats.POSITION_COLOR);

        emitRoundedCorners(wr, sx, sy, sw, sh, radius, c);

        tessellator.draw();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawRoundedOutline(double x, double y, double width, double height, double radius, float lineWidth, int color) {
        if (width <= 0 || height <= 0) return;
        radius = clampRadius(radius, width, height);
        if (radius <= 0) {
            drawRectOutline(x, y, width, height, lineWidth, color);
            return;
        }

        // NOTE: deliberately NOT device-snapped -- the Redacted clickgui draws outlines
        // under a zoom matrix, so snapping here (in pre-zoom space) shifts the highlight
        // off the module fill and looks worse. The fill's snap is fine (fills are the
        // visible feature), outlines ride the matrix and stay put.
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth);

        final float[] c = ColorUtil.rgba(color);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);

        emitRoundedCorners(wr, x, y, width, height, radius, c);

        tessellator.draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    public static void drawFilledCircle(double cx, double cy, double radius, int color) {
        if (radius <= 0) return;

        // Exhibition's drawFullCircle: draw at 2x logical size under a 0.5 scale, so the
        // fan's vertices land on the full device-resolution grid and small thumbs come
        // out as true circles instead of faceted or lopsided polygons. Uses
        // GlStateManager.scale (the same transform the rest of the client relies on).
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.scale(0.5, 0.5, 1.0);
        final double sx = cx * 2.0, sy = cy * 2.0, sr = radius * 2.0;
        ColorUtil.glColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(sx, sy);
        for (int i = 0; i <= 360; i += 1) {
            GL11.glVertex2d(sx + CIRCLE_SIN[i] * sr, sy + CIRCLE_COS[i] * sr);
        }
        GL11.glEnd();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawCircleOutline(double cx, double cy, double radius, float thickness, int color) {
        if (radius <= 0) return;
        final int sf = cachedScaleFactor();
        final double sx = Math.round(cx * sf) / (double) sf;
        final double sy = Math.round(cy * sf) / (double) sf;
        final double sr = Math.round(radius * sf) / (double) sf;
        if (sr <= 0) return;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(thickness);
        ColorUtil.glColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 360; i += 2) {
            GL11.glVertex2d(sx + CIRCLE_SIN[i] * sr, sy + CIRCLE_COS[i] * sr);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // thin 2D line (scaled GUI space), current GL color
    public static void drawLine(double x1, double y1, double x2, double y2, float width, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        ColorUtil.glColor(color);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // checkmark tick (Pandaware's drawCheck geometry: a 3-point line strip scaled to the box size).
    // x/y are the tick's top-left anchor, size is the checkbox box size it should fit in.
    public static void drawTick(double x, double y, double size, float width, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        ColorUtil.glColor(color);
        GL11.glLineWidth(width);
        final double s = size / 12.0;
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex2d(x + 1.0 * s, y);
        GL11.glVertex2d(x + 3.0 * s, y + 3.5 * s);
        GL11.glVertex2d(x + 7.0 * s, y - 2.5 * s);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // filled 2D triangle (scaled GUI space) -- for the flat warning glyph
    public static void drawFilledTriangle(double x1, double y1, double x2, double y2, double x3, double y3, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        ColorUtil.glColor(color);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x3, y3);
        GL11.glEnd();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawIcon(ResourceLocation loc, double x, double y, double size) {
        drawIcon(loc, x, y, size, size, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    // colour-tinted icon (white texture * tint). unlike the plain overload this does NOT
    // reset to white, so callers can pass a theme tint (e.g. grey 0.72) for notifications
    public static void drawIcon(ResourceLocation loc, double x, double y, double size, float r, float g, float b, float a) {
        drawIcon(loc, x, y, size, size, r, g, b, a);
    }

    // non-square variant; all geometry snapped to whole device pixels
    public static void drawIcon(ResourceLocation loc, double x, double y, double w, double h, float r, float g, float b, float a) {
        // ZoomAwareFont treatment for icons: when the clickgui's zoom matrix is active
        // (Redacted theme's user Scale), draw the quad under a residual 1/eff counter-scale
        // about the icon's own origin so the texture is sampled at native density instead
        // of being resampled by the zoom matrix. Without this, small icons distort heavily
        // at low Scale (<1.6) and shimmer at fractional positions.
        final boolean zoomed = ZoomContext.active && Math.abs(ZoomContext.eff - 1.0) >= 0.02;
        if (zoomed) {
            final double eff = ZoomContext.eff;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, 0.0);
            GL11.glScaled(1.0 / eff, 1.0 / eff, 1.0);
            drawIconUnscaled(loc, 0.0, 0.0, w, h, r, g, b, a);
            GL11.glPopMatrix();
            return;
        }
        drawIconUnscaled(loc, x, y, w, h, r, g, b, a);
    }

    /** raw icon quad: no zoom compensation, just texture + device-pixel snap */
    private static void drawIconUnscaled(ResourceLocation loc, double x, double y, double w, double h, float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Minecraft.getMinecraft().getTextureManager().bindTexture(loc);
        ensureSmoothSampling(loc);
        // whole-pixel geometry on the DEVICE grid: fractional positions give every
        // instance of the same icon a different sampling phase (the "every cog looks
        // different" effect), and a truncated size shaves the right/bottom edge
        final int sf = cachedScaleFactor();
        // size snapped to the device grid too, so the quad and the UV span the exact
        // same pixel count -- no stretching, no shaved edges on fractional gui scale
        final float sx = (float) (Math.round(x * sf) / (double) sf);
        final float sy = (float) (Math.round(y * sf) / (double) sf);
        final int pw = Math.max(1, (int) Math.round(Math.round(w * sf) / (double) sf));
        final int ph = Math.max(1, (int) Math.round(Math.round(h * sf) / (double) sf));
        Gui.drawModalRectWithCustomSizedTexture(sx, sy, 0, 0, pw, ph, (float) pw, (float) ph);
    }

    // GUI scale factor, cached briefly -- drawIcon runs a handful of times per frame and
    // allocating a ScaledResolution each time is waste for a value that rarely changes
    private static int iconScaleFactor = 1;
    private static long iconScaleFactorAt;
    private static int cachedScaleFactor() {
        final long now = System.currentTimeMillis();
        if (now - iconScaleFactorAt < 200L) {
            return iconScaleFactor;
        }
        try {
            iconScaleFactor = Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
        } catch (final Throwable t) {
            iconScaleFactor = 1;
        }
        iconScaleFactorAt = now;
        return iconScaleFactor;
    }

    /**
     * Icons are authored at high resolution (384-1000px) and drawn at 10-14 GUI px, so they are
     * ALWAYS heavily minified. The vanilla SimpleTexture upload leaves them at GL_NEAREST with
     * no mipmaps, which is the "zigzaggy edges" look. Switch the bound texture to trilinear
     * filtering and build the mipmap chain once per texture. A small negative LOD bias keeps
     * trilinear from over-softening (the "slightly uncanny" look) by favoring the sharper mip
     * level while still averaging at extreme downscales.
     */
    private static final Set<ResourceLocation> MIPMAPPED_TEXTURES = Collections.newSetFromMap(new java.util.HashMap<>());
    private static void ensureSmoothSampling(final ResourceLocation loc) {
        final boolean first = MIPMAPPED_TEXTURES.add(loc);
        // texture parameters live on the texture object and persist -- issuing them
        // was 4 redundant driver calls per icon per frame
        if (!first) return;
        final boolean mipmaps = GLUtil.isMipmapsSupported();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (mipmaps) {
            GL14.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, -0.4f);
        }
        if (mipmaps) {
            GLUtil.generateMipmap();
        }
    }

    /**
     * Resource reloads replace the underlying GL texture objects, so their cached
     * smooth-sampling state must be re-applied on the next draw. Called from
     * {@link net.minecraft.client.Minecraft#refreshResources()}.
     */
    public static void resetIconSamplingCache() {
        MIPMAPPED_TEXTURES.clear();
    }

    // shared corner arc for rounded rect + outline; caller picks the GL primitive.
    // vertex order/counts are identical to the original per-vertex trig loops
    private static void emitRoundedCorners(WorldRenderer wr, double x, double y, double width, double height, double radius, float[] c) {
        // top-left
        for (int i = 0; i <= 90; i += ARC_STEP) {
            wr.pos(x + radius + QUARTER_SIN[i] * (-radius),
                   y + radius + QUARTER_COS[i] * (-radius), 0)
               .color(c[0], c[1], c[2], c[3]).endVertex();
        }
        // bottom-left
        for (int i = 0; i <= 90; i += ARC_STEP) {
            wr.pos(x + radius + SECOND_SIN[i] * (-radius),
                   y + height - radius + SECOND_COS[i] * (-radius), 0)
               .color(c[0], c[1], c[2], c[3]).endVertex();
        }
        // bottom-right
        for (int i = 0; i <= 90; i += ARC_STEP) {
            wr.pos(x + width - radius + QUARTER_SIN[i] * radius,
                   y + height - radius + QUARTER_COS[i] * radius, 0)
               .color(c[0], c[1], c[2], c[3]).endVertex();
        }
        // top-right
        for (int i = 0; i <= 90; i += ARC_STEP) {
            wr.pos(x + width - radius + SECOND_SIN[i] * radius,
                   y + radius + SECOND_COS[i] * radius, 0)
               .color(c[0], c[1], c[2], c[3]).endVertex();
        }
    }

    private static double clampRadius(double r, double w, double h) {
        if (r <= 0 || w <= 0 || h <= 0) return 0;
        return Math.min(r, Math.min(w, h) / 2.0);
    }

    private static void drawRectOutline(double x, double y, double width, double height, float lineWidth, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth);
        ColorUtil.glColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y + height);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x + width, y);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * celeritas (Embeddium) ColorMixer port: mixes two packed 32-bit colors by ratio using
     * integer bitwise math (much faster than per-channel float lerp). Channel order of the
     * packed ints is preserved (works for ARGB and RGBA alike).
     */
    private static final int CHANNEL_MASK = 0x00FF00FF;

    public static int mixColor(int aColor, int bColor, float ratio) {
        int aRatio = (int) (256 * ratio);
        int bRatio = 256 - aRatio;
        int a1 = (aColor >> 0) & CHANNEL_MASK;
        int b1 = (bColor >> 0) & CHANNEL_MASK;
        int a2 = (aColor >> 8) & CHANNEL_MASK;
        int b2 = (bColor >> 8) & CHANNEL_MASK;
        int c1 = (((a1 * aRatio) + (b1 * bRatio)) >> 8) & CHANNEL_MASK;
        int c2 = (((a2 * aRatio) + (b2 * bRatio)) >> 8) & CHANNEL_MASK;
        return (c1 << 0) | (c2 << 8);
    }
}
