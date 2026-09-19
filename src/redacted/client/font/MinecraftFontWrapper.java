package redacted.client.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import redacted.client.util.font.Font;

import java.awt.*;

public class MinecraftFontWrapper extends Font {
    private final FontRenderer fontRenderer;

    public MinecraftFontWrapper(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    private FontRenderer fr() {
        if (fontRenderer != null) return fontRenderer;
        return Minecraft.getMinecraft().fontRendererObj;
    }

    @Override
    public int draw(String text, double x, double y, int color, boolean dropShadow) {
        FontRenderer r = fr();
        if (r == null) return 0;
        if (dropShadow) {
            return r.drawStringWithShadow(text, (float) x, (float) y, color);
        }
        return r.drawString(text, (float) x, (float) y, color, false);
    }

    @Override
    public int draw(String text, double x, double y, int color) {
        FontRenderer r = fr();
        if (r == null) return 0;
        return r.drawString(text, (float) x, (float) y, color, false);
    }

    @Override
    public int drawWithShadow(String text, double x, double y, int color) {
        FontRenderer r = fr();
        if (r == null) return 0;
        return r.drawStringWithShadow(text, (float) x, (float) y, color);
    }

    @Override
    public int width(String text) {
        FontRenderer r = fr();
        if (r == null) return 0;
        return r.getStringWidth(text);
    }

    @Override
    public int drawCentered(String text, double x, double y, int color) {
        FontRenderer r = fr();
        if (r == null) return 0;
        return r.drawString(text, (float) (x - r.getStringWidth(text) / 2.0), (float) y, color, false);
    }

    @Override
    public int drawRight(String text, double x, double y, int color) {
        FontRenderer r = fr();
        if (r == null) return 0;
        return r.drawString(text, (float) (x - r.getStringWidth(text)), (float) y, color, false);
    }

    @Override
    public float height() {
        FontRenderer r = fr();
        if (r == null) return 0;
        return r.FONT_HEIGHT;
    }

    @Override
    public void drawCharacter(char character, int x, int y, Color color) {
        FontRenderer r = fr();
        if (r == null) return;
        r.drawString(String.valueOf(character), x, y, color.getRGB(), false);
    }
}
