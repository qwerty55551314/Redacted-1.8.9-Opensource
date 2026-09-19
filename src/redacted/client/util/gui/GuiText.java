package redacted.client.util.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;

// Shared text drawing for the alt/team manager screens.
public class GuiText {

    public static void prepareTextRender() {
        final Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void draw(final int size, final String s, final double x, double y, final int color) {
        prepareTextRender();
        y += 2 + height(size) * 0.1f;
        try {
            Fonts.MAIN.get(size, Weight.REGULAR).draw(s, x, y, color);
        } catch (final Exception e) {
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s, (float) x, (float) y, color);
        }
    }

    public static void drawCentered(final int size, final String s, final double cx, double y, final int color) {
        prepareTextRender();
        y += 2 + height(size) * 0.1f;
        try {
            Fonts.MAIN.get(size, Weight.REGULAR).drawCentered(s, cx, y, color);
        } catch (final Exception e) {
            final FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
            fr.drawStringWithShadow(s, (float) (cx - fr.getStringWidth(s) / 2.0), (float) y, color);
        }
    }

    public static int width(final int size, final String s) {
        try {
            return Fonts.MAIN.get(size, Weight.REGULAR).width(s);
        } catch (final Exception e) {
            return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
        }
    }

    public static float height(final int size) {
        try {
            return Fonts.MAIN.get(size, Weight.REGULAR).height();
        } catch (final Exception e) {
            return Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        }
    }
}
