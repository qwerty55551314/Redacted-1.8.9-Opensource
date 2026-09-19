package redacted.client.module.impl.render;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.RenderUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

import java.awt.Color;
public class Crosshair extends Module {
    private final ModeValue style = new ModeValue("Style", this, "Cross", "Cross", "Dot", "Circle", "Arrow").describe("Crosshair shape");
    private final NumberValue size = new NumberValue("Size", this, 1.0D, 0.5D, 3.0D, 0.1D).describe("Overall crosshair scale");
    private final ColorValue color = new ColorValue("Color", this, 0xFFFFFFFF).describe("Crosshair color");
    private final NumberValue alpha = new NumberValue("Alpha", this, 255, 0, 255, 1).describe("Crosshair opacity");
    private final BooleanValue dynamicColor = new BooleanValue("Dynamic Color", this, true).describe("Turn red when aimed at an entity");

    public Crosshair() {
        super("Crosshair", "Custom crosshair overlay", Keyboard.KEY_NONE, Category.RENDER);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.gameSettings.showDebugInfo) return;

        ScaledResolution sr = event.getScaledResolution();
        float cx = sr.getScaledWidth() / 2.0F;
        float cy = sr.getScaledHeight() / 2.0F;

        int color = getCrosshairColor();
        float scale = size.floatValue();
        float t = 2.0F * scale;
        float g = 3.0F * scale;
        float l = 6.0F * scale;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        String s = style.getValue();
        GL11.glLineWidth(t);

        ColorUtil.glColor(color);

        switch (s) {
            case "Cross":
                drawLine(cx, cy - g - l, cx, cy - g);
                drawLine(cx, cy + g, cx, cy + g + l);
                drawLine(cx - g - l, cy, cx - g, cy);
                drawLine(cx + g, cy, cx + g + l, cy);
                break;
            case "Dot":
                RenderUtil.drawFilledCircle(cx, cy, Math.max(t, 2.0), color);
                break;
            case "Circle":
                RenderUtil.drawCircleOutline(cx, cy, g + l, t, color);
                break;
            case "Arrow":
                drawLine(cx, cy - g, cx, cy - g - l);
                drawLine(cx - g, cy - g, cx, cy);
                drawLine(cx, cy, cx + g, cy - g);
                break;
        }

        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private int getCrosshairColor() {
        if (dynamicColor.getValue() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            return new Color(255, 80, 80, alpha.intValue()).getRGB();
        }
        return ColorUtil.withAlpha(color.getRGB(), alpha.intValue());
    }

    private void drawLine(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }
}
