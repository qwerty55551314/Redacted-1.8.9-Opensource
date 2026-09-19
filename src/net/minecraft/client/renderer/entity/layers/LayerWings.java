package net.minecraft.client.renderer.entity.layers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import redacted.client.module.impl.cosmetics.Wings;
import redacted.client.util.render.ModelWings;

// Client-side ender-dragon wings on the local player. Layers render in shoulder-origin space:
// +Y is DOWN, +Z is the player's back, model is at 1/16 scale (see RendererLivingEntity ~L245).
public class LayerWings implements LayerRenderer<AbstractClientPlayer> {
    private final RenderPlayer playerRenderer;
    private final ModelWings model = new ModelWings();

    private static final ResourceLocation EDRAGON = new ResourceLocation("minecraft", "redacted/wings/edragon.png");

    // raw dragon wing reach is ~7 units at boxScale 0.0625; shrink it to a player-sized span. Tune to taste.
    private static final float SCALE = 0.18F;
    private static final float MOUNT_DOWN = 0.05F;  // +Y = down, drop to the shoulder blades
    private static final float MOUNT_BACK = 0.18F;  // +Z = behind the body (sits behind the cape)
    private static final float TILT_BACK = 65.0F;   // stand the flat membrane up so wings fan up-and-back
    private static final float FLAP_SPEED = 0.2F;   // rad/tick

    public LayerWings(final RenderPlayer playerRendererIn) {
        this.playerRenderer = playerRendererIn;
    }

    @Override
    public void doRenderLayer(final AbstractClientPlayer entity, final float limbSwing, final float limbSwingAmount, final float partialTicks, final float p_177141_5_, final float p_177141_6_, final float p_177141_7_, final float scale) {
        if (entity != Minecraft.getMinecraft().thePlayer || entity.isInvisible()) return;
        if (Wings.getActiveWings() == null) return; // only "EDragon" for now

        final float phase = (entity.ticksExisted + partialTicks) * FLAP_SPEED;
        this.model.wing.rotateAngleX = 0.125F - MathHelper.cos(phase) * 0.2F;
        this.model.wing.rotateAngleY = 0.25F;
        this.model.wing.rotateAngleZ = (MathHelper.sin(phase) + 0.125F) * 0.8F;
        this.model.wingTip.rotateAngleZ = -(MathHelper.sin(phase + 2.0F) + 0.5F) * 0.75F;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        this.playerRenderer.bindTexture(EDRAGON);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, MOUNT_DOWN, MOUNT_BACK);
        GlStateManager.rotate(TILT_BACK, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(SCALE, SCALE, SCALE);

        // flat membrane has no back face, so draw it double-sided; render once, mirror across X for the other wing
        GlStateManager.disableCull();
        for (int j = 0; j < 2; ++j) {
            this.model.wing.render(0.0625F);
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
        }
        GlStateManager.enableCull();

        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
    }

    @Override
    public boolean shouldCombineTextures() { return false; }
}
