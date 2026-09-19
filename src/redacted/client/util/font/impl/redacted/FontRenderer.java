package redacted.client.util.font.impl.redacted;

import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.GLUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TTF glyph renderer. Every character owns one GL texture rasterized with AWT and is drawn
 * as an immediate-mode quad, scaled down by {@code SCALE / oversample}.
 *
 * Sharpness model (the fix for scaled-text blur AND per-glyph inconsistency):
 * - Glyphs rasterize at exactly DEVICE pixel resolution: nominal size x (GUI scale / 2),
 *   since a "size 16" font draws at 8 GUI px which is 8 x scaleFactor device px. At rest
 *   the texture is 1:1 texel-to-pixel -- no minification at all. (An earlier attempt
 *   rasterized at 2x+ device res and let the mip chain downscale; every glyph then sat at
 *   a fractional mip level that varied with its subpixel position, so neighboring glyphs
 *   in one word blurred by different amounts -- "some parts darker than others".)
 * - Uploads are trilinear (mipmapped), so the chain only engages when text is actually
 *   minified (draggable HUD scale < 1, billboard HUDs shrinking with distance).
 * - Draw origins AND the drop-shadow offset snap to whole device pixels. A half-device-pixel
 *   shadow offset renders as a ghosted second glyph ("saw double"), so the shadow shift is
 *   at least one full device pixel at every GUI scale.
 * - When the GUI scale factor changes (window resize, options), glyph sets are re-rasterized
 *   in place; the draw-space metrics stay identical.
 */
public class FontRenderer extends redacted.client.util.font.Font {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Color TRANSPARENT_COLOR = new Color(255, 255, 255, 0);
    private static final float SCALE = 0.5f;
    private static final float SCALE_INVERSE = 1 / SCALE;
    private static final char COLOR_INVOKER = '\u00A7';
    private static final int[] COLOR_CODES = new int[32];
    private static final int LATIN_MAX_AMOUNT = 256;
    private static final int INTERNATIONAL_MAX_AMOUNT = 65535;
    private static final int MARGIN_WIDTH = 4;
    private static final int MASK = 0xFF;
    // transparent rows between glyph rows inside an atlas page; keeps mip-level
    // linear filtering from blending ink across vertically adjacent glyphs
    private static final int ROW_GUTTER = 8;
    // glyphs per atlas page: the default (Latin) set is exactly one page
    private static final int PAGE_CAPACITY = 256;

    private final Font font;
    private final boolean fractionalMetrics;
    private final float fontHeight;
    private final FontCharacter[] defaultCharacters = new FontCharacter[LATIN_MAX_AMOUNT];
    private final FontCharacter[] internationalCharacters = new FontCharacter[INTERNATIONAL_MAX_AMOUNT];
    private final List<Integer> defaultPages = new ArrayList<Integer>();
    private final List<Integer> internationalPages = new ArrayList<Integer>();
    private boolean antialiasing = true, international = false;

    // raster px per nominal font px = scaleFactor / 2, so the raster is exactly device
    // pixels (nominal draw size is size/2 GUI px = size/2 * scaleFactor device px)
    private float oversample = 1.0f;
    private int buildScaleFactor = 1;

    private static float oversampleFor(final int scaleFactor) {
        return Math.max(0.5f, scaleFactor / 2.0f);
    }

    // GUI scale factor with a short cache: draw() runs per string per frame, and
    // allocating a ScaledResolution each time is pointless GC churn for a value
    // that only changes on window resize or in the options
    private static int cachedScaleFactor = 1;
    private static long cachedScaleAt;

    public FontRenderer(Font font, boolean fractionalMetrics, boolean antialiasing, boolean international) {
        calculateColorCodes();
        this.antialiasing = antialiasing;
        this.font = font;
        this.fractionalMetrics = fractionalMetrics;
        this.fontHeight = (float) (font.getStringBounds(ALPHABET, new FontRenderContext(new AffineTransform(), antialiasing, fractionalMetrics)).getHeight() / 2);
        this.buildScaleFactor = currentScaleFactor();
        this.oversample = oversampleFor(this.buildScaleFactor);
        this.fillCharacters(this.defaultCharacters, Font.PLAIN, this.defaultPages);
        this.international = international;

        if (this.international) {
            this.fillCharacters(this.internationalCharacters, Font.PLAIN, this.internationalPages);
        }
    }

