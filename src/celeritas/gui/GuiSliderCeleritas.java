package celeritas.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

/**
 * Integer slider for the Celeritas settings screen. Renders a drag knob over a track like the
 * vanilla {@code GuiOptionSlider}; the parent screen is notified on release so it can persist.
 */
public class GuiSliderCeleritas extends GuiButton {

    private int optionId;
    private final String baseLabel;
    private final int min;
    private final int max;
    private int value;
    private int step = 1;
    private boolean dragging;

    public GuiSliderCeleritas(int id, int x, int y, int width, int height,
                              String label, String suffix, int min, int max, int value,
                              boolean showValue, boolean enabled) {
        super(id, x, y, width, height, "");
        this.optionId = id;
        this.baseLabel = label;
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.value = Math.max(min, Math.min(max, value));
        this.enabled = enabled;
        this.displayString = label + this.value + suffix;
    }

    public void setOptionId(int optionId) {
        this.optionId = optionId;
    }

    public void setStep(int step) {
        this.step = Math.max(1, step);
    }

    private float fraction() {
        return (float) (this.value - this.min) / (float) (this.max - this.min);
    }

    private int fromMouse(int mouseX) {
        float f = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
        f = MathHelper.clamp_float(f, 0.0F, 1.0F);
        int v = Math.round((this.min + f * (this.max - this.min)) / (float) this.step) * this.step;
        return Math.max(this.min, Math.min(this.max, v));
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 46, this.width / 2, this.height);
            this.drawTexturedModalRect(this.xPosition + this.width / 2, this.yPosition, 200 - this.width / 2, 46, this.width / 2, this.height);
            this.mouseDragged(mc, mouseX, mouseY);

            int j = 14737632;
            if (!this.enabled) {
                j = 10526880;
            } else if (this.hovered) {
                j = 16777120;
            }
            this.drawCenteredString(mc.fontRendererObj, this.displayString, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, j);
        }
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return 0; // sliders never use the hover highlight
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible && this.dragging) {
            int newValue = fromMouse(mouseX);
            if (newValue != this.value) {
                this.value = newValue;
                this.displayString = this.baseLabel + this.value;
            }
        }
        // Draw the drag knob over the track.
        int knobX = this.xPosition + (int) (this.fraction() * (float) (this.width - 8));
        this.drawTexturedModalRect(knobX, this.yPosition, 0, 66, 4, 20);
        this.drawTexturedModalRect(knobX + 4, this.yPosition, 196, 66, 4, 20);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.value = fromMouse(mouseX);
            this.displayString = this.baseLabel + this.value;
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
        if (this.visible) {
            GuiCeleritasSettings screen = GuiCeleritasSettings.currentScreen();
            if (screen != null) screen.onSliderReleased(this.optionId, this.value);
        }
    }
}
