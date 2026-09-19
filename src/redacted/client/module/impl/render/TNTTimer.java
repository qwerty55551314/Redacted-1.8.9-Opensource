package redacted.client.module.impl.render;

import java.text.DecimalFormat;

import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.item.EntityTNTPrimed;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.HypixelUtil;

import java.awt.Color;

public class TNTTimer extends Module {

    private static TNTTimer instance;
    private static final DecimalFormat timeFormatter = new DecimalFormat("0.00");

    public TNTTimer() {
        super("TNT Timer", "Shows a countdown timer above ignited TNT", Keyboard.KEY_NONE, Category.RENDER, true);
        instance = this;
    }

    public static boolean shouldRender() {
        return instance != null && instance.isEnabled();
    }

    public static void renderTimer(RenderTNTPrimed tntRenderer, EntityTNTPrimed tntPrimed,
                                   double x, double y, double z, float partialTicks) {
        if (!shouldRender()) return;

        int fuseTimer = isHypixel() ? tntPrimed.fuse - 28 : tntPrimed.fuse;

        if (fuseTimer >= 1) {
            double distance = tntPrimed.getDistanceSqToEntity(tntRenderer.getRenderManager().livingPlayer);

            if (distance <= 4096.0D) {
                float number = ((float) fuseTimer - partialTicks) / 20.0F;
                String time = timeFormatter.format((double) number);
                FontRenderer fontrenderer = tntRenderer.getFontRendererFromRenderManager();

                GlStateManager.pushMatrix();
                GlStateManager.translate((float) x + 0.0F, (float) y + tntPrimed.height + 0.5F, (float) z);
                GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(-tntRenderer.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
                byte xMultiplier = 1;

                if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 2) {
                    xMultiplier = -1;
                }

                float scale = 0.02666667F;

                GlStateManager.rotate(tntRenderer.getRenderManager().playerViewX * (float) xMultiplier, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(-scale, -scale, scale);
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                Tessellator tessellator = Tessellator.getInstance();
                WorldRenderer worldrenderer = tessellator.getWorldRenderer();
                int stringWidth = fontrenderer.getStringWidth(time) >> 1;
                float green = Math.min((float) fuseTimer / (isHypixel() ? 52.0F : 80.0F), 1.0F);
                Color color = new Color(1.0F - green, green, 0.0F);

                GlStateManager.disableTexture2D();
                worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
                worldrenderer.pos((double) (-stringWidth - 1), -1.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
                worldrenderer.pos((double) (-stringWidth - 1), 8.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
                worldrenderer.pos((double) (stringWidth + 1), 8.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
                worldrenderer.pos((double) (stringWidth + 1), -1.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.25F).endVertex();
                tessellator.draw();
                GlStateManager.enableTexture2D();
                fontrenderer.drawString(time, -fontrenderer.getStringWidth(time) >> 1, 0, color.getRGB());
                GlStateManager.enableLighting();
                GlStateManager.disableBlend();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.popMatrix();
            }
        }
    }

    private static boolean isHypixel() {
        return HypixelUtil.isOnHypixel();
    }
}
