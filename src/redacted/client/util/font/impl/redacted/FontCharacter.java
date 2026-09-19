package redacted.client.util.font.impl.redacted;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * One glyph inside its font's atlas page. Owns no GL texture itself -- only the
 * UV window into the shared page texture. Geometry (x/y/width/height) and texel
 * content are byte-identical to the old per-glyph texture rendering; only the
 * bind/draw granularity changed (one page bind + one batched draw per string
 * instead of one texture + one draw call per character).
 */
public class FontCharacter {

    private final int pageTexture;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;
    private final float width;
    private final float height;

    public FontCharacter(final int pageTexture, final float u0, final float v0, final float u1, final float v1, final float width, final float height) {
        this.pageTexture = pageTexture;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.width = width;
        this.height = height;
    }

    public float getWidth() { return width; }

    public float getHeight() { return height; }

    /** GL texture name of the atlas page holding this glyph (freed at page level). */
    public int getTexture() { return pageTexture; }

    public float getU0() { return u0; }

    public float getV0() { return v0; }

    public float getU1() { return u1; }

    public float getV1() { return v1; }

    /** Immediate single-glyph quad (drawCharacter path): binds the page, one quad. */
    public void render(final float x, final float y) {
        GlStateManager.bindTexture(pageTexture);

        GL11.glBegin(GL11.GL_QUADS);
        emit(x, y);
        GL11.glEnd();
    }

    /** Emits the quad into an already-open GL_QUADS batch (no begin/end, no bind). */
    public void emit(final float x, final float y) {
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + width, y);
    }
}