    public static void calculateColorCodes() {
        for (int i = 0; i < 32; ++i) {
            final int amplifier = (i >> 3 & 1) * 85;
            int red = (i >> 2 & 1) * 170 + amplifier;
            int green = (i >> 1 & 1) * 170 + amplifier;
            int blue = (i & 1) * 170 + amplifier;
            if (i == 6) {
                red += 85;
            }
            if (i >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            COLOR_CODES[i] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        }
    }

    /** Legacy signature kept for compatibility: builds into a throwaway page list. */
    public void fillCharacters(final FontCharacter[] characters, final int style) {
        fillCharacters(characters, style, new ArrayList<Integer>());
    }

    /**
     * Rasterizes every glyph exactly as before (same AWT bounds, margins and hints --
     * byte-identical glyph images), then packs the glyph images into atlas pages of
     * {@link #PAGE_CAPACITY} characters and uploads one texture per page. Each
     * FontCharacter becomes a UV window into its page instead of owning a texture,
     * so a whole string draws with one bind and one batched quad pass.
     */
    public void fillCharacters(final FontCharacter[] characters, final int style, final List<Integer> pagesOut) {
        // rasterize at nominal size * oversample == exact device pixels, so at-rest text
        // is 1:1 texel-to-pixel instead of permanently sitting on a blurred mip level
        final Font font = this.font.deriveFont(style, this.font.getSize2D() * this.oversample);
        // margin stays in whole raster px (MARGIN_WIDTH=4 and oversample is k or k/2, so
        // this is always integral) -- the draw-space margin is unchanged at MARGIN_WIDTH/2
        final int margin = (int) (MARGIN_WIDTH * this.oversample);
        final BufferedImage fontImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D fontGraphics = (Graphics2D) fontImage.getGraphics();
        final FontMetrics fontMetrics = fontGraphics.getFontMetrics(font);

        for (int pageStart = 0; pageStart < characters.length; pageStart += PAGE_CAPACITY) {
            final int pageEnd = Math.min(pageStart + PAGE_CAPACITY, characters.length);
            final int count = pageEnd - pageStart;
            final int[] glyphWidths = new int[count];
            final int[] glyphHeights = new int[count];
            final BufferedImage[] glyphImages = new BufferedImage[count];

            long area = 0L;
            int maxGlyphWidth = 1;

            for (int i = 0; i < count; ++i) {
                final char character = (char) (pageStart + i);
                final Rectangle2D charRectangle = fontMetrics.getStringBounds(character + "", fontGraphics);

                final BufferedImage charImage = new BufferedImage(MathHelper.ceiling_float_int(
                        (float) charRectangle.getWidth()) + margin * 2, MathHelper.ceiling_float_int(
                        (float) charRectangle.getHeight()), BufferedImage.TYPE_INT_ARGB);

                final Graphics2D charGraphics = (Graphics2D) charImage.getGraphics();
                charGraphics.setFont(font);

                final int width = charImage.getWidth();
                final int height = charImage.getHeight();
                charGraphics.setColor(TRANSPARENT_COLOR);
                charGraphics.fillRect(0, 0, width, height);
                setRenderHints(charGraphics);
                charGraphics.drawString(character + "", margin, font.getSize());

                glyphImages[i] = charImage;
                glyphWidths[i] = width;
                glyphHeights[i] = height;
                area += (long) (width + 1) * (height + ROW_GUTTER);
                if (width > maxGlyphWidth) maxGlyphWidth = width;
            }

            // atlas width: power of two, at least sqrt(area) so rows pack compactly
            int atlasWidth = 64;
            while (atlasWidth < maxGlyphWidth || (long) atlasWidth * atlasWidth < area) atlasWidth <<= 1;

            // pack rows (next-fit); height is exact-fit (NPOT textures are fine -- the
            // per-glyph textures this replaces were NPOT as well)
            final int[] posX = new int[count];
            final int[] posY = new int[count];
            int cursorX = 0, cursorY = 0, rowHeight = 0;
            for (int i = 0; i < count; ++i) {
                final int w = glyphWidths[i];
                if (cursorX > 0 && cursorX + w > atlasWidth) {
                    cursorY += rowHeight + ROW_GUTTER;
                    cursorX = 0;
                    rowHeight = 0;
                }
                posX[i] = cursorX;
                posY[i] = cursorY;
                cursorX += w;
                if (glyphHeights[i] > rowHeight) rowHeight = glyphHeights[i];
            }
            final int atlasHeight = cursorY + rowHeight;

            final BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D atlasGraphics = (Graphics2D) atlas.getGraphics();
            for (int i = 0; i < count; ++i) {
                atlasGraphics.drawImage(glyphImages[i], posX[i], posY[i], null);
                glyphImages[i] = null;
            }
            atlasGraphics.dispose();

            final int pageTexture = GL11.glGenTextures();
            uploadTexture(pageTexture, atlas, atlasWidth, atlasHeight);
            pagesOut.add(pageTexture);

            final float texW = (float) atlasWidth;
            final float texH = (float) atlasHeight;
            for (int i = 0; i < count; ++i) {
                final float u0 = posX[i] / texW;
                final float v0 = posY[i] / texH;
                final float u1 = (posX[i] + glyphWidths[i]) / texW;
                final float v1 = (posY[i] + glyphHeights[i]) / texH;
                characters[pageStart + i] = new FontCharacter(pageTexture, u0, v0, u1, v1, glyphWidths[i], glyphHeights[i]);
            }
        }
    }

    /** Frees the current atlas pages and re-rasterizes at the new oversampling. */
    private void rebuildCharacters() {
        freeCharacters(this.defaultCharacters, this.defaultPages);
        freeCharacters(this.internationalCharacters, this.internationalPages);
        this.fillCharacters(this.defaultCharacters, Font.PLAIN, this.defaultPages);
        if (this.international) {
            this.fillCharacters(this.internationalCharacters, Font.PLAIN, this.internationalPages);
        }
    }

    private static void freeCharacters(final FontCharacter[] characters, final List<Integer> pages) {
        for (final Integer page : pages) {
            GL11.glDeleteTextures(page);
        }
        pages.clear();
        // drop stale UV entries immediately so a failed rebuild can't sample freed pages
        Arrays.fill(characters, null);
    }

    private static int currentScaleFactor() {
        final long now = System.currentTimeMillis();
        if (now - cachedScaleAt < 200L) {
            return cachedScaleFactor;
        }
        try {
            final Minecraft mc = Minecraft.getMinecraft();
            cachedScaleFactor = Math.max(1, new ScaledResolution(mc).getScaleFactor());
        } catch (final Throwable t) {
            cachedScaleFactor = 1;
        }
        cachedScaleAt = now;
        return cachedScaleFactor;
    }

    public void setRenderHints(final Graphics2D graphics) {
        graphics.setColor(Color.WHITE);
        if (antialiasing) {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    public void uploadTexture(final int texture, final BufferedImage image, final int width, final int height) {
        final int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
        final ByteBuffer byteBuffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int pixel = pixels[x + y * width];
                byteBuffer.put((byte) ((pixel >> 16) & MASK));
                byteBuffer.put((byte) ((pixel >> 8) & MASK));
                byteBuffer.put((byte) (pixel & MASK));
                byteBuffer.put((byte) ((pixel >> 24) & MASK));
            }
        }
        byteBuffer.flip();
        GlStateManager.bindTexture(texture);
        final boolean mipmaps = GLUtil.isMipmapsSupported();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (mipmaps) {
            // bias the mip selection one level toward sharp: at exact 1:1 the computed LOD is
            // 0, but driver LOD epsilon in compatibility contexts can nudge it positive and
            // blend toward mip 1 -- a constant slight blur. -1 keeps 1:1 text firmly on
            // level 0 while genuinely minified text still uses the chain.
            GL14.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, -1.0f);
        }
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, byteBuffer);
        if (mipmaps) {
            GLUtil.generateMipmap();
        }
    }

    public int draw(final String text, final double x, final double y, final int color) {
        return draw(text, x, y, color, false);
    }

    public int drawCentered(final String text, final double x, final double y, final int color) {
        return draw(text, x - (width(text) >> 1), y, color, false);
    }

    public int drawRight(String text, double x, double y, int color) {
        return draw(text, x - (width(text)), y, color, false);
    }

    public int drawWithShadow(final String text, final double x, final double y, final int color) {
        // No shadow pass. Rise (the renderer this file was ported from) shipped with its
        // offset shadow commented out for exactly the reasons we kept hitting: behind a
        // thin antialiased TTF glyph there is no offset that works -- tight lands inside
        // the AA halo and reads as blur, loose reads as a doubled glyph. Vanilla only
        // gets away with shadows because its font is chunky pixel art. Shadowed call
        // sites keep working; they just render a single crisp pass.
        return draw(text, x, y, color, false);
    }

    public int draw(String text, double x, double y, final int color, final boolean shadow) {
        if (text == null) {
            return 0;
        }

        // re-rasterize when the GUI scale changed since build (window resize / options);
        // rare, so the rebuild cost is fine and the metrics stay identical
        final int scaleFactor = currentScaleFactor();
        if (scaleFactor != this.buildScaleFactor) {
            this.buildScaleFactor = scaleFactor;
            this.oversample = oversampleFor(scaleFactor);
            this.rebuildCharacters();
        }

        final FontCharacter[] characterSet = this.international ? internationalCharacters : defaultCharacters;

        double givenX = x;
        // snap to the device pixel grid: fractional origins smear glyphs even at 1:1
        x = Math.round(x * scaleFactor) / (double) scaleFactor;
        y = Math.round(y * scaleFactor) / (double) scaleFactor;
        GL11.glPushMatrix();
        // narrow attrib mask covering exactly what this draw mutates (enable bits,
        // blend func, current color) -- GL_ALL_ATTRIB_BITS is the most expensive
        // state snapshot the driver offers and this runs per string per frame
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // raster == device pixels: matrix = 1/scaleFactor, coordinates = GUI * scaleFactor
        GL11.glScaled(SCALE / oversample, SCALE / oversample, SCALE / oversample);

        x -= MARGIN_WIDTH / SCALE_INVERSE;
        y -= MARGIN_WIDTH / SCALE_INVERSE;
        final double toRaster = SCALE_INVERSE * this.oversample;
        x *= toRaster;
        y *= toRaster;
        // baseline fudge, ROUNDED to whole raster (= device) pixels -- a fractional
        // vertical shift lands every glyph between texel rows and smears vertically
        y -= Math.round(fontHeight * this.oversample / 5.0);

        final double startX = x;

        final int length = text.length();
        ColorUtil.glColor(color);
        // only pay for the bold-strip when there's actually a color code present (regex/literal replace isn't free)
        if (text.indexOf('\u00A7') >= 0) text = text.replace("\u00A7l", "");
        try {
            final int textLength = text.length();
            // line spacing rounded to whole raster px for the same reason as the baseline
            final int lineHeightTimes2 = (int) Math.round(height() * 2 * this.oversample);
            final double marginWidthTimes2 = MARGIN_WIDTH * 2 * this.oversample;

            // one bind + one GL_QUADS batch per atlas page run instead of one texture
            // bind and one draw call per character
            int batchPage = -1;
            boolean batching = false;

            for (int i = 0; i < textLength; i++) {
                char character = text.charAt(i);

                if (character == '\n') {
                    x = startX;
                    y += lineHeightTimes2;
                    continue;
                }                if (character == '\u00A7') {
                    if (i + 1 >= textLength) break;
                    int codeIdx = "0123456789abcdefklmnor".indexOf(text.charAt(++i));
                    if (codeIdx >= 0 && codeIdx < 16) {
                        // packed-int path (Color(int rgb) semantics = opaque); no per-code Color allocation
                        ColorUtil.glColor(COLOR_CODES[codeIdx] | 0xFF000000);
                    }
                    continue;
                }
                if (character >= characterSet.length || characterSet[character] == null) continue;
                FontCharacter fontCharacter = characterSet[character];
                float characterWidth = fontCharacter.getWidth();
                final int page = fontCharacter.getTexture();
                if (page != batchPage) {
                    if (batching) GL11.glEnd();
                    batchPage = page;
                    GlStateManager.bindTexture(page);
                    GL11.glBegin(GL11.GL_QUADS);
                    batching = true;
                }
                fontCharacter.emit((float) x, (float) y);
                x += characterWidth - marginWidthTimes2;
            }
            if (batching) GL11.glEnd();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.bindTexture(0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glPopAttrib();
        GL11.glPopMatrix();

        // legacy unit: end-x in nominal-raster px (2x draw px), preserved across oversampling
        return (int) ((x - givenX) / this.oversample);
    }

    @Override
    public void drawCharacter(final char character, final int x, final int y, final Color color) {
        final FontCharacter[] characterSet = this.international ? internationalCharacters : defaultCharacters;

        if (character >= characterSet.length || characterSet[character] == null) return;
        final FontCharacter fontCharacter = characterSet[character];
        GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
        fontCharacter.render(x, y);
    }

    public int width(String text) {
        if (text == null) return 0;
        if (text.indexOf('\u00A7') >= 0) text = text.replace("\u00A7l", "");
        final FontCharacter[] characterSet = this.international ? internationalCharacters : defaultCharacters;
        final int length = text.length();
        double width = 0;
        for (int i = 0; i < length; ++i) {
            final char character = text.charAt(i);
            if (character == COLOR_INVOKER) {
                i++;
                continue;
            }
            if (character >= characterSet.length || characterSet[character] == null) continue;
            width += characterSet[character].getWidth() - MARGIN_WIDTH * 2.0 * this.oversample;
        }

        return (int) (width / (2.0 * this.oversample));
    }

    public float height() {
        return fontHeight;
    }
}
