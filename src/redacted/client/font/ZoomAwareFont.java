package redacted.client.font;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import redacted.client.util.font.Font;

/**
 * Font wrapper that keeps text pixel-exact under a persistent GUI zoom
 * (the clickgui's user Scale slider).
 *
 * The MatHax/Envy pattern (CustomTextRenderer): re-rasterize glyphs at the
 * effective size (baseSize x eff) so the raster carries eff times more texel
 * density, then draw under the GUI's zoom matrix with a small RESIDUAL scale
 * of 1/eff. The two eff factors cancel on size (text grows with the panel
 * exactly like every rect), while texel density still lands 1:1 on device
 * pixels:
 *
 *   raster nominal  s*eff  -> drawn footprint s*eff/2 in local space
 *   residual 1/eff         -> footprint s/2 in zoomed (layout) space
 *   zoom matrix eff        -> s/2*eff device px on screen
 *   raster texels          -> s*eff*scaleFactor/2 device texels  (equal: 1:1)
 *
 * History, so neither failure mode comes back:
 * - Inverse zoom about the panel CENTRE: net glyph matrix became identity --
 *   text kept its unzoomed size while rects grew, layouts stopped fitting.
 * - No counter-scale at all: text grew as eff^2 (raster eff x matrix eff) --
 *   fine at 1.1, overflowing panels by 1.4, unreadably huge at 1.5.
 * The residual scale pivots on each draw's own origin, so positions are
 * untouched; only the glyph quads shrink back by exactly the over-raster
 * factor.
 *
 * Glyphs per effective size are cached by the normal per-size {@link Fonts}
 * cache; eff only changes when the user moves the slider, so cache growth is
 * bounded by the slider steps.
 *
 * When no zoom is active ({@link ZoomContext#eff} ~ 1, e.g. other GUIs that
 * share the module's font distribution, or the clickgui at scale 1) this
 * delegates 1:1 to the base font with zero overhead.
 */
public final class ZoomAwareFont extends Font {

    private final int baseSize;
    private final Weight weight;
    private final FontSupplier supplier;

    private Font base;         // resolved at the nominal size, used when eff ~ 1
    private int resolvedEffThousandths = -1;
    private Font zoomed;       // resolved at round(baseSize * eff), drawn with a 1/eff residual scale

    public interface FontSupplier {
        Font get(int size, Weight weight);
    }

    public ZoomAwareFont(final int size, final Weight weight, final FontSupplier supplier) {
        this.baseSize = size;
        this.weight = weight;
        this.supplier = supplier;
        this.base = supplier.get(size, weight);
    }

    private Font effective() {
        final double eff = ZoomContext.eff;
        if (Math.abs(eff - 1.0) < 0.02) {
            return this.base;
        }
        final int key = (int) Math.round(eff * 1000.0);
        if (key != this.resolvedEffThousandths) {
            this.resolvedEffThousandths = key;
            this.zoomed = this.supplier.get(Math.max(8, (int) Math.round(this.baseSize * eff)), this.weight);
        }
        return this.zoomed;
    }

    private boolean zoomed() {
        return ZoomContext.active && Math.abs(ZoomContext.eff - 1.0) >= 0.02;
    }

    /**
     * Draws with the dense raster counter-scaled by 1/eff about the draw origin.
     * The pivot translation is per-call (around this string's own x/y), NOT a global
     * inverse about the panel centre -- positions in layout space are preserved and
     * only glyph quads shrink by the over-raster factor.
     */
    private int drawResidual(final String text, final double x, final double y, final DrawCall call) {
        final double eff = ZoomContext.eff;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0.0);
        GL11.glScaled(1.0 / eff, 1.0 / eff, 1.0);
        final int out = call.run(this.effective(), 0.0, 0.0);
        GL11.glPopMatrix();
        return out;
    }

    private interface DrawCall {
        int run(Font font, double x, double y);
    }

    @Override
    public int draw(final String text, final double x, final double y, final int color, final boolean dropShadow) {
        if (!zoomed()) {
            return this.base.draw(text, x, y, color, dropShadow);
        }
        return this.drawResidual(text, x, y, (f, px, py) -> f.draw(text, px, py, color, dropShadow));
    }

    @Override
    public int draw(final String text, final double x, final double y, final int color) {
        if (!zoomed()) {
            return this.base.draw(text, x, y, color);
        }
        return this.drawResidual(text, x, y, (f, px, py) -> f.draw(text, px, py, color));
    }

    @Override
    public int drawWithShadow(final String text, final double x, final double y, final int color) {
        if (!zoomed()) {
            return this.base.drawWithShadow(text, x, y, color);
        }
        return this.drawResidual(text, x, y, (f, px, py) -> f.drawWithShadow(text, px, py, color));
    }

    @Override
    public int drawCentered(final String text, final double x, final double y, final int color) {
        if (!zoomed()) {
            return this.base.drawCentered(text, x, y, color);
        }
        // centre offset computed in LAYOUT space (effective width / eff == base width),
        // then the residual draw runs from that origin
        final double startX = x - this.width(text) / 2.0;
        return this.drawResidual(text, startX, y, (f, px, py) -> f.draw(text, px, py, color));
    }

    @Override
    public int drawRight(final String text, final double x, final double y, final int color) {
        if (!zoomed()) {
            return this.base.drawRight(text, x, y, color);
        }
        final double startX = x - this.width(text);
        return this.drawResidual(text, startX, y, (f, px, py) -> f.draw(text, px, py, color));
    }

    @Override
    public void drawCharacter(final char character, final int x, final int y, final java.awt.Color color) {
        if (!zoomed()) {
            this.base.drawCharacter(character, x, y, color);
            return;
        }
        final double eff = ZoomContext.eff;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0.0);
        GL11.glScaled(1.0 / eff, 1.0 / eff, 1.0);
        this.effective().drawCharacter(character, 0, 0, color);
        GL11.glPopMatrix();
    }

    /**
     * Width in the caller's (zoomed layout) coordinate space: the effective raster is
     * counter-scaled by 1/eff when drawn, so its layout footprint is width/eff.
     */
    @Override
    public int width(final String text) {
        if (!zoomed()) {
            return this.base.width(text);
        }
        return (int) Math.round(this.effective().width(text) / ZoomContext.eff);
    }

    /** Height in the caller's (zoomed layout) space, consistent with {@link #width(String)}. */
    @Override
    public float height() {
        if (!zoomed()) {
            return this.base.height();
        }
        return (float) (this.effective().height() / ZoomContext.eff);
    }
}
