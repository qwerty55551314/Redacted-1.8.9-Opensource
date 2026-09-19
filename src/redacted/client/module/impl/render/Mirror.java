package redacted.client.module.impl.render;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.gui.Draggable;
import redacted.client.util.render.RenderUtil;
import redacted.client.value.impl.NumberValue;

public class Mirror extends Module {
    private final NumberValue quality = new NumberValue("Quality", this, 2.0D, 1.0D, 4.0D, 0.5D).describe("Resolution: 1 = 540p, 2 = 1080p, 4 = 4K");
    private final NumberValue updateDelay = new NumberValue("Update Delay", this, 3, 1, 20, 1).describe("Re-render every N frames -- the main FPS knob (quality is only paid on update frames)");

    // ponytail: a second world pass per update frame -- Quality and Update Delay are the perf knobs.
    // The flag gates getMouseOver (keep the frame's real pick) plus the handful of Render3D handlers
    // whose data is wrong or stateful under the flipped camera (SuperheroFX update, Trajectories,
    // the stencil-using floating TargetHUD).
    private static boolean rendering;

    private Draggable draggable;
    private Framebuffer fbo;
    private int frameCounter;

    public Mirror() {
        super("Mirror", "Rear-view mirror of what's behind you", Keyboard.KEY_NONE, Category.RENDER);
    }

    public static boolean isRendering() {
        return rendering;
    }

    private static int passWidth, passHeight;

    // World-pass size overrides: the vanilla projection/viewport/outline-blit sites ask these for
    // their dimensions so the mirror pass renders at FBO size WITHOUT lying to mc.displayWidth
    // a global swap poisoned every ScaledResolution-derived cache that refreshed mid-pass (font
    // scale factor, icon scale), which made the HUD flicker between qualities.
    public static int viewWidth(int real) {
        return rendering ? passWidth : real;
    }

    public static int viewHeight(int real) {
        return rendering ? passHeight : real;
    }

    // eager-registered from Client.start so the edit-HUD dummy exists before first render;
    // base size is fixed 16:9, so no per-frame setBaseWidth/Height needed
    public Draggable getDraggable() {
        if (draggable == null) draggable = registerDraggable("Mirror", -1, -1, 240, 135);
        return draggable;
    }

    /** Edit-GUI stand-in while the module is off: a dark pane instead of a real world pass. */
    public void renderEditPreview(final ScaledResolution sr) {
        final Draggable d = getDraggable();
        float x = d.getX(), y = d.getY();
        if (x < 0) { x = sr.getScaledWidth() / 2.0F - d.getWidth() / 2.0F; d.setX(x); }   // same first-run spot as the live path
        if (y < 0) { y = 5; d.setY(y); }
        RenderUtil.drawRoundedRect(x, y, d.getWidth(), d.getHeight(), 4, 0xE0101318);
    }

    @Override
    public void onDisable() {
        if (fbo != null) {
            fbo.deleteFramebuffer();   // main thread == GL thread for every setEnabled path
            fbo = null;
        }
    }

    @EventTarget
    public void onRender2D(final Render2DEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!OpenGlHelper.isFramebufferEnabled()) return;
        // OptiFine shader pipeline is not re-entrant mid-frame (renderWorld -> Shaders.beginRender)
        if (net.minecraft.src.Config.isShaders()) return;
        final Entity view = mc.getRenderViewEntity();
        if (view == null) return;

        final int targetW = Math.round(960 * quality.floatValue());
        final int targetH = Math.round(540 * quality.floatValue());
        if (fbo == null || fbo.framebufferWidth != targetW || fbo.framebufferHeight != targetH
                || frameCounter++ % updateDelay.intValue() == 0) {
            renderMirrorPass(event.getPartialTicks(), view, targetW, targetH);
        }

        final Draggable d = getDraggable();
        final ScaledResolution sr = event.getScaledResolution();
        float x = d.getX(), y = d.getY();
        if (x < 0) { x = sr.getScaledWidth() / 2.0F - d.getWidth() / 2.0F; d.setX(x); }   // first run: top-center
        if (y < 0) { y = 5; d.setY(y); }
        final float w = d.getWidth(), h = d.getHeight();

        // opaque blit, same discipline as Framebuffer.framebufferRenderExt: blend and depth off (on
        // non-update frames the depth buffer still holds this frame's hotbar item depths), v=0 at the
        // bottom edge (GL FBO textures are bottom-up), and U reversed so the rear camera reads like a
        // mirror instead of a backwards camera
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        fbo.bindFramebufferTexture();
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        worldrenderer.pos(x, y + h, 0.0D).tex(1.0D, 0.0D).color(255, 255, 255, 255).endVertex();
        worldrenderer.pos(x + w, y + h, 0.0D).tex(0.0D, 0.0D).color(255, 255, 255, 255).endVertex();
        worldrenderer.pos(x + w, y, 0.0D).tex(0.0D, 1.0D).color(255, 255, 255, 255).endVertex();
        worldrenderer.pos(x, y, 0.0D).tex(1.0D, 1.0D).color(255, 255, 255, 255).endVertex();
        tessellator.draw();
        fbo.unbindFramebufferTexture();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    private void renderMirrorPass(final float partialTicks, final Entity view, final int targetW, final int targetH) {
        if (fbo == null || fbo.framebufferWidth != targetW || fbo.framebufferHeight != targetH) {
            if (fbo != null) fbo.deleteFramebuffer();
            fbo = new Framebuffer(targetW, targetH, true);
            fbo.setFramebufferFilter(9729);   // GL_LINEAR; createFramebuffer resets to NEAREST on recreate
            // the high-res texture is shown in a small box: trilinear minification, mips regenerated per update
            GlStateManager.bindTexture(fbo.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager.bindTexture(0);
        }

        final float yaw = view.rotationYaw, pitch = view.rotationPitch;
        final float prevYaw = view.prevRotationYaw, prevPitch = view.prevRotationPitch;
        final boolean hand = mc.entityRenderer.getRenderHand();
        passWidth = targetW;
        passHeight = targetH;
        try {
            // exact optical reflection of the view direction: yaw + 180, pitch negated
            view.rotationYaw = yaw + 180.0F;
            view.prevRotationYaw = prevYaw + 180.0F;
            view.rotationPitch = -pitch;
            view.prevRotationPitch = -prevPitch;
            rendering = true;
            mc.entityRenderer.setRenderHand(false);
            // the ESP-outline block rebinds mc.getFramebuffer() mid-pass (RenderGlobal.renderEntities);
            // the delegate points that rebind at our FBO instead of the screen
            mc.setFramebufferDelegate(fbo);
            fbo.bindFramebuffer(true);   // renderWorldPass clears color+depth itself
            mc.entityRenderer.renderWorld(partialTicks, System.nanoTime());
            // composite the ESP glow layer into the mirror, exactly like the main pass does right
            // after its renderWorld; self-guarded, and blits at viewWidth/viewHeight so it fills our FBO
            mc.renderGlobal.renderEntityOutlineFramebuffer();
        } finally {
            view.rotationYaw = yaw;
            view.rotationPitch = pitch;
            view.prevRotationYaw = prevYaw;
            view.prevRotationPitch = prevPitch;
            rendering = false;
            mc.entityRenderer.setRenderHand(hand);
            mc.setFramebufferDelegate(null);
            // vanilla world->overlay transition (EntityRenderer.updateCameraAndRender + the
            // GuiIngame.renderGameOverlay opening), so later Render2D handlers see clean GUI state
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.alphaFunc(516, 0.1F);
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        fbo.bindFramebufferTexture();
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        fbo.unbindFramebufferTexture();
    }
}
