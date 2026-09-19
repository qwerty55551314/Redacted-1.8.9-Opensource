package redacted.client.gui.clickgui.exhibition;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import redacted.client.Client;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.render.ClickGUI;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.SettingMath;
import redacted.client.util.render.ColorUtil;
import redacted.client.util.render.RenderUtil;
import redacted.client.util.font.Font;
import redacted.client.value.Value;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.client.value.impl.RangeValue;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExhibitionClickGUI extends GuiScreen {

    public static final ExhibitionClickGUI instance = new ExhibitionClickGUI();

    private static final double REF_RECT_WIDTH = 400.0;
    private static final double REF_RECT_HEIGHT = 320.0;

    private final Animation introAnimation = new Animation(Easing.EASE_OUT_QUAD, 300);

    private float x = 100, y = 100;

    private final int borderOuter = new Color(10, 10, 10).getRGB();
    private final int borderMiddle = new Color(48, 48, 48).getRGB();
    private final int background = new Color(17, 17, 17).getRGB();
    private final int accentColor = new Color(165, 241, 165).getRGB();

    private Category selectedCategory = Category.COMBAT;
    private float scrollOffset;
    private float targetScroll;

    private boolean dragging = false;
    private float draggingOffsetX, draggingOffsetY;
    private boolean closing = false;

    private final List<ExhibitionModulePanel> cachedPanels = new ArrayList<>();

    private Font tahoma12;
    private Font tahoma14;
    private Font iconFont40;

    private double getScale() {
        return ClickGUI.guiScale();
    }

    public void rebuildCache() {
        cachedPanels.clear();
        for (Module module : Client.getInstance().getModuleManager().getModulesByCategory(selectedCategory)) {
            cachedPanels.add(new ExhibitionModulePanel(module));
        }
        scrollOffset = 0;
        targetScroll = 0;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        double scale = getScale();
        ScaledResolution sr = new ScaledResolution(mc);

        tahoma12 = Fonts.MAIN.get(Fonts.size(12 * scale), Weight.REGULAR);
        tahoma14 = Fonts.MAIN.get(Fonts.size(14 * scale), Weight.REGULAR);
        iconFont40 = Fonts.EXHIBITION_ICONS.get(Fonts.size(40 * scale));

        double pw = REF_RECT_WIDTH * scale;
        double ph = REF_RECT_HEIGHT * scale;
        if (x * scale < 0 || y * scale < 0 || x * scale + pw > sr.getScaledWidth() || y * scale + ph > sr.getScaledHeight()) {
            x = (float) ((sr.getScaledWidth() - pw) / (2.0 * scale));
            y = (float) ((sr.getScaledHeight() - ph) / (2.0 * scale));
        }

        closing = false;
        introAnimation.setValue(0);
        introAnimation.reset();
        rebuildCache();
        Double savedScroll = Client.getInstance().getThemeManager().getExhibitionScroll();
        if (savedScroll != null) {
            scrollOffset = (float) (savedScroll * scale);
            targetScroll = scrollOffset;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        double scale = getScale();
        Client.getInstance().getThemeManager().setExhibitionScroll(scrollOffset / scale);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // category icon glyphs, mapped exactly as the reference UI maps them:
    // Combat=E Player=F Movement=J Visuals(render)=C Other/I=I
    private String getCategoryIcon(Category type) {
        switch (type) {
            case COMBAT: return "E";
            case MOVEMENT: return "J";
            case RENDER: return "C";
            case WORLD: return "D";
            case DEVELOPER: return "F";
            default: return "B";
        }
    }

    private void drawRectWH(float x, float y, float w, float h, int color) {
        // double-precision rect: the int-cast version made 1px borders and the mode
        // arrow collapse onto random pixel rows while the scroll offset animated
        // through fractional values (borders "spazzing", arrow distorting)
        RenderUtil.drawRect(x, y, w, h, color);
    }

    private void startScissor(float x, float y, float width, float height) {
        ScaledResolution sr = new ScaledResolution(mc);
        double scaleFactor = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) (x * scaleFactor),
                (int) ((sr.getScaledHeight() - (y + height)) * scaleFactor),
                (int) (width * scaleFactor),
                (int) (height * scaleFactor)
        );
    }

    private void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        double scale = getScale();
        float rectW = (float) (REF_RECT_WIDTH * scale);
        float rectH = (float) (REF_RECT_HEIGHT * scale);
        float px = x * (float) scale;
        float py = y * (float) scale;

        introAnimation.run(closing ? 0 : 1);
        double anim = introAnimation.getValue();
        int globalAlpha = (int) (255 * anim);

        if (closing && introAnimation.isFinished()) {
            mc.displayGuiScreen(null);
            return;
        }

        if (dragging) {
            x = (mouseX + draggingOffsetX) / (float) scale;
            y = (mouseY + draggingOffsetY) / (float) scale;
            ScaledResolution sr = new ScaledResolution(mc);
            float maxRX = (float) ((sr.getScaledWidth() - rectW) / scale);
            float maxRY = (float) ((sr.getScaledHeight() - rectH) / scale);
            x = Math.max(0, Math.min(x, maxRX));
            y = Math.max(0, Math.min(y, maxRY));
            px = x * (float) scale;
            py = y * (float) scale;
        }

        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, (1.0 - anim) * 10.0, 0);

        drawRectWH(px - 0.5f, py - 0.5f, rectW + 1, rectH + 1, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
        drawRectWH(px, py, rectW, rectH, ColorUtil.withAlpha(borderMiddle, globalAlpha / 255f));
        drawRectWH(px + 0.5f, py + 0.5f, rectW - 1, rectH - 1, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
        drawRectWH(px + 1.5f, py + 1.5f, rectW - 3, rectH - 3, ColorUtil.withAlpha(background, globalAlpha / 255f));

        RenderUtil.drawGradientRect(px + 2, py + 2, rectW / 2f - 2, 2,
                ColorUtil.withAlpha(new Color(55, 177, 218).getRGB(), globalAlpha / 255f),
                ColorUtil.withAlpha(new Color(204, 77, 198).getRGB(), globalAlpha / 255f));
        RenderUtil.drawGradientRect(px + rectW / 2f, py + 2, rectW / 2f - 2, 2,
                ColorUtil.withAlpha(new Color(204, 77, 198).getRGB(), globalAlpha / 255f),
                ColorUtil.withAlpha(new Color(204, 227, 53).getRGB(), globalAlpha / 255f));

        tahoma14.draw(Client.NAME, px + 6, py + 6, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));

        float catX = px + 8 * (float) scale;
        float catY = py + 22 * (float) scale;

        drawGroupBox(catX, catY, 55 * (float) scale, rectH - 30 * (float) scale, "", globalAlpha);

        float btnY = catY + 5 * (float) scale;
        for (Category type : Category.values()) {
            boolean selected = selectedCategory == type;
            boolean hovered = GUIUtil.mouseOver(catX + 2 * scale, btnY, 51 * (float) scale, 35 * (float) scale, mouseX, mouseY);

            int textCol = selected ? 210 : hovered ? 165 : 100;

            iconFont40.drawCentered(getCategoryIcon(type), catX + 27.5f * (float) scale, btnY + 17 * (float) scale, ColorUtil.withAlpha(new Color(textCol, textCol, textCol).getRGB(), globalAlpha / 255f));

            btnY += 40 * (float) scale;
        }

        float modAreaX = px + 70 * (float) scale;
        float modAreaY = py + 22 * (float) scale;
        float modAreaW = rectW - 78 * (float) scale;
        float modAreaH = rectH - 30 * (float) scale;

        drawGroupBox(modAreaX, modAreaY, modAreaW, modAreaH, "", globalAlpha);

        boolean hoveringModArea = GUIUtil.mouseOver(modAreaX, modAreaY, modAreaW, modAreaH, mouseX, mouseY);
        float wheel = Mouse.getDWheel();
        if (hoveringModArea && wheel != 0) {
            targetScroll += wheel > 0 ? 30 * (float) scale : -30 * (float) scale;
        }

        float col1Height = 0, col2Height = 0;
        for (ExhibitionModulePanel panel : cachedPanels) {
            float boxHeight = panel.calculateHeight(scale);
            float halfW = modAreaW / 2f - 10 * (float) scale;
            if (col1Height <= col2Height) {
                panel.setRenderBounds(modAreaX + 6 * (float) scale, modAreaY + 6 * (float) scale + col1Height, halfW);
                col1Height += boxHeight + 8 * (float) scale;
            } else {
                panel.setRenderBounds(modAreaX + modAreaW / 2f + 4 * (float) scale, modAreaY + 6 * (float) scale + col2Height, halfW);
                col2Height += boxHeight + 8 * (float) scale;
            }
        }

        float maxScroll = Math.max(0, Math.max(col1Height, col2Height) - modAreaH + 12 * (float) scale);
        targetScroll = Math.max(-maxScroll, Math.min(0, targetScroll));
        scrollOffset += (targetScroll - scrollOffset) * 0.5f;

        startScissor(modAreaX + 1, modAreaY + 1, modAreaW - 2, modAreaH - 2);

        for (ExhibitionModulePanel panel : cachedPanels) {
            panel.y += scrollOffset;
            panel.draw(mouseX, mouseY, globalAlpha, scale);
        }

        endScissor();

        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        double scale = getScale();
        float px = x * (float) scale;
        float py = y * (float) scale;
        float rectW = (float) (REF_RECT_WIDTH * scale);
        float rectH = (float) (REF_RECT_HEIGHT * scale);

        if (GUIUtil.mouseOver(px, py, rectW, 15 * (float) scale, mouseX, mouseY) && mouseButton == 0) {
            draggingOffsetX = px - mouseX;
            draggingOffsetY = py - mouseY;
            dragging = true;
            return;
        }

        float catX = px + 8 * (float) scale;
        float btnY = py + 27 * (float) scale;
        for (Category type : Category.values()) {
            if (GUIUtil.mouseOver(catX + 2 * (float) scale, btnY, 51 * (float) scale, 35 * (float) scale, mouseX, mouseY) && mouseButton == 0) {
                selectedCategory = type;
                rebuildCache();
                return;
            }
            btnY += 40 * (float) scale;
        }

        float modAreaX = px + 70 * (float) scale;
        float modAreaY = py + 22 * (float) scale;
        float modAreaW = rectW - 78 * (float) scale;
        float modAreaH = rectH - 30 * (float) scale;

        if (GUIUtil.mouseOver(modAreaX, modAreaY, modAreaW, modAreaH, mouseX, mouseY)) {
            for (ExhibitionModulePanel panel : cachedPanels) {
                if (panel.click(mouseX, mouseY, mouseButton, scale)) return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        for (ExhibitionModulePanel panel : cachedPanels) panel.release();
    }

    public void requestClose() {
        closing = true;
    }

    public boolean isBindingKey() {
        for (ExhibitionModulePanel panel : cachedPanels) {
            if (panel.isBinding()) return true;
        }
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 || (!isBindingKey() && Client.getInstance().getModuleManager().getModule(ClickGUI.class).shouldCloseOn(keyCode))) {
            requestClose();
            return;
        }
        for (ExhibitionModulePanel panel : cachedPanels) panel.keyTyped(typedChar, keyCode);
    }

    public void drawGroupBox(float x, float y, float width, float height, String title, int globalAlpha) {
        drawRectWH(x - 0.5f, y - 0.5f, width + 1, height + 1, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
        drawRectWH(x, y, width, height, ColorUtil.withAlpha(borderMiddle, globalAlpha / 255f));
        drawRectWH(x + 0.5f, y + 0.5f, width - 1, height - 1, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
        drawRectWH(x + 1.5f, y + 1.5f, width - 3, height - 3, ColorUtil.withAlpha(background, globalAlpha / 255f));

        if (!title.isEmpty()) {
            float titleWidth = tahoma14.width(title);
            drawRectWH(x + 6, y - 1.5f, titleWidth + 4, 3, ColorUtil.withAlpha(background, globalAlpha / 255f));
            tahoma14.draw(title, x + 8, y - 2, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
        }
    }

    private class ExhibitionModulePanel {
        private final Module module;
        public float x, y, width, height;
        private boolean isBinding = false;
        private Value<?> draggingSlider = null;
        private ModeValue openDropdown = null;
        private int draggingRangeHandle = 0;
        private double anchoredValue;
        private double anchoredMouseX;

        private static final float REF_HEADER_HEIGHT = 18.0f;
        private static final float REF_CHECKBOX_SIZE = 6.0f;
        private static final float REF_GAP = 2.0f;
        private static final float REF_BOOL_HEIGHT = 12.0f;
        private static final float REF_NUM_RANGE_HEIGHT = 16.0f;
        private static final float REF_MODE_HEIGHT = 20.0f;

        public ExhibitionModulePanel(Module module) {
            this.module = module;
        }

        public boolean isBinding() {
            return isBinding;
        }

        public void setRenderBounds(float x, float y, float width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        public float calculateHeight(double scale) {
            float h = REF_HEADER_HEIGHT * (float) scale;
            for (Value<?> setting : module.getSettings()) {
                if (!setting.isVisible()) continue;
                if (setting instanceof BooleanValue) h += REF_BOOL_HEIGHT * (float) scale;
                else if (setting instanceof NumberValue) h += REF_NUM_RANGE_HEIGHT * (float) scale;
                else if (setting instanceof RangeValue) h += REF_NUM_RANGE_HEIGHT * (float) scale;
                else if (setting instanceof ModeValue) {
                    h += REF_MODE_HEIGHT * (float) scale;
                    if (openDropdown == setting) {
                        h += ((ModeValue) setting).getModes().size() * 9 * (float) scale + 2 * (float) scale;
                    }
                }
            }
            return h + 4 * (float) scale;
        }

        public void draw(int mouseX, int mouseY, int globalAlpha, double scale) {
            this.height = calculateHeight(scale);
            drawGroupBox(x, y, width, height, module.getName(), globalAlpha);

            boolean enabled = module.isEnabled();
            drawRectWH(x + width - 12 * (float) scale, y - 1, 6 * (float) scale, 4 * (float) scale, ColorUtil.withAlpha(enabled ? accentColor : borderMiddle, globalAlpha / 255f));

            String bindText = isBinding ? "..." : "[" + Keyboard.getKeyName(module.getKey()) + "]";
            if (bindText.equals("[NONE]")) bindText = "[-]";
            tahoma12.draw(bindText, x + width - 18 * (float) scale - tahoma12.width(bindText), y - 2, ColorUtil.withAlpha(new Color(120, 120, 120).getRGB(), globalAlpha / 255f));

            float currentY = y + 10 * (float) scale;

            float s = (float) scale;
            for (Value<?> setting : module.getSettings()) {
                if (!setting.isVisible()) continue;

                if (setting instanceof BooleanValue) {
                    BooleanValue boolSet = (BooleanValue) setting;
                    drawRectWH(x + 6 * s, currentY, REF_CHECKBOX_SIZE * s, REF_CHECKBOX_SIZE * s, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
                    RenderUtil.drawGradientRect(x + 6.5f * s, currentY + 0.5f * s, 5f * s, 5f * s, ColorUtil.withAlpha(new Color(46, 46, 46).getRGB(), globalAlpha / 255f), ColorUtil.withAlpha(new Color(27, 27, 27).getRGB(), globalAlpha / 255f));
                    if (boolSet.getValue()) {
                        drawRectWH(x + 7f * s, currentY + 1f * s, 4f * s, 4f * s, ColorUtil.withAlpha(accentColor, globalAlpha / 255f));
                    }
                    tahoma14.draw(setting.getName(), x + 16 * s, currentY, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
                    currentY += REF_BOOL_HEIGHT * s;
                } else if (setting instanceof NumberValue) {
                    NumberValue numSet = (NumberValue) setting;
                    tahoma14.draw(setting.getName(), x + 6 * s, currentY, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
                    if (draggingSlider == numSet) {
                        double newVal = differentialSliderValue(numSet, mouseX, scale);
                        numSet.setValue(newVal);
                    }
                    double percent = SettingMath.valueToPercent(numSet.getMinimum(), numSet.getMaximum(), numSet.getValue());
                    String valStr = String.format("%.2f", numSet.getValue());
                    tahoma12.draw(valStr, x + width - 6 * s - tahoma12.width(valStr), currentY, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
                    currentY += 8 * s;
                    drawRectWH(x + 6 * s, currentY, width - 12 * s, 4 * s, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
                    RenderUtil.drawGradientRect(x + 6.5f * s, currentY + 0.5f * s, (float) ((width - 13f * s) * percent), 3f * s, ColorUtil.withAlpha(new Color(accentColor).darker().getRGB(), globalAlpha / 255f), ColorUtil.withAlpha(accentColor, globalAlpha / 255f));
                    currentY += 8 * s;
                } else if (setting instanceof RangeValue) {
                    RangeValue rangeSet = (RangeValue) setting;
                    tahoma14.draw(setting.getName(), x + 6 * s, currentY, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
                    if (draggingSlider == rangeSet) {
                        double newVal = differentialSliderValue(rangeSet, mouseX, scale);
                        if (draggingRangeHandle == 0) rangeSet.setRangeMin(Math.min(newVal, rangeSet.getRangeMax()));
                        else rangeSet.setRangeMax(Math.max(newVal, rangeSet.getRangeMin()));
                    }
                    double minPct = SettingMath.valueToPercent(rangeSet.getMinimum(), rangeSet.getMaximum(), rangeSet.getRangeMin());
                    double maxPct = SettingMath.valueToPercent(rangeSet.getMinimum(), rangeSet.getMaximum(), rangeSet.getRangeMax());
                    String valStr = String.format("%.1f - %.1f", rangeSet.getRangeMin(), rangeSet.getRangeMax());
                    tahoma12.draw(valStr, x + width - 6 * s - tahoma12.width(valStr), currentY, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
                    currentY += 8 * s;
                    drawRectWH(x + 6 * s, currentY, width - 12 * s, 4 * s, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
                    RenderUtil.drawGradientRect(x + 6.5f * s + (float) ((width - 13f * s) * minPct), currentY + 0.5f * s, (float) ((width - 13f * s) * (maxPct - minPct)), 3f * s, ColorUtil.withAlpha(new Color(accentColor).darker().getRGB(), globalAlpha / 255f), ColorUtil.withAlpha(accentColor, globalAlpha / 255f));
                    drawRectWH(x + 6 * s + (float) ((width - 12f * s) * minPct) - 1f * s, currentY - 1, 2f * s, 6f * s, ColorUtil.withAlpha(accentColor, globalAlpha / 255f));
                    drawRectWH(x + 6 * s + (float) ((width - 12f * s) * maxPct) - 1f * s, currentY - 1, 2f * s, 6f * s, ColorUtil.withAlpha(accentColor, globalAlpha / 255f));
                    currentY += 8 * s;
                } else if (setting instanceof ModeValue) {
                    ModeValue modeSet = (ModeValue) setting;
                    boolean open = openDropdown == modeSet;
                    tahoma12.draw(setting.getName(), x + 6 * s, currentY, ColorUtil.withAlpha(new Color(220, 220, 220).getRGB(), globalAlpha / 255f));
                    currentY += 8 * s;
                    // closed combo box (reference: 40x9 ref box, border, arrow at right)
                    drawRectWH(x + 6 * s - 0.5f * s, currentY - 0.5f * s, (width - 12 * s) + 1f * s, 10 * s + 1f * s, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
                    RenderUtil.drawGradientRect(x + 6.5f * s, currentY + 0.5f * s, width - 13f * s, 9f * s, ColorUtil.withAlpha(new Color(31, 31, 31).getRGB(), globalAlpha / 255f), ColorUtil.withAlpha(new Color(36, 36, 36).getRGB(), globalAlpha / 255f));
                    tahoma12.draw(modeSet.getValue(), x + 8 * s, currentY + 3f * s, ColorUtil.withAlpha(new Color(151, 151, 151).getRGB(), globalAlpha / 255f));
                    // little arrow, flips when open
                    float ax = x + width - 12 * s;
                    float ay = currentY + 4.5f * s;
                    int arrC = ColorUtil.withAlpha(new Color(open ? 220 : 151, open ? 220 : 151, open ? 220 : 151).getRGB(), globalAlpha / 255f);
                    if (!open) {
                        drawRectWH(ax - 1.5f * s, ay, 3f * s, 1f * s, arrC);
                        drawRectWH(ax - 1f * s, ay + 1f * s, 2f * s, 1f * s, arrC);
                        drawRectWH(ax - 0.5f * s, ay + 2f * s, 1f * s, 1f * s, arrC);
                    } else {
                        drawRectWH(ax - 0.5f * s, ay, 1f * s, 1f * s, arrC);
                        drawRectWH(ax - 1f * s, ay + 1f * s, 2f * s, 1f * s, arrC);
                        drawRectWH(ax - 1.5f * s, ay + 2f * s, 3f * s, 1f * s, arrC);
                    }
                    // box (10) + 2px gap so the selector never touches the setting below
                    currentY += 12 * s;
                    if (open) {
                        List<String> modes = modeSet.getModes();
                        // expanded option list under the box
                        float listH = modes.size() * 9 * s;
                        drawRectWH(x + 6 * s - 0.5f * s, currentY + 1 * s - 0.5f * s, (width - 12 * s) + 1f * s, listH + 1f * s, ColorUtil.withAlpha(borderOuter, globalAlpha / 255f));
                        RenderUtil.drawGradientRect(x + 6.5f * s, currentY + 1.5f * s, width - 13f * s, listH - 1f * s, ColorUtil.withAlpha(new Color(31, 31, 31).getRGB(), globalAlpha / 255f), ColorUtil.withAlpha(new Color(36, 36, 36).getRGB(), globalAlpha / 255f));
                        float oy = currentY + 2f * s;
                        for (String mode : modes) {
                            boolean sel = mode.equals(modeSet.getValue());
                            tahoma12.draw(mode, x + 9 * s, oy + 1f * s, ColorUtil.withAlpha(sel ? accentColor : new Color(255, 255, 255).getRGB(), globalAlpha / 255f));
                            oy += 9 * s;
                        }
                        currentY += listH + 2 * s;
                    }
                }
            }
        }

        private double differentialSliderValue(Value<?> setting, int mouseX, double scale) {
            double deltaPx = (mouseX - anchoredMouseX) / scale;
            float trackW = width - 12 * (float) scale;
            double newPercent = anchoredValue + deltaPx / (trackW / scale);
            if (setting instanceof NumberValue) {
                NumberValue n = (NumberValue) setting;
                return SettingMath.percentToValue(n.getMinimum(), n.getMaximum(), n.getIncrement(), newPercent);
            } else if (setting instanceof RangeValue) {
                RangeValue r = (RangeValue) setting;
                return SettingMath.percentToValue(r.getMinimum(), r.getMaximum(), r.getIncrement(), newPercent);
            }
            return 0;
        }

        public boolean click(int mouseX, int mouseY, int button, double scale) {
            float s = (float) scale;
            if (GUIUtil.mouseOver(x, y - 6 * s, width, 12 * s, mouseX, mouseY)) {
                if (button == 0) {
                    module.toggle();
                } else if (button == 1) {
                    isBinding = !isBinding;
                }
                return true;
            }
            float currentY = y + 10 * s;
            for (Value<?> setting : module.getSettings()) {
                if (!setting.isVisible()) continue;

                if (setting instanceof BooleanValue) {
                    if (GUIUtil.mouseOver(x + 6 * s, currentY, width - 12 * s, 10 * s, mouseX, mouseY) && button == 0) {
                        ((BooleanValue) setting).toggle();
                        return true;
                    }
                    currentY += REF_BOOL_HEIGHT * s;
                } else if (setting instanceof NumberValue) {
                    double trackTop = currentY + 8 * s;
                    if (button == 0 && mouseY >= trackTop && GUIUtil.mouseOver(x + 6 * s, currentY, width - 12 * s, REF_NUM_RANGE_HEIGHT * s, mouseX, mouseY)) {
                        draggingSlider = setting;
                        NumberValue n = (NumberValue) setting;
                        float trackW = width - 12 * s;
                        n.setValue(SettingMath.percentToValue(n.getMinimum(), n.getMaximum(), n.getIncrement(), (mouseX - (x + 6 * s)) / trackW));
                        anchoredValue = SettingMath.valueToPercent(n.getMinimum(), n.getMaximum(), n.getValue());
                        anchoredMouseX = mouseX;
                        return true;
                    }
                    currentY += REF_NUM_RANGE_HEIGHT * s;
                } else if (setting instanceof RangeValue) {
                    double trackTop = currentY + 8 * s;
                    if (button == 0 && mouseY >= trackTop && GUIUtil.mouseOver(x + 6 * s, currentY, width - 12 * s, REF_NUM_RANGE_HEIGHT * s, mouseX, mouseY)) {
                        draggingSlider = setting;
                        RangeValue r = (RangeValue) setting;
                        float trackW = width - 12 * s;
                        float trackStart = x + 6 * s;
                        draggingRangeHandle = SettingMath.nearestHandle(r, trackStart, trackW, mouseX);
                        double val = SettingMath.percentToValue(r.getMinimum(), r.getMaximum(), r.getIncrement(), (mouseX - trackStart) / trackW);
                        if (draggingRangeHandle == 0) r.setRangeMin(Math.min(val, r.getRangeMax()));
                        else r.setRangeMax(Math.max(val, r.getRangeMin()));
                        double handleVal = draggingRangeHandle == 0 ? r.getRangeMin() : r.getRangeMax();
                        anchoredValue = SettingMath.valueToPercent(r.getMinimum(), r.getMaximum(), handleVal);
                        anchoredMouseX = mouseX;
                        return true;
                    }
                    currentY += REF_NUM_RANGE_HEIGHT * s;
                } else if (setting instanceof ModeValue) {
                    ModeValue modeSet = (ModeValue) setting;
                    float boxTop = currentY + 8 * s;
                    if (GUIUtil.mouseOver(x + 6 * s, boxTop, width - 12 * s, 10 * s, mouseX, mouseY)) {
                        openDropdown = openDropdown == modeSet ? null : modeSet;
                        return true;
                    }
                    currentY += REF_MODE_HEIGHT * s;
                    if (openDropdown == modeSet) {
                        List<String> modes = modeSet.getModes();
                        int count = modes.size();
                        float listH = count * 9 * s + 2 * s;
                        if (GUIUtil.mouseOver(x + 6 * s, currentY, width - 12 * s, listH, mouseX, mouseY)) {
                            if (button == 0) {
                                int idx = (int) ((mouseY - (currentY + 2 * s)) / (9 * s));
                                idx = Math.max(0, Math.min(count - 1, idx));
                                modeSet.setValue(modes.get(idx));
                            }
                            openDropdown = null;
                            return true;
                        }
                        currentY += listH;
                    }
                }
            }
            return false;
        }

        public void release() {
            draggingSlider = null;
        }

        public void keyTyped(char typedChar, int keyCode) {
            if (isBinding) {
                if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_SPACE) {
                    module.setKey(Keyboard.KEY_NONE);
                } else if (keyCode != 0) {
                    module.setKey(keyCode);
                }
                isBinding = false;
            }
        }
    }
}
