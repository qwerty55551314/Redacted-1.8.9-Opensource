package net.minecraft.client.renderer.entity.layers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import redacted.client.module.impl.cosmetics.Hat;
import redacted.client.util.render.ModelHat;

// Client-side kasa hat on the LOCAL player only. After bipedHead.postRender we sit at the head
// pivot in flipped model space (+Y down, 1/16 scale) already rotated by head yaw+pitch, so the
// hat tracks the head. Textureless: disableTexture2D + flat color, re-enabled afterward.
public class LayerHat implements LayerRenderer<AbstractClientPlayer> {
    private final ModelRenderer bipedHead;
    private final ModelHat model = new ModelHat();

    public LayerHat(final ModelRenderer bipedHeadIn) {
        this.bipedHead = bipedHeadIn;
    }

    @Override
    public void doRenderLayer(final AbstractClientPlayer entity, final float limbSwing, final float limbSwingAmount, final float partialTicks, final float p_177141_5_, final float p_177141_6_, final float p_177141_7_, final float scale) {
        if (entity != Minecraft.getMinecraft().thePlayer || entity.isInvisible()) return;
        final String hat = Hat.getActiveHat();
        if (hat == null) return;

        final boolean sensei = hat.equals("Sensei");

        GlStateManager.pushMatrix();
        // drop with the head when sneaking, like LayerCustomHead
        if (entity.isSneaking()) {
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        this.bipedHead.postRender(0.0625F);

        // flat color, lighting off or it renders uncolorable grey
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        if (sensei) {
            GlStateManager.color(0.22F, 0.18F, 0.13F, 1.0F); // dark walnut
        } else {
            GlStateManager.color(0.78F, 0.66F, 0.42F, 1.0F); // straw tan
        }

        if (sensei) {
            this.model.sensei.render(0.0625F);
        } else {
            this.model.samurai.render(0.0625F);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() { return false; }
}
