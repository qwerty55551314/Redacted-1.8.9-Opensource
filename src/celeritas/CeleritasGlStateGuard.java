package celeritas;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Port of Natron's {@code NatronGlStateGuard}: wraps a Celeritas managed-code block so vanilla's
 * own {@code GlStateManager} cache is never left pointing at stale state afterwards.
 * <p>
 * See Natron's class comment for the full reasoning behind the restore mask.
 */
public final class CeleritasGlStateGuard implements AutoCloseable {

    private static final int RESTORE_MASK = GL11.GL_ENABLE_BIT
        | GL11.GL_COLOR_BUFFER_BIT
        | GL11.GL_LIGHTING_BIT
        | GL11.GL_DEPTH_BUFFER_BIT
        | GL11.GL_FOG_BIT
        | GL11.GL_POLYGON_BIT
        | GL11.GL_TEXTURE_BIT
        | GL11.GL_STENCIL_BUFFER_BIT
        | GL11.GL_CURRENT_BIT
        | GL11.GL_VIEWPORT_BIT
        | GL11.GL_SCISSOR_BIT;

    /**
     * The active texture unit captured before we force unit 0. {@code glPushAttrib(GL_TEXTURE_BIT)}
     * snapshots the real active unit, but it does NOT update vanilla's {@code GlStateManager} cache,
     * and after {@code glPopAttrib} the driver restores that captured unit while vanilla's cache still
     * believes unit 0 is active. The mismatch makes later vanilla binds (entities rendered right after
     * terrain) target the wrong unit -> e.g. skeleton/entity head textures glitching at certain view
     * angles. We re-sync the cache to the restored unit on close.
     */
    private final int savedActiveTexture;

    private CeleritasGlStateGuard() {
        GL11.glPushAttrib(RESTORE_MASK);
        this.savedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        RenderDevice.enterManagedCode();
    }

    public static CeleritasGlStateGuard open() {
        return new CeleritasGlStateGuard();
    }

    @Override
    public void close() {
        RenderDevice.exitManagedCode();
        GL11.glPopAttrib();
        // glPopAttrib restores the driver's alpha-test enable bit to whatever it was before this block,
        // but it does NOT touch vanilla's GlStateManager cache. Celeritas's PipelineState ran
        // GlStateManager.enableAlpha()/disableAlpha() inside the block, so the cache now disagrees with the
        // driver. The next entity pass (e.g. RendererLivingEntity) calls enableAlpha() before drawing the
        // transparent parts of mobs; if the cache wrongly reports alpha test as already enabled, that call is
        // skipped and the driver keeps it disabled -> skeleton heads/ribs render black instead of discarding
        // the transparent texels. Re-sync the cache to whatever the driver actually restored.
        if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        // Re-sync vanilla's active-texture cache with the unit the driver actually restored.
        GlStateManager.setActiveTexture(this.savedActiveTexture);
    }
}
