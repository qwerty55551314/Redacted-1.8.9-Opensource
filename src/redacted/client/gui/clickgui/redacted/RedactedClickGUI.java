package redacted.client.gui.clickgui.redacted;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import redacted.client.Client;
import redacted.client.font.Fonts;
import redacted.client.font.ZoomAwareFont;
import redacted.client.font.ZoomContext;
import redacted.client.font.Weight;
import redacted.client.gui.config.GuiConfigManager;
import redacted.client.gui.edit.GuiEditHud;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.render.ClickGUI;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.font.Font;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.InputField;
import redacted.client.util.gui.ScrollUtil;
import redacted.client.util.render.RenderUtil;
import redacted.client.util.render.StencilUtil;
import redacted.client.value.Value;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.CleanerGridValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.HotbarValue;
import redacted.client.value.impl.ItemGridValue;
import redacted.client.value.impl.ItemPicker;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.client.value.impl.RangeValue;
import redacted.client.value.impl.StringValue;

import java.io.IOException;
import java.text.Collator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RedactedClickGUI extends GuiScreen {
    private static final double REFERENCE_HEIGHT = 540.0;
    private static final double REF_GUI_WIDTH = 530.0;
    private static final double REF_GUI_HEIGHT = 345.0;
    private static final double REF_SIDEBAR_WIDTH = 115.0;
    private static final double REF_CONTENT_PADDING = 15.0;
    private static final double REF_CATEGORY_WIDTH = 92.0;
    private static final double REF_CATEGORY_HEIGHT = 23.0;
    private static final double REF_CATEGORY_SPACING = 27.0;
    private static final double REF_MODULE_HEIGHT = 40.0;
    private static final double REF_MODULE_SPACING = 46.0;
    private static final double REF_SETTING_HEIGHT = 24.0;
    private static final double REF_DRAG_HEIGHT = 24.0;
    private static final double REF_SLIDER_WIDTH = 130.0;
    private static final double REF_SLIDER_TRACK_H = 2.0;
    private static final double REF_SCROLL_STEP = 30.0;
    private static final double REF_SEARCH_HEIGHT = 26.0;

    // dark black palette (matches GuiTeamManager / GuiAltManager)
    private static final int BG_BASE      = 0xFF0A0A0A;
    private static final int BG_PANE      = 0xFF0F0F0F;
    private static final int BG_ROW       = 0xFF141414;
    private static final int BG_HOVER     = 0xFF1A1A1A;
    private static final int BG_INPUT     = 0xFF111111;
    private static final int BG_BUTTON    = 0xFF222222;
    private static final int BG_BUTTON_HV = 0xFF333333;
    private static final int BORDER_DEF   = 0xFF333333;
    private static final int BORDER_FOCUS = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SEC     = 0xFF888888;
    private static final int TEXT_FAINT   = 0xFF555555;
    private static final int TEXT_ACCENT  = 0xFF8888FF;
    private static final int SHADOW_CLR   = 0x30000000;
    private static final int HOVER_SOFT   = 0x33FFFFFF;
    private static final int SETTING_HIT_BG = 0xFF1E1E2E; // subtle purple tint when search matches a setting

    private static final int ACCENT_CLR  = 0xFFAA88FF;

    // quick-pick swatches shown above the HSB square in every ColorValue picker (shared across modules)
    private static final int[] PALETTE = {
        0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000,
        0xFFFF5555, 0xFFFF8000, 0xFFFFD700, 0xFFFFFF55,
        0xFF55FF55, 0xFF00FFAA, 0xFF55FFFF, 0xFF5588FF,
        0xFFAA88FF, 0xFFFF55FF, 0xFFFF55AA, 0xFF8B4513,
    };

    // AutoHotBar widget accents
    private static final int EXCLUDED_CLR     = 0xFFB05050;   // muted red: item clicked-off (excluded from its category)
    private static final int CAT_CLR          = 0xFF66B8FF;   // teal: a whole-category slot binding
    private static final int EXCLUDE_OVERLAY  = 0xC8121212;   // dim wash drawn over excluded item icons

    // Cleaner mode badge colors (Drop reuses the muted red above): Keep one = green, Ignore = grey
    private static final int CLEAN_KEEP_CLR   = 0xFF6FCF6F;

    private static final ResourceLocation ICON_SETTINGS = new ResourceLocation("minecraft", "redacted/clickgui/settings.png");
    private static final ResourceLocation ICON_CHECK = new ResourceLocation("minecraft", "redacted/clickgui/check.png");

    private final Animation openAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    // window resizes re-run initGui -- only play the open zoom on the first init
    private boolean animatedIn;
    private final ScrollUtil scrollUtil = new ScrollUtil();

    private double x = 100, y = 100;
    private double dragX, dragY;
    private boolean dragging;
    private double dragUserScale = 1.0;   // user scale frozen at the start of a slider/range drag

    private Category selectedCategory = Category.COMBAT;
    private final Set<Module> expandedModules = new HashSet<>();

    private NumberValue draggingSlider = null;
    private RangeValue draggingRange = null;
    private int draggingRangeHandle = 0;
    private double anchoredValue;
    private double anchoredMouseX;
    private Module bindingModule = null;

    private String searchQuery = "";
    private boolean searchFocused;
    private int searchCursorCounter;
    // held-backspace delete cadence: the lwjglx shim's GLFW repeat delivery is unreliable on some
    // setups, so updateScreen() re-fires the delete while the key is held (OS repeats reset the
    // timer, so when they DO arrive this stays quiet and the fast native cadence wins).
    private static final long BACKSPACE_REPEAT_MS = 90L;
    private long backspaceLastMs;
    private StringValue editingString = null;
    private final InputField stringInput = new InputField();

    // AutoHotBar widget state
    private ItemPicker searchingPicker = null;    // which picker widget's category search box has focus (HotbarValue or CleanerGridValue)
    private ItemPicker scrollPicker = null;       // picker widget whose open category grid the wheel scrolls (last-frame hover)
    private HotbarValue draggingHotbar = null;    // widget we're dragging an item out of (AutoHotBar only)
    private String draggingKey = null;            // the item key being dragged to a slot
    private HotbarValue geomHotbar = null;        // last drawn open widget -> its panel origin for release hit-tests
    private double geomCpX, geomCpY, geomCpW, geomScale;

    private final HashMap<BooleanValue, Double> boolAnimations = new HashMap<>();

    // hovered element's help text, captured during the draw passes and rendered last (on top, unclipped)
    private String tooltipText;

    // screen-fit factor only. the user "Scale" is applied separately as a zoom about the panel
    // centre (see drawScreen), so the panel grows/shrinks in place instead of walking toward 0,0.
    private double getScale() {
        return new ScaledResolution(mc).getScaledHeight() / REFERENCE_HEIGHT;
    }

    private double getUserScale() {
        final ClickGUI cg = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        return cg != null ? cg.getScale() : 1.0;
    }

    // map a raw cursor coord back through the user-scale zoom (pivoted on the panel centre) so the
    // hit-tests, which run on the unzoomed fit-space layout, line up with what's drawn.
    private int unzoomX(final int mx) {
        final double us = (draggingSlider != null || draggingRange != null) ? dragUserScale : getUserScale();
        final double cx = (x + REF_GUI_WIDTH / 2.0) * getScale();
        return (int) Math.round((mx - cx) / us + cx);
    }

    private int unzoomY(final int my) {
        final double us = (draggingSlider != null || draggingRange != null) ? dragUserScale : getUserScale();
        final double cy = (y + REF_GUI_HEIGHT / 2.0) * getScale();
        return (int) Math.round((my - cy) / us + cy);
    }

    private boolean isRounded() {
        final ClickGUI cg = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        return cg == null || cg.isRounded();
    }

    private double rad(final double r, final double scale) {
        return isRounded() ? r * scale : 0;
    }

    @Override
    public void initGui() {
        final double scale = getScale();
        // LWJGL2-style key repeats are off by default in the lwjglx shim; without this, holding
        // backspace in the search box deletes one character and stops (GLFW repeat events are dropped).
        Keyboard.enableRepeatEvents(true);
        final double[] pos = Client.getInstance().getThemeManager().getRedactedClickGuiPosition();
        if (pos != null) {
            x = pos[0];
            y = pos[1];
            validatePosition(scale);
        }
        final String cat = Client.getInstance().getThemeManager().getRedactedCategory();
        if (cat != null) {
            for (final Category c : Category.values()) {
                if (c.name().equalsIgnoreCase(cat)) { selectedCategory = c; break; }
            }
        }
        clampPosition(scale);
        if (animatedIn) {
            openAnimation.setValue(1);
        } else {
            openAnimation.setValue(0);
            openAnimation.reset();
            animatedIn = true;
        }
        dragging = false;
        draggingSlider = null;
        draggingRange = null;
        bindingModule = null;
        boolAnimations.clear();
        expandedModules.clear();
        final List<String> saved = Client.getInstance().getThemeManager().getRedactedExpanded();
        if (saved != null) {
            for (final Module m : Client.getInstance().getModuleManager().getModules()) {
                if (saved.contains(m.getName())) expandedModules.add(m);
            }
        }
        final Double scroll = Client.getInstance().getThemeManager().getRedactedScroll();
        if (scroll != null) scrollUtil.setTarget(scroll);

        searchQuery = "";
        searchFocused = false;
        searchCursorCounter = 0;
        editingString = null;
        stringInput.setFocused(false);
        searchingPicker = null;
        scrollPicker = null;
        draggingHotbar = null;
        draggingKey = null;

        // restore last search if "Remember Search" is enabled
        final ClickGUI cg = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        if (cg != null && cg.isRememberSearch()) {
            searchQuery = restoreSearchQuery();
        }
    }

    // cursor blink advances on the 20 Hz tick (like vanilla GuiTextField) instead of once per
    // frame -- at high framerates the old per-frame counter made the | stroke strobe.
    @Override
    public void updateScreen() {
        searchCursorCounter++;
        repeatBackspaceIfHeld();
    }

    // fallback key-repeat for the search/input fields: while backspace is physically held, keep
    // deleting every BACKSPACE_REPEAT_MS. keyTyped() resets the timer on every event it receives,
    // so a working OS/shim repeat stream keeps this from double-firing.
    private void repeatBackspaceIfHeld() {
        if (!Keyboard.isKeyDown(Keyboard.KEY_BACK)) return;
        final long now = System.currentTimeMillis();
        if (now - backspaceLastMs < BACKSPACE_REPEAT_MS) return;
        backspaceLastMs = now;

        if (editingString != null) {
            stringInput.textboxKeyTyped((char) 0, Keyboard.KEY_BACK);
        } else if (searchingPicker != null) {
            final String cur = searchingPicker.getSearch();
            if (!cur.isEmpty()) searchingPicker.setSearch(cur.substring(0, cur.length() - 1));
        } else if (searchFocused && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, final float partialTicks) {
        final double scale = getScale();
        // during a slider/range drag, render with the SAME frozen zoom the hit-tests use
        // (dragUserScale) -- otherwise the first value change rescales the panel under a
        // cursor mapping that no longer matches what's drawn, and the slider handle
        // slides away from the cursor (drag direction decides whether it amplifies)
        final boolean draggingValue = draggingSlider != null || draggingRange != null;
        final double us = draggingValue ? dragUserScale : getUserScale();
        // the tooltip is drawn after popMatrix (raw screen space), so keep the untransformed cursor
        final int rawMouseX = mouseX, rawMouseY = mouseY;
        // everything else hit-tests the centre-zoomed panel - map the cursor back through that zoom
        mouseX = unzoomX(mouseX);
        mouseY = unzoomY(mouseY);
        tooltipText = null;   // re-captured each frame by whatever the cursor is over
        openAnimation.run(1.0);
        final double openScale = openAnimation.getValue();

        GlStateManager.pushMatrix();
        final double cx = (x + REF_GUI_WIDTH / 2.0) * scale;
        final double cy = (y + REF_GUI_HEIGHT / 2.0) * scale;
        GlStateManager.translate(cx, cy, 0);
        // open pop (openScale 0->1) folded together with the user zoom, both about the panel centre
        GlStateManager.scale(openScale * us, openScale * us, 1.0);
        GlStateManager.translate(-cx, -cy, 0);
        // zoom-aware fonts re-rasterize at round(size * us) and draw counter-scaled (MatHax
        // pattern), so text stays 1:1 texel-per-pixel at any Scale setting instead of being
        // resampled by this matrix. Only once the animation settles -- sweeping it would
        // churn the per-size font cache for a 250ms cosmetic zoom.
        if (openScale >= 0.995 && Math.abs(us - 1.0) >= 0.02) {
            // quantize the published zoom to 0.05 steps (MatHax bucket pattern): each
            // step builds one glyph set, so sweeping the Scale slider costs a handful of
            // rasterizations instead of one per 1/1000th of zoom (which stalled frames
            // and ate drag events). The residual 1/eff draw scale uses the same quantized
            // value, so the tiny bucket error cancels exactly.
            ZoomContext.set(cx, cy, Math.round(us * 20.0) / 20.0);
        } else {
            ZoomContext.clear();
        }

        if (dragging) {
            x = (mouseX - dragX) / scale;
            y = (mouseY - dragY) / scale;
            clampPosition(scale);
        }

        final double guiW = REF_GUI_WIDTH * scale;
        final double guiH = REF_GUI_HEIGHT * scale;
        final double sbW = REF_SIDEBAR_WIDTH * scale;
        final double px = x * scale;
        final double py = y * scale;
        final ClickGUI cg = Client.getInstance().getModuleManager().getModule(ClickGUI.class);

        // shadow / panel
        // shadow commented out - user prefers no drop shadow behind the panel
        // if (openScale > 0.95) {
        //     RenderUtil.drawRoundedRect(px - 2, py - 2, guiW + 4, guiH + 4, rad(12, scale), SHADOW_CLR);
        // }
        RenderUtil.drawRoundedRect(px, py, guiW, guiH, rad(10, scale), BG_PANE);
        RenderUtil.drawRoundedRect(px, py, sbW, guiH, rad(10, scale), BG_BASE);
        RenderUtil.drawGradientRect(px + sbW - 2 * scale, py, 2 * scale, guiH, 0x1A000000, 0x00000000);

        // title
        try {
            final Font f = new ZoomAwareFont(Math.max(8, (int) Math.round(32 * scale)), Weight.REGULAR, (s, w) -> Fonts.TITAN_ONE.get(s, w));
            f.draw("Redacted", (float) (px + 12 * scale), (float) (py + 14 * scale), TEXT_PRIMARY);
            new ZoomAwareFont(Math.max(8, (int) Math.round(16 * scale)), Weight.REGULAR, (s, w) -> Fonts.TITAN_ONE.get(s, w))
                .draw("0.1", (float) (px + 12 * scale + f.width("Redacted") + 1 * scale),
                    (float) (py + 14 * scale), TEXT_SEC);
        } catch (final Exception ignored) {}

        // categories
        double catY = py + 62 * scale;
        final double catW = REF_CATEGORY_WIDTH * scale;
        final double catH = REF_CATEGORY_HEIGHT * scale;
        final double catSpacing = REF_CATEGORY_SPACING * scale;
        for (final Category cat : Category.values()) {
            final boolean sel = cat == selectedCategory;
            final boolean hover = GUIUtil.mouseOver(px + 10 * scale, catY, catW, catH, mouseX, mouseY);
            if (sel) {
                RenderUtil.drawRoundedRect(px + 10 * scale, catY, catW, catH, rad(5, scale), BG_HOVER);
            }
            final String name = cat.name().substring(0, 1) + cat.name().substring(1).toLowerCase();
            try {
                final Font cf = new ZoomAwareFont(Math.max(8, (int) Math.round(16 * scale)), Weight.REGULAR, (s, w) -> cg != null ? cg.getSidebarFont(s, w) : Fonts.MAIN.get(s, w));
                // category icon glyph (same Snipeware set as the dropdown GUI) + name.
                // DEVELOPER/COSMETICS have no glyph but keep the same text inset so the
                // sidebar stays aligned. Icons are slightly larger and lower than the text.
                final Font iconF = new ZoomAwareFont(Math.max(8, (int) Math.round(19 * scale)), Weight.REGULAR, (s, w) -> Fonts.SNIPA_ICONS.get(s, w));
                final String glyph = categoryGlyph(cat);
                if (glyph != null) {
                    iconF.drawWithShadow(glyph, (float) (px + 16 * scale), (float) (catY + 7.5f * scale), sel ? TEXT_PRIMARY : (hover ? TEXT_SEC : TEXT_FAINT));
                }
                cf.draw(name, (float) (px + 32 * scale), (float) (catY + 8 * scale), sel ? TEXT_PRIMARY : (hover ? TEXT_SEC : TEXT_FAINT));
            } catch (final Exception ignored) {}
            catY += catSpacing;
        }

        // edit GUI button
        final double editH = 32 * scale;
        final double editY = py + guiH - editH - 10 * scale;
        final double editX = px + 10 * scale;
        final boolean editHov = GUIUtil.mouseOver(editX, editY, catW, editH, mouseX, mouseY);
        RenderUtil.drawRoundedRect(editX, editY, catW, editH, rad(5, scale), editHov ? BG_HOVER : BG_ROW);
        try {
            final Font bf = new ZoomAwareFont(Math.max(8, (int) Math.round(20 * scale)), Weight.REGULAR, (s, w) -> cg != null ? cg.getSidebarFont(s, w) : Fonts.MAIN.get(s, w));
            final float tw = bf.width("Edit GUI");
            bf.draw("Edit GUI", (float) (editX + catW / 2 - tw / 2), (float) (editY + editH / 2 - bf.height() / 2 + 2 * scale), editHov ? TEXT_PRIMARY : TEXT_SEC);
        } catch (final Exception ignored) {}

        // config button (above edit GUI)
        final double cfgH = 32 * scale;
        final double cfgY = editY - cfgH - 6 * scale;
        final boolean cfgHov = GUIUtil.mouseOver(editX, cfgY, catW, cfgH, mouseX, mouseY);
        RenderUtil.drawRoundedRect(editX, cfgY, catW, cfgH, rad(5, scale), cfgHov ? BG_HOVER : BG_ROW);
        try {
            final Font bf = new ZoomAwareFont(Math.max(8, (int) Math.round(20 * scale)), Weight.REGULAR, (s, w) -> cg != null ? cg.getSidebarFont(s, w) : Fonts.MAIN.get(s, w));
            final float tw = bf.width("Config");
            bf.draw("Config", (float) (editX + catW / 2 - tw / 2), (float) (cfgY + cfgH / 2 - bf.height() / 2 + 2 * scale), cfgHov ? TEXT_PRIMARY : TEXT_SEC);
        } catch (final Exception ignored) {}

        // scroll (route the wheel to an open category grid if the cursor was over it last frame)
        final int contentSz = Math.max(8, (int) Math.round(16 * scale));
        final int descSz = Math.max(8, (int) Math.round(14 * scale));
        final Font contentFont = new ZoomAwareFont(contentSz, Weight.REGULAR, (s, w) -> cg != null ? cg.getSelectedFont(s, w) : Fonts.MAIN.get(s, w));
        final Font descFont = new ZoomAwareFont(descSz, Weight.REGULAR, (s, w) -> cg != null ? cg.getSelectedFont(s, w) : Fonts.MAIN.get(s, w));
        final double modX = contentX(scale);
        final double moduleW = moduleWidth(scale);

        final List<Module> modules = visibleModules();

        double totalH = 0;
        for (final Module m : modules) {
            totalH += REF_MODULE_SPACING * scale;
            // search auto-expands: while a query is active, matching modules show their settings
            if (expandedModules.contains(m) || isSearchAutoExpanded(m)) {
                for (final Value<?> s : m.getSettings()) {
                    if (!s.isVisible()) continue;
                    totalH += settingHeight(scale);
                    if (s instanceof ColorValue && ((ColorValue) s).isPickerOpen()) {
                        totalH += 180 * scale;
                    }
                    if (s instanceof ItemGridValue && ((ItemGridValue) s).isGridOpen()) {
                        totalH += itemGridHeight((ItemGridValue) s, scale);
                    }
                    if (s instanceof HotbarValue && ((HotbarValue) s).isWidgetOpen()) {
                        totalH += hbHeight((HotbarValue) s, scale);
                    }
                    if (s instanceof CleanerGridValue && ((CleanerGridValue) s).isWidgetOpen()) {
                        totalH += cgHeight((CleanerGridValue) s, scale);
                    }
                }
            }
        }
        scrollUtil.setMax(-totalH + REF_GUI_HEIGHT * scale - 24 * scale - REF_SEARCH_HEIGHT * scale - 4 * scale);

        final int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            if (scrollPicker != null) scrollPicker.setScroll(scrollPicker.getScroll() + (wheel > 0 ? -1 : 1));
            else scrollUtil.setTarget(scrollUtil.getTarget() + (wheel > 0 ? REF_SCROLL_STEP : -REF_SCROLL_STEP));
        }
        scrollUtil.onRender();
        scrollPicker = null;   // re-set during this frame's render if a grid is still hovered

        // search bar (drawn above the module clip so scrolled rows can't cover it)
        final double searchH = REF_SEARCH_HEIGHT * scale;
        final double searchY = (y + 9) * scale;
        RenderUtil.drawRoundedRect(modX, searchY, moduleW, searchH, rad(5, scale), BG_INPUT);
        RenderUtil.drawRoundedOutline(modX, searchY, moduleW, searchH, rad(5, scale), 1.0f, searchFocused ? BORDER_FOCUS : BORDER_DEF);
        final String searchDisplay = searchQuery.isEmpty() && !searchFocused ? "Search modules..." : searchQuery;
        final int searchColor = searchQuery.isEmpty() && !searchFocused ? TEXT_FAINT : TEXT_PRIMARY;
        contentFont.draw(searchDisplay, modX + 8 * scale, searchY + (searchH - contentFont.height()) / 2.0 + 2 * scale, searchColor);
        if (searchFocused && (searchCursorCounter / 6) % 2 == 0) {
            // thin vanilla-sized caret: 6px tall, 2px below the text baseline
            RenderUtil.drawRect(modX + 8 * scale + contentFont.width(searchQuery), searchY + (searchH - 8 * scale) / 2.0 + 2 * scale, 1, 6 * scale, TEXT_PRIMARY);
        }

        // clip the module list to the area below the search bar - square top so the first row corner isn't cut
        final double listTop = searchY + searchH + 9 * scale;
        StencilUtil.initStencilToWrite();
        RenderUtil.drawRoundedRect(px + REF_SIDEBAR_WIDTH * scale + 8 * scale, listTop,
                guiW - REF_SIDEBAR_WIDTH * scale - 13 * scale, py + guiH - 6 * scale - listTop, 0, -1);
        StencilUtil.readStencilBuffer(1);

        final double modH = REF_MODULE_HEIGHT * scale;
        final double modSpacing = REF_MODULE_SPACING * scale;
        // +2 inset: the stencil clip starts at listTop, and a row drawn exactly there
        // loses its top rounded border to the clip (visible at higher scales)
        double modY = listTop + 2 * scale + scrollUtil.getScroll();

        for (final Module module : modules) {
            final boolean hovered = GUIUtil.mouseOver(modX, modY, moduleW, modH, mouseX, mouseY);
            // no tooltip for the module row itself: its description is already printed
            // under the name -- redundant. Setting descriptions still get one.
            final boolean active = module.isEnabled() || module instanceof ClickGUI;

            // hover lighting like config/edit GUI buttons: fill BG_HOVER instead of
            // a white outline (the old HOVER_SOFT ring)
            RenderUtil.drawRoundedRect(modX, modY, moduleW, modH, rad(6, scale), hovered ? BG_HOVER : BG_ROW);

            if (active) {
                RenderUtil.drawRoundedOutline(modX, modY, moduleW, modH, rad(6, scale), (float) (1.5 * scale), BORDER_FOCUS);
            }

            // module label + description
            contentFont.drawWithShadow(module.getName(), modX + 10 * scale, modY + 8 * scale, active ? TEXT_PRIMARY : TEXT_SEC);
            descFont.drawWithShadow(module.getDescription(), modX + 10 * scale, modY + 25 * scale, TEXT_FAINT);

            // keybind text
            final String keyName = module.getKey() == 0 ? "NONE" : Keyboard.getKeyName(module.getKey());
            contentFont.drawRight("[" + (bindingModule == module ? "..." : keyName) + "]", modX + moduleW - 8 * scale, modY + 14 * scale, TEXT_FAINT);

            // settings icon for modules with settings
            if (!module.getSettings().isEmpty()) {
                drawSmoothIcon(ICON_SETTINGS, modX + moduleW - 18 * scale, modY + 26 * scale, 10 * scale, 10 * scale, TEXT_FAINT);
            }

            modY += modSpacing;

            // expanded settings
            if (expandedModules.contains(module) || isSearchAutoExpanded(module)) {
                double setY = modY - 2 * scale;
                // owners = settings that something else depends on; render them as headers and indent their children
                final Set<Value<?>> owners = new HashSet<>();
                for (final Value<?> v : module.getSettings()) {
                    if (v.getParent() != null) owners.add(v.getParent());
                }
                boolean drewSetting = false;
                for (final Value<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) continue;
                    final double setH = settingHeight(scale);

                    final boolean isOwner = setting instanceof BooleanValue && owners.contains(setting);
                    int depth = 0;
                    for (Value<?> p = setting.getParent(); p != null; p = p.getParent()) depth++;
                    final double indent = depth * 10 * scale;

                    if (GUIUtil.mouseOver(modX + 5 * scale, setY, moduleW - 10 * scale, setH - 4 * scale, mouseX, mouseY)
                            && !setting.getDescription().isEmpty()) {
                        tooltipText = setting.getDescription();
                    }

                    // search highlight: when a query is active and this setting matches,
                    // paint an accent outline + brighter label so the found setting pops
                    final boolean settingHit = !searchQuery.isEmpty() && settingMatchesSearch(setting, searchQuery.toLowerCase());

                    // thin divider above each owner (drawn in the gap, no added height) to break up the sections
                    if (isOwner && drewSetting) {
                        RenderUtil.drawRect(modX + 8 * scale, setY - 1 * scale, moduleW - 16 * scale, Math.max(1.0, scale), BORDER_DEF);
                    }

                    RenderUtil.drawRoundedRect(modX + 5 * scale + indent, setY, moduleW - 10 * scale - indent, setH - 4 * scale, rad(4, scale), settingHit ? SETTING_HIT_BG : BG_INPUT);
                    if (settingHit) {
                        RenderUtil.drawRoundedOutline(modX + 5 * scale + indent, setY, moduleW - 10 * scale - indent, setH - 4 * scale, rad(4, scale), 1.0f, ACCENT_CLR);
                    }
                    if (isOwner) {
                        RenderUtil.drawRect(modX + 5 * scale, setY, 2 * scale, setH - 4 * scale, ACCENT_CLR);
                    }
                    contentFont.drawWithShadow(setting.getName(), modX + 12 * scale + indent, setY + 6 * scale, settingHit ? TEXT_ACCENT : (isOwner ? TEXT_PRIMARY : TEXT_SEC));
                    drewSetting = true;

                    if (setting instanceof BooleanValue) {
                        final BooleanValue b = (BooleanValue) setting;
                        double anim = boolAnimations.getOrDefault(b, b.getValue() ? 1.0 : 0.0);
                        final double target = b.getValue() ? 1.0 : 0.0;
                        anim += (target - anim) * 0.15;
                        boolAnimations.put(b, anim);

                        if (module.usesCheckboxes()) {
                            // Gothaj-style checkbox (EntityESP sub-features): dark box, center-scale
                            // accent fill, CENTERED check image. 13px box slightly smaller for a subtler look
                            final double cbS = 13 * scale;
                            final double cbX = modX + moduleW - 27 * scale;
                            final double cbY = setY + (setH - 4 * scale - cbS) / 2.0;
                            final double cbCx = cbX + cbS / 2.0;
                            final double cbCy = cbY + cbS / 2.0;
                            final double checkS = 7 * scale;

                            RenderUtil.drawRoundedRect(cbX, cbY, cbS, cbS, rad(3, scale), BG_INPUT);
                            RenderUtil.drawRoundedOutline(cbX, cbY, cbS, cbS, rad(3, scale), 1.0f, BORDER_DEF);

                            if (anim > 0.02) {
                                final double half = cbS / 2.0 * anim;   // center-scale fill (Gothaj)
                                RenderUtil.drawRoundedRect(cbCx - half, cbCy - half, half * 2, half * 2, rad(3, scale), ACCENT_CLR);
                                GlStateManager.color(1.0F, 1.0F, 1.0F, (float) anim);
                                drawSmoothIcon(ICON_CHECK, cbCx - checkS / 2.0, cbCy - checkS / 2.0, checkS, checkS);
                                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                            }
                        } else {
                            // Snipeware-style pill toggle (every other module keeps this)
                            final double tX = modX + moduleW - 36 * scale;
                            final double tW = 22 * scale;
                            final double tH = 11 * scale;
                            final double tY = setY + 4.5 * scale;
                            final double tR = tH / 2.0;

                            final int trackOff = BG_ROW;
                            final int trackOn = ACCENT_CLR;
                            final int trackR = (int) (((trackOff >> 16) & 0xFF) + (((trackOn >> 16) & 0xFF) - ((trackOff >> 16) & 0xFF)) * anim);
                            final int trackG = (int) (((trackOff >> 8) & 0xFF) + (((trackOn >> 8) & 0xFF) - ((trackOff >> 8) & 0xFF)) * anim);
                            final int trackB = (int) ((trackOff & 0xFF) + ((trackOn & 0xFF) - (trackOff & 0xFF)) * anim);
                            RenderUtil.drawRoundedRect(tX, tY, tW, tH, rad(tR, 1), 0xFF000000 | (trackR << 16) | (trackG << 8) | trackB);

                            final double thumbR = 4 * scale;
                            final double thumbX = tX + tR + (tW - tR * 2) * anim;
                            final double thumbY = tY + tR;
                            RenderUtil.drawFilledCircle(thumbX, thumbY, thumbR, TEXT_PRIMARY);
                        }
                    } else if (setting instanceof ModeValue) {
                        final String modeText = ((ModeValue) setting).getValue();
                        contentFont.drawRight(modeText, modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);
                    } else if (setting instanceof ColorValue) {
                        final ColorValue cv = (ColorValue) setting;
                        final double swX = modX + moduleW - 24 * scale;
                        RenderUtil.drawRect(swX, setY + 4 * scale, 16 * scale, 12 * scale, 0xFF000000 | cv.getRGB());
                        RenderUtil.drawRoundedOutline(swX, setY + 4 * scale, 16 * scale, 12 * scale, 0, 1.0f, cv.isPickerOpen() ? BORDER_FOCUS : BORDER_DEF);

                        if (cv.isPickerOpen()) {
                            final double cpX = modX + 5 * scale;
                            final double cpY = setY + settingHeight(scale) + 2 * scale;
                            final double cpW = moduleW - 10 * scale;
                            final double cpH = 180 * scale;
                            final double sqS = Math.min(100 * scale, cpW - 30 * scale);

                            RenderUtil.drawRoundedRect(cpX, cpY, cpW, cpH, rad(6, scale), BG_ROW);
                            RenderUtil.drawRoundedOutline(cpX, cpY, cpW, cpH, rad(6, scale), 1.0f, BORDER_DEF);

                            // preset swatch row -- one click sets the color; HSB square below stays for custom picks
                            for (int pi = 0; pi < PALETTE.length; pi++) {
                                final double pcx = cpX + IG_PAD * scale + pi * (IG_CELL * scale + IG_GAP * scale);
                                final double pcy = cpY + 8 * scale;
                                final boolean sel = (PALETTE[pi] & 0xFFFFFF) == (cv.getValue() & 0xFFFFFF);
                                RenderUtil.drawRoundedRect(pcx, pcy, IG_CELL * scale, IG_CELL * scale, rad(3, scale), 0xFF000000 | PALETTE[pi]);
                                RenderUtil.drawRoundedOutline(pcx, pcy, IG_CELL * scale, IG_CELL * scale, rad(3, scale), 1.0f, sel ? ACCENT_CLR : BORDER_DEF);
                            }

                            final double sqX = cpX + 10 * scale;
                            final double sqY = cpY + 46 * scale;
                            for (int sy = 0; sy < (int) sqS; sy++) {
                                for (int sx = 0; sx < (int) sqS; sx++) {
                                    final float sat = sx / (float) sqS;
                                    final float val = 1.0f - sy / (float) sqS;
                                    final int col = java.awt.Color.HSBtoRGB(cv.getHue(), sat, val);
                                    RenderUtil.drawRect(sqX + sx, sqY + sy, 1, 1, 0xFF000000 | col);
                                }
                            }
                            RenderUtil.drawRoundedOutline(sqX, sqY, sqS, sqS, 0, 1.0f, BORDER_DEF);
                            final double indX = sqX + cv.getSat() * sqS;
                            final double indY = sqY + (1.0 - cv.getVal()) * sqS;
                            RenderUtil.drawCircleOutline(indX, indY, 3 * scale, (float) (1.5 * scale), TEXT_PRIMARY);

                            final double barX = sqX + sqS + 5 * scale;
                            final double barW = 12 * scale;
                            for (int i = 0; i < (int) sqS; i++) {
                                final float t = i / (float) sqS;
                                final int col = java.awt.Color.HSBtoRGB(t, 1.0f, 1.0f);
                                RenderUtil.drawRect(barX, sqY + i, barW, 1, 0xFF000000 | col);
                            }
                            RenderUtil.drawRoundedOutline(barX, sqY, barW, sqS, 0, 1.0f, BORDER_DEF);
                            final double thumbY = sqY + cv.getHue() * sqS - 2 * scale;
                            RenderUtil.drawRoundedRect(barX - 1 * scale, thumbY, barW + 2 * scale, 4 * scale, rad(2, scale), TEXT_PRIMARY);

                            // RGB readout
                            final int rgb = java.awt.Color.HSBtoRGB(cv.getHue(), cv.getSat(), cv.getVal());
                            final String rgbStr = String.format("#%06X", rgb & 0xFFFFFF);
                            descFont.drawCentered(rgbStr, cpX + cpW / 2.0, cpY + cpH - 14 * scale, TEXT_FAINT);
                        }
                    } else if (setting instanceof StringValue) {
                        if (editingString == setting) {
                            final String sv = editingString.getValue();
                            RenderUtil.drawRoundedRect(modX + moduleW - 120 * scale, setY + 3 * scale, 102 * scale, setH - 10 * scale, rad(3, scale), BG_INPUT);
                            RenderUtil.drawRoundedOutline(modX + moduleW - 120 * scale, setY + 3 * scale, 102 * scale, setH - 10 * scale, rad(3, scale), 1.0f, BORDER_FOCUS);
                            contentFont.draw(sv, modX + moduleW - 116 * scale, setY + 6 * scale, TEXT_PRIMARY);
                            if ((searchCursorCounter / 6) % 2 == 0) {
                                RenderUtil.drawRect(modX + moduleW - 116 * scale + contentFont.width(sv), setY + (setH - 8 * scale) / 2.0, 1, 8 * scale, TEXT_PRIMARY);
                            }
                        } else {
                            String display = ((StringValue) setting).getValue();
                            if (display.length() > 16) display = display.substring(0, 14) + "..";
                            contentFont.drawRight(display, modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);
                        }
                    } else if (setting instanceof NumberValue) {
                        final NumberValue n = (NumberValue) setting;
                        contentFont.drawRight(String.format("%.1f", n.getValue()), modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);

                        final double sW = REF_SLIDER_WIDTH * scale;
                        final double sX = sliderX(modX, scale);
                        final double sY = setY + 10 * scale;
                        RenderUtil.drawRoundedRect(sX, sY, sW, Math.max(1.0, REF_SLIDER_TRACK_H * scale), rad(1, scale), BORDER_DEF);

                        final double pct = Math.max(0.0, Math.min(1.0, (n.getValue() - n.getMinimum()) / (n.getMaximum() - n.getMinimum())));
                        RenderUtil.drawRoundedRect(sX, sY, sW * pct, Math.max(1.0, REF_SLIDER_TRACK_H * scale), rad(1, scale), ACCENT_CLR);
                        RenderUtil.drawFilledCircle(sX + sW * pct, sY + Math.max(1.0, REF_SLIDER_TRACK_H * scale) / 2.0, Math.max(1.5, 2.0 * scale), TEXT_PRIMARY);

                        if (draggingSlider == n) n.setValue(differentialSliderValue(n, mouseX, scale));
                    } else if (setting instanceof RangeValue) {
                        final RangeValue r = (RangeValue) setting;
                        contentFont.drawRight(String.format("%.1f - %.1f", r.getRangeMin(), r.getRangeMax()), modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);

                        final double sW = REF_SLIDER_WIDTH * scale;
                        final double sX = sliderX(modX, scale);
                        final double sY = setY + 10 * scale;
                        RenderUtil.drawRoundedRect(sX, sY, sW, Math.max(1.0, REF_SLIDER_TRACK_H * scale), rad(1, scale), BORDER_DEF);

                        final double span = r.getMaximum() - r.getMinimum();
                        final double minP = span == 0 ? 0 : Math.max(0.0, Math.min(1.0, (r.getRangeMin() - r.getMinimum()) / span));
                        final double maxP = span == 0 ? 0 : Math.max(0.0, Math.min(1.0, (r.getRangeMax() - r.getMinimum()) / span));
                        RenderUtil.drawRoundedRect(sX + sW * minP, sY, sW * (maxP - minP), Math.max(1.0, REF_SLIDER_TRACK_H * scale), rad(1, scale), ACCENT_CLR);
                        RenderUtil.drawFilledCircle(sX + sW * minP, sY + Math.max(1.0, REF_SLIDER_TRACK_H * scale) / 2.0, Math.max(1.5, 2.0 * scale), TEXT_PRIMARY);
                        RenderUtil.drawFilledCircle(sX + sW * maxP, sY + Math.max(1.0, REF_SLIDER_TRACK_H * scale) / 2.0, Math.max(1.5, 2.0 * scale), TEXT_PRIMARY);

                        if (draggingRange == r) {
                            final double v = differentialSliderValue(r, mouseX, scale);
                            if (draggingRangeHandle == 0) r.setRangeMin(Math.min(v, r.getRangeMax()));
                            else r.setRangeMax(Math.max(v, r.getRangeMin()));
                        }
                    } else if (setting instanceof ItemGridValue) {
                        final ItemGridValue grid = (ItemGridValue) setting;
                        int selCount = 0;
                        for (final ItemGridValue.Entry e : grid.getEntries()) if (e.isEnabled()) selCount++;
                        contentFont.drawRight(selCount + "/" + grid.getEntries().size(), modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);
                        if (grid.isGridOpen()) {
                            drawItemGrid(grid, modX, moduleW, setY, mouseX, mouseY, scale);
                        }
                    } else if (setting instanceof HotbarValue) {
                        final HotbarValue hbv = (HotbarValue) setting;
                        contentFont.drawRight(hbv.isWidgetOpen() ? "[-]" : "[+]", modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);
                        if (hbv.isWidgetOpen()) {
                            drawHotbar(hbv, modX, moduleW, setY, mouseX, mouseY, scale);
                        }
                    } else if (setting instanceof CleanerGridValue) {
                        final CleanerGridValue cgv = (CleanerGridValue) setting;
                        contentFont.drawRight(cgv.isWidgetOpen() ? "[-]" : "[+]", modX + moduleW - 18 * scale, setY + 6 * scale, TEXT_ACCENT);
                        if (cgv.isWidgetOpen()) {
                            drawCleaner(cgv, modX, moduleW, setY, mouseX, mouseY, scale);
                        }
                    }
                    setY += setH;
                    if (setting instanceof ColorValue && ((ColorValue) setting).isPickerOpen()) {
                        setY += 180 * scale;
                    }
                    if (setting instanceof ItemGridValue && ((ItemGridValue) setting).isGridOpen()) {
                        setY += itemGridHeight((ItemGridValue) setting, scale);
                    }
                    if (setting instanceof HotbarValue && ((HotbarValue) setting).isWidgetOpen()) {
                        setY += hbHeight((HotbarValue) setting, scale);
                    }
                    if (setting instanceof CleanerGridValue && ((CleanerGridValue) setting).isWidgetOpen()) {
                        setY += cgHeight((CleanerGridValue) setting, scale);
                    }
                }
                modY = setY + 2 * scale;
            }
        }

        StencilUtil.uninitStencilBuffer();

        // AutoHotBar drag ghost: the item being dragged from a category grid, following the cursor
        if (draggingHotbar != null && draggingKey != null) {
            final ItemStack ghost = draggingHotbar.iconForKey(draggingKey);
            if (ghost != null) {
                beginItems();
                itemIcon(ghost, mouseX, mouseY, scale);
                endItems();
            }
        }

        GlStateManager.popMatrix();
        // tooltip + anything after this draws in RAW screen space -- clear the zoom
        // context so zoom-aware fonts stop counter-scaling outside the zoom matrix
        ZoomContext.clear();
        // item rendering toggles depth off (see endItems); restore it so anything drawn after the
        // GUI this frame (overlays, tooltips) isn't left without a depth test
        GlStateManager.enableDepth();

        // tooltip last: outside the stencil clip + open-zoom matrix, so it overflows the panel and
        // sits on top. drawn in raw scaled-resolution space, where mouseX/mouseY live. The zoom
        // context is cleared here, so the font must be sized for the VISUAL scale (scale * us)
        // directly -- passing the zoomed-space descFont renders it at base size (tiny).
        final int tipSz = Math.max(8, (int) Math.round(14 * scale * us));
        final Font tipFont = new ZoomAwareFont(tipSz, Weight.REGULAR,
                (s, w) -> cg != null ? cg.getSelectedFont(s, w) : Fonts.MAIN.get(s, w));
        drawTooltip(tooltipText, rawMouseX, rawMouseY, scale * us, tipFont);
    }

    // small themed help box that follows the cursor. supports \n and word-wraps long lines (Font has
    // no wrap of its own). clamped to the screen so it never spills off an edge.
    private void drawTooltip(final String text, final double mouseX, final double mouseY, final double scale, final Font font) {
        if (text == null || text.isEmpty()) return;

        final double maxW = 200 * scale;
        final List<String> lines = new java.util.ArrayList<>();
        for (final String para : text.split("\n", -1)) {
            if (para.isEmpty()) { lines.add(""); continue; }
            String line = "";
            for (final String word : para.split(" ")) {
                final String trial = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(trial) > maxW) {
                    lines.add(line);
                    line = word;
                } else {
                    line = trial;
                }
            }
            if (!line.isEmpty()) lines.add(line);
        }
        if (lines.isEmpty()) return;

        final double pad = 6 * scale;
        final double lineH = font.height() + 2 * scale;
        double textW = 0;
        for (final String l : lines) textW = Math.max(textW, font.width(l));
        final double boxW = textW + pad * 2;
        final double boxH = lines.size() * lineH + pad * 2;

        double bx = mouseX + 10 * scale;
        double by = mouseY + 10 * scale;
        if (bx + boxW > width) bx = mouseX - boxW - 6 * scale;
        if (by + boxH > height) by = height - boxH - 2 * scale;
        if (bx < 0) bx = 2 * scale;
        if (by < 0) by = 2 * scale;

        RenderUtil.drawRoundedRect(bx, by, boxW, boxH, rad(4, scale), BG_ROW);
        RenderUtil.drawRoundedOutline(bx, by, boxW, boxH, rad(4, scale), 1.0f, BORDER_DEF);
        double ty = by + pad + 1 * scale;
        for (final String l : lines) {
            font.drawWithShadow(l, bx + pad, ty, TEXT_PRIMARY);
            ty += lineH;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, final int mouseButton) throws IOException {
        mouseX = unzoomX(mouseX);
        mouseY = unzoomY(mouseY);
        dragUserScale = getUserScale();   // freeze the zoom mapping for any drag this click starts
        final double scale = getScale();
        final double px = x * scale;
        final double py = y * scale;
        final double guiW = REF_GUI_WIDTH * scale;
        final double guiH = REF_GUI_HEIGHT * scale;

        // search bar click (checked BEFORE the drag strip: the field spans ref-rows 9..35 and
        // the strip covers 0..24, so without this ordering clicks on the field's upper half started
        // window drags instead of focusing)
        final double modX = contentX(scale);
        final double moduleW = moduleWidth(scale);
        final double searchH = REF_SEARCH_HEIGHT * scale;
        final double searchY = (y + 9) * scale;
        if (GUIUtil.mouseOver(modX, searchY, moduleW, searchH, mouseX, mouseY)) {
            searchFocused = true;
            searchCursorCounter = 0;
            bindingModule = null;
            return;
        }

        if (GUIUtil.mouseOver(px, py, guiW, REF_DRAG_HEIGHT * scale, mouseX, mouseY)) {
            dragging = true;
            dragX = mouseX - px;
            dragY = mouseY - py;
            bindingModule = null;
            return;
        }
        searchFocused = false;
        editingString = null;
        stringInput.setFocused(false);
        searchingPicker = null;

        double catY = py + 62 * scale;
        final double catW = REF_CATEGORY_WIDTH * scale;
        final double catH = REF_CATEGORY_HEIGHT * scale;
        for (final Category cat : Category.values()) {
            if (GUIUtil.mouseOver(px + 10 * scale, catY, catW, catH, mouseX, mouseY)) {
                selectedCategory = cat;
                scrollUtil.setTarget(0);
                expandedModules.clear();
                searchQuery = "";
                bindingModule = null;
                return;
            }
            catY += REF_CATEGORY_SPACING * scale;
        }

        // config button sits one button-height above edit GUI; mirror the render geometry (32h) so the hitbox lines up
        final double cfgEditY = py + guiH - 32 * scale - 10 * scale;
        final double cfgH = 32 * scale;
        final double cfgY = cfgEditY - cfgH - 6 * scale;
        if (GUIUtil.mouseOver(px + 10 * scale, cfgY, catW, cfgH, mouseX, mouseY)) {
            mc.displayGuiScreen(new GuiConfigManager(this));
            return;
        }

        final double editH = 28 * scale;
        final double editY = py + guiH - editH - 10 * scale;
        if (GUIUtil.mouseOver(px + 10 * scale, editY, catW, editH, mouseX, mouseY)) {
            mc.displayGuiScreen(new GuiEditHud());
            return;
        }

        // must mirror the render pass: searchY = (y+9)*scale, listTop gap 9*scale,
        // first-row inset 2*scale
        double modY = (y + 9) * scale + REF_SEARCH_HEIGHT * scale + 9 * scale + 2 * scale + scrollUtil.getScroll();
        final double modH = REF_MODULE_HEIGHT * scale;
        final double modSpacing = REF_MODULE_SPACING * scale;
        final List<Module> modules = visibleModules();

        // set when the click landed inside an open picker's panel; a click nothing else consumed
        // then dismisses the pickers instead of doing nothing
        boolean clickInsidePickerPanel = false;

        for (final Module module : modules) {
            if (GUIUtil.mouseOver(modX, modY, moduleW, modH, mouseX, mouseY)) {
                if (mouseButton == 0) {
                    // the gear icon (drawn for modules with settings) expands/collapses the settings;
                    // the rest of the row toggles the module. the gear was purely decorative before,
                    // which made the settings panel undiscoverable.
                    final boolean onGear = !module.getSettings().isEmpty()
                            && GUIUtil.mouseOver(modX + moduleW - 26 * scale, modY + 20 * scale, 22 * scale, 20 * scale, mouseX, mouseY);
                    if (onGear) {
                        if (expandedModules.contains(module)) expandedModules.remove(module);
                        else expandedModules.add(module);
                    } else {
                        module.toggle();
                    }
                } else if (mouseButton == 1) {
                    if (expandedModules.contains(module)) expandedModules.remove(module);
                    else expandedModules.add(module);
                } else if (mouseButton == 2) bindingModule = module;
                return;
            }
            modY += modSpacing;

            if (expandedModules.contains(module)) {
                double setY = modY - 2 * scale;
                for (final Value<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) continue;
                    final double setH = settingHeight(scale);
                    if (GUIUtil.mouseOver(modX + 5 * scale, setY, moduleW - 10 * scale, setH - 4 * scale, mouseX, mouseY)) {
                        bindingModule = null;
                        if (setting instanceof BooleanValue) {
                            ((BooleanValue) setting).toggle();
                        } else if (setting instanceof ModeValue) {
                            final ModeValue m = (ModeValue) setting;
                            if (mouseButton == 0) m.cycle();
                            else m.cycleBackwards();
                        } else if (setting instanceof ColorValue) {
                            // the whole row opens/closes the picker -- the tiny 16x12 swatch used to be
                            // the ONLY close target, so a click anywhere else left it looking stuck
                            final ColorValue cv = (ColorValue) setting;
                            cv.setPickerOpen(!cv.isPickerOpen());
                            if (cv.isPickerOpen()) cv.syncFromValue();
                        } else if (setting instanceof StringValue) {
                            if (mouseButton == 0) {
                                editingString = (StringValue) setting;
                                searchFocused = false;
                                searchCursorCounter = 0;
                                stringInput.setText(editingString.getValue());
                                stringInput.setFocused(true);
                            } else if (mouseButton == 2) {
                                ((StringValue) setting).setValue("");
                                if (editingString == setting) stringInput.setText("");
                            }
                        } else if (setting instanceof NumberValue) {
                            final NumberValue n = (NumberValue) setting;
                            if (mouseButton == 0 && mouseY >= setY + 8 * scale) {
                                final double sW = REF_SLIDER_WIDTH * scale;
                                final double sX = sliderX(modX, scale);
                                final double pct = Math.max(0.0, Math.min(1.0, (mouseX - sX) / sW));
                                final double val = n.getMinimum() + pct * (n.getMaximum() - n.getMinimum());
                                n.setValue(Math.round(val / n.getIncrement()) * n.getIncrement());
                                draggingSlider = n;
                                anchoredValue = (n.getValue() - n.getMinimum()) / (n.getMaximum() - n.getMinimum());
                                anchoredMouseX = mouseX;
                            }
                        } else if (setting instanceof RangeValue) {
                            final RangeValue r = (RangeValue) setting;
                            if (mouseButton == 0 && mouseY >= setY + 8 * scale) {
                                final double sW = REF_SLIDER_WIDTH * scale;
                                final double sX = sliderX(modX, scale);
                                final double span = r.getMaximum() - r.getMinimum();
                                final double minPx = sX + sW * (span == 0 ? 0 : (r.getRangeMin() - r.getMinimum()) / span);
                                final double maxPx = sX + sW * (span == 0 ? 0 : (r.getRangeMax() - r.getMinimum()) / span);
                                draggingRangeHandle = Math.abs(mouseX - minPx) <= Math.abs(mouseX - maxPx) ? 0 : 1;
                                final double pct = Math.max(0.0, Math.min(1.0, (mouseX - sX) / sW));
                                final double val = r.getMinimum() + pct * (r.getMaximum() - r.getMinimum());
                                final double snapped = Math.round(val / r.getIncrement()) * r.getIncrement();
                                if (draggingRangeHandle == 0) r.setRangeMin(Math.min(snapped, r.getRangeMax()));
                                else r.setRangeMax(Math.max(snapped, r.getRangeMin()));
                                draggingRange = r;
                                anchoredValue = ((draggingRangeHandle == 0 ? r.getRangeMin() : r.getRangeMax()) - r.getMinimum()) / span;
                                anchoredMouseX = mouseX;
                            }
                        } else if (setting instanceof ItemGridValue) {
                            final ItemGridValue grid = (ItemGridValue) setting;
                            grid.setGridOpen(!grid.isGridOpen());
                        } else if (setting instanceof HotbarValue) {
                            final HotbarValue hbv = (HotbarValue) setting;
                            hbv.setWidgetOpen(!hbv.isWidgetOpen());
                        } else if (setting instanceof CleanerGridValue) {
                            final CleanerGridValue cgv = (CleanerGridValue) setting;
                            cgv.setWidgetOpen(!cgv.isWidgetOpen());
                        }
                        return;
                    }
                    setY += setH;
                    if (setting instanceof ColorValue && ((ColorValue) setting).isPickerOpen()) {
                        final ColorValue cv = (ColorValue) setting;
                        final double cpX = modX + 5 * scale;
                        final double cpY = setY + 2 * scale;
                        final double cpW = moduleW - 10 * scale;
                        if (GUIUtil.mouseOver(cpX, cpY, cpW, 180 * scale, mouseX, mouseY)) clickInsidePickerPanel = true;
                        final double sqS = Math.min(100 * scale, cpW - 30 * scale);
                        final double sqX = cpX + 10 * scale;
                        final double sqY = cpY + 46 * scale;
                        final double barX = sqX + sqS + 5 * scale;
                        final double barW = 12 * scale;

                        for (int pi = 0; pi < PALETTE.length; pi++) {
                            final double pcx = cpX + IG_PAD * scale + pi * (IG_CELL * scale + IG_GAP * scale);
                            final double pcy = cpY + 8 * scale;
                            if (GUIUtil.mouseOver(pcx, pcy, IG_CELL * scale, IG_CELL * scale, mouseX, mouseY)) {
                                cv.setValue(0xFF000000 | PALETTE[pi]);
                                cv.syncFromValue();
                                return;
                            }
                        }

                        if (GUIUtil.mouseOver(sqX, sqY, sqS, sqS, mouseX, mouseY)) {
                            cv.setDraggingSquare(true);
                            final float sat = (float) Math.max(0, Math.min(1, (mouseX - sqX) / sqS));
                            final float val = (float) Math.max(0, Math.min(1, 1.0 - (mouseY - sqY) / sqS));
                            cv.setHSB(cv.getHue(), sat, val);
                        } else if (GUIUtil.mouseOver(barX, sqY, barW, sqS, mouseX, mouseY)) {
                            cv.setDraggingHue(true);
                            final float hue = (float) Math.max(0, Math.min(1, (mouseY - sqY) / sqS));
                            cv.setHSB(hue, cv.getSat(), cv.getVal());
                        }
                        setY += 180 * scale;
                    }
                    if (setting instanceof ItemGridValue && ((ItemGridValue) setting).isGridOpen()) {
                        final ItemGridValue grid = (ItemGridValue) setting;
                        final double cpX = modX + 5 * scale;
                        final double cpY = setY + 2 * scale;
                        final double cpW = moduleW - 10 * scale;
                        final int cols = itemGridCols(cpW, scale);
                        final double cell = IG_CELL * scale;
                        final double gap = IG_GAP * scale;
                        final double pad = IG_PAD * scale;
                        final List<ItemGridValue.Entry> entries = grid.getEntries();
                        for (int i = 0; i < entries.size(); i++) {
                            final double cx = cpX + pad + (i % cols) * (cell + gap);
                            final double cy = cpY + pad + (i / cols) * (cell + gap);
                            if (GUIUtil.mouseOver(cx, cy, cell, cell, mouseX, mouseY)) {
                                grid.toggle(entries.get(i));
                                break;
                            }
                        }
                        setY += itemGridHeight(grid, scale);
                    }
                    if (setting instanceof HotbarValue && ((HotbarValue) setting).isWidgetOpen()) {
                        final HotbarValue hbv = (HotbarValue) setting;
                        final double cpX = modX + 5 * scale;
                        final double cpY = setY + 2 * scale;
                        final double cpW = moduleW - 10 * scale;

                        final int cat = hbCategoryAt(hbv, cpX, cpY, scale, mouseX, mouseY);
                        if (cat != -1) {
                            // left = start a whole-category drag (resolved on release: drop on a slot, else open);
                            // right = just open/close the category panel
                            if (mouseButton == 0) { draggingHotbar = hbv; draggingKey = hbv.categoryKey(cat); }
                            else { hbv.setOpenCategory(hbv.getOpenCategory() == cat ? -1 : cat); searchingPicker = null; }
                            return;
                        }
                        if (hbSearchAt(hbv, cpX, cpY, cpW, scale, mouseX, mouseY)) {
                            searchingPicker = hbv;
                            searchCursorCounter = 0;
                            return;
                        }
                        final HotbarValue.Entry ge = hbGridEntryAt(hbv, cpX, cpY, cpW, scale, mouseX, mouseY);
                        if (ge != null) {
                            if (mouseButton == 0) { draggingHotbar = hbv; draggingKey = ge.getKey(); }   // resolved on release
                            else hbv.toggleItem(ge.getKey());
                            return;
                        }
                        final int[] si = hbSlotItemAt(hbv, cpX, cpY, scale, mouseX, mouseY);
                        if (si != null && mouseButton == 1) {
                            hbv.removeFromSlot(si[0], hbv.getSlots().get(si[0]).get(si[1]));
                            return;
                        }
                        setY += hbHeight(hbv, scale);
                    }
                    if (setting instanceof CleanerGridValue && ((CleanerGridValue) setting).isWidgetOpen()) {
                        final CleanerGridValue cgv = (CleanerGridValue) setting;
                        final double cpX = modX + 5 * scale;
                        final double cpY = setY + 2 * scale;
                        final double cpW = moduleW - 10 * scale;

                        final int cat = hbCategoryAt(cgv, cpX, cpY, scale, mouseX, mouseY);
                        if (cat != -1) {
                            // left = cycle the whole-category mode; right = open/close its grid for per-item overrides
                            if (mouseButton == 1) { cgv.setOpenCategory(cgv.getOpenCategory() == cat ? -1 : cat); searchingPicker = null; }
                            else cgv.cycle(HotbarValue.categoryRef(cat));
                            return;
                        }
                        if (hbSearchAt(cgv, cpX, cpY, cpW, scale, mouseX, mouseY)) {
                            searchingPicker = cgv;
                            searchCursorCounter = 0;
                            return;
                        }
                        final HotbarValue.Entry ge = hbGridEntryAt(cgv, cpX, cpY, cpW, scale, mouseX, mouseY);
                        if (ge != null) {
                            // left = cycle a per-item mode forward, right = backward (overrides the category)
                            if (mouseButton == 0) cgv.cycle(ge.getKey());
                            else cgv.cycleBack(ge.getKey());
                            return;
                        }
                        setY += cgHeight(cgv, scale);
                    }
                }
                modY = setY + 2 * scale;
            }
        }

        // reaching this point means no handler consumed the click (dead space around the list)
        // put open pickers away, unless the click landed inside one and its drags/palette own it
        if (!clickInsidePickerPanel) {
            for (final Module m : modules) {
                for (final Value<?> v : m.getSettings()) {
                    if (v instanceof ColorValue) ((ColorValue) v).setPickerOpen(false);
                }
            }
        }
        bindingModule = null;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, final int clickedMouseButton, final long timeSinceLastClick) {
        mouseX = unzoomX(mouseX);
        mouseY = unzoomY(mouseY);
        final double scale = getScale();
        if (draggingSlider != null) draggingSlider.setValue(differentialSliderValue(draggingSlider, mouseX, scale));
        if (draggingRange != null) {
            final double v = differentialSliderValue(draggingRange, mouseX, scale);
            if (draggingRangeHandle == 0) draggingRange.setRangeMin(Math.min(v, draggingRange.getRangeMax()));
            else draggingRange.setRangeMax(Math.max(v, draggingRange.getRangeMin()));
        }

        // ColorValue picker drag (same layout constants as the render pass)
        final double modX = contentX(scale);
        double setY = (y + 9) * scale + REF_SEARCH_HEIGHT * scale + 9 * scale + 2 * scale + scrollUtil.getScroll();
        final double moduleW = moduleWidth(scale);
        final List<Module> modules = visibleModules();
        for (final Module module : modules) {
            setY += REF_MODULE_SPACING * scale;
            if (expandedModules.contains(module)) {
                for (final Value<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) continue;
                    setY += settingHeight(scale);
                    if (setting instanceof ColorValue) {
                        final ColorValue cv = (ColorValue) setting;
                        if (cv.isPickerOpen()) {
                            final double cpX = modX + 5 * scale;
                            final double cpY = setY + 2 * scale;
                            final double cpW = moduleW - 10 * scale;
                            final double sqS = Math.min(100 * scale, cpW - 30 * scale);
                            final double sqX = cpX + 10 * scale;
                            final double sqY = cpY + 46 * scale;
                            final double barX = sqX + sqS + 5 * scale;
                            final double barW = 12 * scale;

                            if (cv.isDraggingSquare()) {
                                final float sat = (float) Math.max(0, Math.min(1, (mouseX - sqX) / sqS));
                                final float val = (float) Math.max(0, Math.min(1, 1.0 - (mouseY - sqY) / sqS));
                                cv.setHSB(cv.getHue(), sat, val);
                            } else if (cv.isDraggingHue()) {
                                final float hue = (float) Math.max(0, Math.min(1, (mouseY - sqY) / sqS));
                                cv.setHSB(hue, cv.getSat(), cv.getVal());
                            }
                            setY += 180 * scale;
                        }
                    }
                    if (setting instanceof ItemGridValue && ((ItemGridValue) setting).isGridOpen()) {
                        setY += itemGridHeight((ItemGridValue) setting, scale);
                    }
                    if (setting instanceof HotbarValue && ((HotbarValue) setting).isWidgetOpen()) {
                        setY += hbHeight((HotbarValue) setting, scale);
                    }
                    if (setting instanceof CleanerGridValue && ((CleanerGridValue) setting).isWidgetOpen()) {
                        setY += cgHeight((CleanerGridValue) setting, scale);
                    }
                }
            }
        }
    }

    private double differentialSliderValue(final Value<?> setting, final int mouseX, final double scale) {
        final double dPx = (mouseX - anchoredMouseX) / scale;
        final double pct = Math.max(0.0, Math.min(1.0, anchoredValue + dPx / REF_SLIDER_WIDTH));
        if (setting instanceof NumberValue) {
            final NumberValue n = (NumberValue) setting;
            final double val = n.getMinimum() + pct * (n.getMaximum() - n.getMinimum());
            return Math.round(val / n.getIncrement()) * n.getIncrement();
        } else if (setting instanceof RangeValue) {
            final RangeValue r = (RangeValue) setting;
            final double val = r.getMinimum() + pct * (r.getMaximum() - r.getMinimum());
            return Math.round(val / r.getIncrement()) * r.getIncrement();
        }
        return 0;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, final int state) {
        mouseX = unzoomX(mouseX);
        mouseY = unzoomY(mouseY);
        // resolve an AutoHotBar drag: drop onto a hotbar slot, else (released on its own cell) toggle it
        if (draggingHotbar != null && draggingKey != null && geomHotbar == draggingHotbar) {
            final int col = hbSlotColAt(draggingHotbar, geomCpX, geomCpY, geomScale, mouseX, mouseY);
            if (col != -1) {
                draggingHotbar.dropOnSlot(col, draggingKey);                  // binds an item OR a whole category
            } else if (HotbarValue.isCategoryKey(draggingKey)) {
                // a category drag that didn't land on a slot -> if released on its own icon, open the category
                final int cat = hbCategoryAt(draggingHotbar, geomCpX, geomCpY, geomScale, mouseX, mouseY);
                if (cat != -1 && draggingHotbar.categoryKey(cat).equals(draggingKey)) {
                    draggingHotbar.setOpenCategory(draggingHotbar.getOpenCategory() == cat ? -1 : cat);
                    searchingPicker = null;
                }
            } else {
                // an item drag that didn't land on a slot -> if released on its own grid cell, toggle exclude
                final HotbarValue.Entry ge = hbGridEntryAt(draggingHotbar, geomCpX, geomCpY, geomCpW, geomScale, mouseX, mouseY);
                if (ge != null && ge.getKey().equals(draggingKey)) draggingHotbar.toggleItem(draggingKey);
            }
        }
        draggingHotbar = null;
        draggingKey = null;

        final boolean was = dragging;
        dragging = false;
        draggingSlider = null;
        draggingRange = null;
        // release all color picker drags
        for (final Module m : Client.getInstance().getModuleManager().getModules()) {
            for (final Value<?> v : m.getSettings()) {
                if (v instanceof ColorValue) {
                    ((ColorValue) v).setDraggingSquare(false);
                    ((ColorValue) v).setDraggingHue(false);
                }
            }
        }
        if (was) savePosition(getScale());
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        savePosition(getScale());
        final java.util.List<String> names = new java.util.ArrayList<>();
        for (final Module m : expandedModules) names.add(m.getName());
        Client.getInstance().getThemeManager().setRedactedExpanded(names);
        Client.getInstance().getThemeManager().setRedactedScroll(scrollUtil.getScroll());
        Client.getInstance().getThemeManager().setRedactedCategory(selectedCategory.name());

        // persist last search if "Remember Search" is enabled
        final ClickGUI cg = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        if (cg != null && cg.isRememberSearch()) {
            saveSearchQuery(searchQuery);
        } else {
            saveSearchQuery("");
        }
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        // Ctrl+F / Cmd+F -> focus search bar
        if (keyCode == Keyboard.KEY_F && GuiScreen.isCtrlKeyDown()) {
            searchFocused = true;
            searchCursorCounter = 0;
            return;
        }

        if (editingString != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editingString = null;
                stringInput.setFocused(false);
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) backspaceLastMs = System.currentTimeMillis();
            if (stringInput.textboxKeyTyped(typedChar, keyCode)) editingString.setValue(stringInput.getText());
            return;
        }

        if (searchingPicker != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) { searchingPicker = null; return; }
            final String cur = searchingPicker.getSearch();
            if (keyCode == Keyboard.KEY_BACK) {
                if (!cur.isEmpty()) searchingPicker.setSearch(cur.substring(0, cur.length() - 1));
                backspaceLastMs = System.currentTimeMillis();
                return;
            }
            if (keyCode == Keyboard.KEY_DELETE) { searchingPicker.setSearch(""); return; }
            if (GuiScreen.isKeyComboCtrlV(keyCode)) { searchingPicker.setSearch(cur + GuiScreen.getClipboardString()); return; }
            if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) { searchingPicker.setSearch(cur + typedChar); return; }
            return;
        }

        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchFocused = false;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (GuiScreen.isCtrlKeyDown()) {
                    // Ctrl+Backspace clears the whole query (matches the dropdown GUI)
                    searchQuery = "";
                } else if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                backspaceLastMs = System.currentTimeMillis();
                return;
            }
            if (GuiScreen.isKeyComboCtrlV(keyCode)) {
                searchQuery += GuiScreen.getClipboardString();
                return;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                searchQuery += typedChar;
                return;
            }
            return;
        }

        if (bindingModule != null) {
            bindingModule.setKey(keyCode == 1 ? 0 : keyCode);
            bindingModule = null;
            return;
        }
        if (keyCode == 1 || Client.getInstance().getModuleManager().getModule(ClickGUI.class).shouldCloseOn(keyCode)) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) mc.setIngameFocus();
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    /** Snipeware category glyph, matching DropdownClickGUI's icon set.
     *  Null for DEVELOPER/COSMETICS - no icon, but the sidebar keeps their text inset. */
    private String categoryGlyph(final Category cat) {
        switch (cat) {
            case COMBAT: return "E";
            case MOVEMENT: return "A";
            case RENDER: return "C";
            case WORLD: return "D";
            case UTILITY: return "B";
            case DEVELOPER: return null;
            case COSMETICS: return null;
            default: return "B";
        }
    }

    private void drawSmoothIcon(final ResourceLocation loc, final double x, final double y, final double w, final double h) {
        // through RenderUtil.drawIcon: full trilinear + mipmaps + LOD bias + device-grid
        // snapping. The old hand-rolled version set only the MIN filter (MAG stayed
        // NEAREST from the upload), had no mipmaps (128px settings.png drawn at ~9px
        // aliased into mush) and drew at fractional row positions, so every instance of
        // the same icon sampled at a different phase and looked different.
        RenderUtil.drawIcon(loc, x, y, w, h, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawSmoothIcon(final ResourceLocation loc, final double x, final double y, final double w, final double h, final int color) {
        final float r = ((color >> 16) & 0xFF) / 255.0F;
        final float g = ((color >> 8) & 0xFF) / 255.0F;
        final float b = (color & 0xFF) / 255.0F;
        RenderUtil.drawIcon(loc, x, y, w, h, r, g, b, 1.0F);
    }

    // item-grid sub-panel (ItemGridValue)
    private static final double IG_CELL = 18.0;   // cell box (16px icon + 1px frame each side)
    private static final double IG_ICON = 16.0;
    private static final double IG_GAP = 3.0;
    private static final double IG_PAD = 6.0;     // panel inner padding

    private int itemGridCols(final double cpW, final double scale) {
        final double cell = IG_CELL * scale;
        final double gap = IG_GAP * scale;
        final double inner = cpW - 2 * IG_PAD * scale;
        return Math.max(1, (int) Math.floor((inner + gap) / (cell + gap)));
    }

    private double itemGridHeight(final ItemGridValue grid, final double scale) {
        final double cpW = moduleWidth(scale) - 10 * scale;
        final int cols = itemGridCols(cpW, scale);
        final int rows = Math.max(1, (grid.getEntries().size() + cols - 1) / cols);
        return IG_PAD * scale * 2 + rows * (IG_CELL * scale) + (rows - 1) * (IG_GAP * scale);
    }

    private void drawItemGrid(final ItemGridValue grid, final double modX, final double moduleW,
                              final double rowY, final int mouseX, final int mouseY, final double scale) {
        final double cpX = modX + 5 * scale;
        final double cpY = rowY + settingHeight(scale) + 2 * scale;
        final double cpW = moduleW - 10 * scale;
        final double cpH = itemGridHeight(grid, scale);

        RenderUtil.drawRoundedRect(cpX, cpY, cpW, cpH, rad(6, scale), BG_ROW);
        RenderUtil.drawRoundedOutline(cpX, cpY, cpW, cpH, rad(6, scale), 1.0f, BORDER_DEF);

        final int cols = itemGridCols(cpW, scale);
        final double cell = IG_CELL * scale;
        final double gap = IG_GAP * scale;
        final double pad = IG_PAD * scale;
        final List<ItemGridValue.Entry> entries = grid.getEntries();

        // cell frames first
        for (int i = 0; i < entries.size(); i++) {
            final double cx = cpX + pad + (i % cols) * (cell + gap);
            final double cy = cpY + pad + (i / cols) * (cell + gap);
            final boolean on = entries.get(i).isEnabled();
            final boolean hover = GUIUtil.mouseOver(cx, cy, cell, cell, mouseX, mouseY);
            if (hover) {
                final ItemStack ic = entries.get(i).getIcon();
                tooltipText = ic != null ? ic.getDisplayName() : entries.get(i).getName();
            }
            RenderUtil.drawRoundedRect(cx, cy, cell, cell, rad(3, scale), on ? BG_HOVER : BG_INPUT);
            RenderUtil.drawRoundedOutline(cx, cy, cell, cell, rad(3, scale), 1.0f,
                    on ? ACCENT_CLR : (hover ? BORDER_FOCUS : BORDER_DEF));
        }

        // item icons on top -- after the rects so the cell fills don't paint over them
        beginItems();
        for (int i = 0; i < entries.size(); i++) {
            final ItemStack icon = entries.get(i).getIcon();
            if (icon == null) continue;
            final double ccx = cpX + pad + (i % cols) * (cell + gap) + cell / 2.0;
            final double ccy = cpY + pad + (i / cols) * (cell + gap) + cell / 2.0;
            itemIcon(icon, ccx, ccy, scale);
        }
        endItems();
    }

    // shared item-icon GL plumbing. without the rescale-normal/blend/white-color preamble the native
    // icons render invisible against the GUI's 2D rect state (mirrors BlockESP.drawIcon).
    private void beginItems() {
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void endItems() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // render the native 16px icon scaled about (ccx,ccy). call between beginItems()/endItems().
    private void itemIcon(final ItemStack stack, final double ccx, final double ccy, final double scale) {
        if (stack == null) return;
        final int ix = (int) Math.round(ccx) - 8;
        final int iy = (int) Math.round(ccy) - 8;
        GlStateManager.pushMatrix();
        GlStateManager.translate(ccx, ccy, 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(-ccx, -ccy, 0);
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, ix, iy);
        GlStateManager.popMatrix();
    }

    // AutoHotBar widget (HotbarValue)
    private static final double HB_CELL = 18.0;     // category + slot cell box
    private static final double HB_GAP = 3.0;
    private static final double HB_PAD = 6.0;
    private static final double HB_SEARCH = 16.0;   // category search box height
    private static final int HB_VIEW_ROWS = 6;      // visible rows in the open category grid

    private int hbGridCols(final double cpW, final double scale) {
        final double cell = HB_CELL * scale, gap = HB_GAP * scale;
        final double inner = cpW - 2 * HB_PAD * scale;
        return Math.max(1, (int) Math.floor((inner + gap) / (cell + gap)));
    }

    private int hbMaxFallbacks(final HotbarValue hb) {
        int maxF = 0;
        for (final List<String> s : hb.getSlots()) maxF = Math.max(maxF, s.size() - 1);
        return maxF;
    }

    private double hbHeight(final HotbarValue hb, final double scale) {
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        double h = pad + cell + pad;                 // top pad + category icon row + pad
        if (hb.getOpenCategory() >= 0) {
            h += HB_SEARCH * scale + pad;            // search box
            h += HB_VIEW_ROWS * cell + (HB_VIEW_ROWS - 1) * gap + pad;   // grid viewport
        }
        h += cell;                                   // hotbar primary row
        final int maxF = hbMaxFallbacks(hb);
        if (maxF > 0) h += maxF * (cell + gap);      // fallback rows
        return h + pad;
    }

    // start-Y of the hotbar strip (primary row) relative to the panel top cpY
    private double hbStripY(final HotbarValue hb, final double cpY, final double scale) {
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        double y = cpY + pad + cell + pad;
        if (hb.getOpenCategory() >= 0) {
            y += HB_SEARCH * scale + pad + HB_VIEW_ROWS * cell + (HB_VIEW_ROWS - 1) * gap + pad;
        }
        return y;
    }

    // shared picker chrome (HotbarValue + CleanerGridValue draw the same category row + search + grid)
    // draw the category search box: rounded field, placeholder, blinking cursor when focused
    private void drawPickerSearch(final ItemPicker picker, final double cpX, final double searchY,
                                  final double cpW, final double pad, final double scale, final Font font) {
        final boolean focused = searchingPicker == picker;
        RenderUtil.drawRoundedRect(cpX + pad, searchY, cpW - 2 * pad, HB_SEARCH * scale, rad(3, scale), BG_INPUT);
        RenderUtil.drawRoundedOutline(cpX + pad, searchY, cpW - 2 * pad, HB_SEARCH * scale, rad(3, scale), 1.0f, focused ? BORDER_FOCUS : BORDER_DEF);
        final String sv = picker.getSearch();
        final String shown = sv.isEmpty() && !focused ? "Search items..." : sv;
        font.draw(shown, cpX + pad + 4 * scale, searchY + (HB_SEARCH * scale - font.height()) / 2.0, sv.isEmpty() && !focused ? TEXT_FAINT : TEXT_PRIMARY);
        if (focused && (searchCursorCounter / 6) % 2 == 0) {
            RenderUtil.drawRect(cpX + pad + 4 * scale + font.width(sv), searchY + (HB_SEARCH * scale - 8 * scale) / 2.0, 1, 8 * scale, TEXT_PRIMARY);
        }
    }

    // clamp the picker's scroll to its content, persist it, and arm the wheel if the grid viewport is
    // hovered (read next frame). returns the top visible row index.
    private int pickerScrollRow(final ItemPicker picker, final int totalRows, final double gridX, final double gridY,
                               final double viewW, final double viewH, final int mouseX, final int mouseY) {
        final int maxRow = Math.max(0, totalRows - HB_VIEW_ROWS);
        final int rowOffset = (int) Math.max(0, Math.min(maxRow, Math.round(picker.getScroll())));
        picker.setScroll(rowOffset);
        if (maxRow > 0 && GUIUtil.mouseOver(gridX, gridY, viewW, viewH, mouseX, mouseY)) scrollPicker = picker;
        return rowOffset;
    }

    // draw the category-row icons + open-category grid icons in one GL batch (no slot strip).
    // HotbarValue draws its strip icons in a separate batch afterwards.
    private void drawPickerIcons(final List<HotbarValue.Category> cats, final List<HotbarValue.Entry> filtered,
                                final int cols, final int rowOffset, final double cpX, final double catY,
                                final double gridX, final double gridY, final double cell, final double gap,
                                final double pad, final double scale) {
        beginItems();
        for (int i = 0; i < cats.size(); i++) {
            itemIcon(cats.get(i).getIcon(), cpX + pad + i * (cell + gap) + cell / 2.0, catY + cell / 2.0, scale);
        }
        if (filtered != null) {
            for (int r = 0; r < HB_VIEW_ROWS; r++) {
                for (int c = 0; c < cols; c++) {
                    final int idx = (rowOffset + r) * cols + c;
                    if (idx >= filtered.size()) continue;
                    itemIcon(filtered.get(idx).getIcon(), gridX + c * (cell + gap) + cell / 2.0, gridY + r * (cell + gap) + cell / 2.0, scale);
                }
            }
        }
        endItems();
    }

    private void drawHotbar(final HotbarValue hb, final double modX, final double moduleW,
                            final double rowY, final int mouseX, final int mouseY, final double scale) {
        final double cpX = modX + 5 * scale;
        final double cpY = rowY + settingHeight(scale) + 2 * scale;
        final double cpW = moduleW - 10 * scale;
        final double cpH = hbHeight(hb, scale);
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;

        // cache the panel origin so mouseReleased can resolve drop targets without re-walking layout
        geomHotbar = hb; geomCpX = cpX; geomCpY = cpY; geomCpW = cpW; geomScale = scale;
        final Font font = contentFont();

        RenderUtil.drawRoundedRect(cpX, cpY, cpW, cpH, rad(6, scale), BG_ROW);
        RenderUtil.drawRoundedOutline(cpX, cpY, cpW, cpH, rad(6, scale), 1.0f, BORDER_DEF);

        final List<HotbarValue.Category> cats = hb.getCategories();
        final double catY = cpY + pad;

        // category icon row backgrounds
        for (int i = 0; i < cats.size(); i++) {
            final double cx = cpX + pad + i * (cell + gap);
            final boolean open = i == hb.getOpenCategory();
            final boolean hover = GUIUtil.mouseOver(cx, catY, cell, cell, mouseX, mouseY);
            if (hover) tooltipText = cats.get(i).getName();
            RenderUtil.drawRoundedRect(cx, catY, cell, cell, rad(3, scale), open ? BG_HOVER : BG_INPUT);
            RenderUtil.drawRoundedOutline(cx, catY, cell, cell, rad(3, scale), 1.0f,
                    open ? ACCENT_CLR : (hover ? BORDER_FOCUS : BORDER_DEF));
        }

        // open category panel (search + scrollable grid) backgrounds
        int cols = 0;
        double gridX = 0, gridY = 0;
        List<HotbarValue.Entry> filtered = null;
        int rowOffset = 0;
        if (hb.getOpenCategory() >= 0) {
            final double searchY = catY + cell + pad;
            drawPickerSearch(hb, cpX, searchY, cpW, pad, scale, font);

            gridX = cpX + pad;
            gridY = searchY + HB_SEARCH * scale + pad;
            cols = hbGridCols(cpW, scale);
            filtered = hb.getFilteredEntries();
            final int totalRows = Math.max(1, (filtered.size() + cols - 1) / cols);
            final double viewW = cpW - 2 * pad;
            final double viewH = HB_VIEW_ROWS * cell + (HB_VIEW_ROWS - 1) * gap;
            rowOffset = pickerScrollRow(hb, totalRows, gridX, gridY, viewW, viewH, mouseX, mouseY);

            for (int r = 0; r < HB_VIEW_ROWS; r++) {
                for (int c = 0; c < cols; c++) {
                    final int idx = (rowOffset + r) * cols + c;
                    if (idx >= filtered.size()) continue;
                    final double cx = gridX + c * (cell + gap);
                    final double cy = gridY + r * (cell + gap);
                    final boolean excl = hb.isExcluded(filtered.get(idx).getKey());
                    final boolean hover = GUIUtil.mouseOver(cx, cy, cell, cell, mouseX, mouseY);
                    if (hover) tooltipText = filtered.get(idx).displayName();
                    RenderUtil.drawRoundedRect(cx, cy, cell, cell, rad(3, scale), BG_INPUT);
                    RenderUtil.drawRoundedOutline(cx, cy, cell, cell, rad(3, scale), 1.0f, excl ? EXCLUDED_CLR : (hover ? BORDER_FOCUS : BORDER_DEF));
                }
            }
        }

        // hotbar strip backgrounds
        final double stripY = hbStripY(hb, cpY, scale);
        final List<List<String>> slots = hb.getSlots();
        for (int i = 0; i < HotbarValue.SLOTS; i++) {
            final double cx = cpX + pad + i * (cell + gap);
            final List<String> list = slots.get(i);
            if (list.isEmpty()) {
                RenderUtil.drawRoundedRect(cx, stripY, cell, cell, rad(3, scale), BG_INPUT);
                RenderUtil.drawRoundedOutline(cx, stripY, cell, cell, rad(3, scale), 1.0f, BORDER_DEF);
                font.drawCentered("+", cx + cell / 2.0, stripY + cell / 2.0 - font.height() / 2.0, TEXT_FAINT);
            } else {
                for (int k = 0; k < list.size(); k++) {
                    final double cy = stripY + k * (cell + gap);
                    final boolean isCat = HotbarValue.isCategoryKey(list.get(k));
                    if (GUIUtil.mouseOver(cx, cy, cell, cell, mouseX, mouseY)) tooltipText = hotbarTokenLabel(hb, list.get(k));
                    RenderUtil.drawRoundedRect(cx, cy, cell, cell, rad(3, scale), k == 0 ? BG_HOVER : BG_INPUT);
                    RenderUtil.drawRoundedOutline(cx, cy, cell, cell, rad(3, scale), 1.0f, isCat ? CAT_CLR : (k == 0 ? ACCENT_CLR : BORDER_DEF));
                }
            }
        }

        // picker icons (categories + open grid), then the strip icons in their own batch
        drawPickerIcons(cats, filtered, cols, rowOffset, cpX, catY, gridX, gridY, cell, gap, pad, scale);
        beginItems();
        for (int i = 0; i < HotbarValue.SLOTS; i++) {
            final List<String> list = slots.get(i);
            final double cx = cpX + pad + i * (cell + gap) + cell / 2.0;
            for (int k = 0; k < list.size(); k++) {
                itemIcon(hb.iconForKey(list.get(k)), cx, stripY + k * (cell + gap) + cell / 2.0, scale);
            }
        }
        endItems();

        // post-icon overlays (drawn after the GL batch so they sit on top of the icons)
        // dim excluded items in the open category grid
        if (filtered != null) {
            for (int r = 0; r < HB_VIEW_ROWS; r++) {
                for (int c = 0; c < cols; c++) {
                    final int idx = (rowOffset + r) * cols + c;
                    if (idx >= filtered.size() || !hb.isExcluded(filtered.get(idx).getKey())) continue;
                    RenderUtil.drawRoundedRect(gridX + c * (cell + gap), gridY + r * (cell + gap), cell, cell, rad(3, scale), EXCLUDE_OVERLAY);
                }
            }
        }
        // corner dot on whole-category slot cells so they read differently from specific-item cells
        for (int i = 0; i < HotbarValue.SLOTS; i++) {
            final List<String> list = slots.get(i);
            final double cx = cpX + pad + i * (cell + gap);
            for (int k = 0; k < list.size(); k++) {
                if (!HotbarValue.isCategoryKey(list.get(k))) continue;
                RenderUtil.drawRect(cx + cell - 4 * scale, stripY + k * (cell + gap) + 1 * scale, 3 * scale, 3 * scale, CAT_CLR);
            }
        }
    }

    // Cleaner widget (CleanerGridValue)
    // same category row + search + scrollable grid as the AutoHotBar picker (shares the HB_* geometry
    // and the hbCategoryAt/hbSearchAt/hbGridEntryAt hit-tests), but no hotbar strip and no drag: a cell
    // shows its clean mode as a tinted outline + corner badge, and clicking cycles the mode.
    private int modeColor(final CleanerGridValue.CleanerMode m) {
        switch (m) {
            case DROP:     return EXCLUDED_CLR;     // red
            case KEEP_ONE: return CLEAN_KEEP_CLR;   // green
            default:       return TEXT_SEC;         // IGNORE -> grey
        }
    }

    // one-letter badge drawn on the cell so the mode reads at a glance, not by color alone
    private String modeLetter(final CleanerGridValue.CleanerMode m) {
        switch (m) {
            case DROP:     return "D";
            case KEEP_ONE: return "K";
            default:       return "I";   // IGNORE
        }
    }

    // full word for the hover tooltip (null = the item isn't configured)
    private String modeWord(final CleanerGridValue.CleanerMode m) {
        if (m == null) return "Not set";
        switch (m) {
            case DROP:     return "Drop";
            case KEEP_ONE: return "Keep one";
            default:       return "Ignore";
        }
    }

    // mode letter in the cell's top-right corner, with a 1px shadow so it stays legible over the icon
    private void drawModeBadge(final CleanerGridValue.CleanerMode m, final double cellX, final double cellY, final double cell, final double scale) {
        final Font bf = new ZoomAwareFont(Math.max(8, (int) Math.round(9 * scale)), Weight.REGULAR, (s, w) -> Fonts.MAIN.get(s, w));
        final String letter = modeLetter(m);
        final double tx = cellX + cell - 2 * scale;
        final double ty = cellY + 1 * scale;
        bf.drawRight(letter, tx + 0.6 * scale, ty + 0.6 * scale, 0xFF000000);
        bf.drawRight(letter, tx, ty, modeColor(m));
    }

    private double cgHeight(final CleanerGridValue cg, final double scale) {
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        double h = pad + cell;                            // top pad + category icon row
        if (cg.getOpenCategory() >= 0) {
            h += pad + HB_SEARCH * scale;                 // gap + search box
            h += pad + HB_VIEW_ROWS * cell + (HB_VIEW_ROWS - 1) * gap;   // gap + grid viewport
        }
        return h + pad;                                  // bottom pad
    }

    private void drawCleaner(final CleanerGridValue cg, final double modX, final double moduleW,
                             final double rowY, final int mouseX, final int mouseY, final double scale) {
        final double cpX = modX + 5 * scale;
        final double cpY = rowY + settingHeight(scale) + 2 * scale;
        final double cpW = moduleW - 10 * scale;
        final double cpH = cgHeight(cg, scale);
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        final Font font = contentFont();

        RenderUtil.drawRoundedRect(cpX, cpY, cpW, cpH, rad(6, scale), BG_ROW);
        RenderUtil.drawRoundedOutline(cpX, cpY, cpW, cpH, rad(6, scale), 1.0f, BORDER_DEF);

        final List<HotbarValue.Category> cats = cg.getCategories();
        final double catY = cpY + pad;

        // category icon row (outline tinted by the category's mode; the open category gets the accent)
        for (int i = 0; i < cats.size(); i++) {
            final double cx = cpX + pad + i * (cell + gap);
            final boolean open = i == cg.getOpenCategory();
            final boolean hover = GUIUtil.mouseOver(cx, catY, cell, cell, mouseX, mouseY);
            final CleanerGridValue.CleanerMode m = cg.getMode(HotbarValue.categoryRef(i));
            if (hover) tooltipText = cats.get(i).getName() + "\n" + modeWord(m);
            RenderUtil.drawRoundedRect(cx, catY, cell, cell, rad(3, scale), open ? BG_HOVER : BG_INPUT);
            final int outline = open ? ACCENT_CLR : (m != null ? modeColor(m) : (hover ? BORDER_FOCUS : BORDER_DEF));
            RenderUtil.drawRoundedOutline(cx, catY, cell, cell, rad(3, scale), 1.0f, outline);
        }

        // open category panel (search + scrollable grid)
        int cols = 0;
        double gridX = 0, gridY = 0;
        List<HotbarValue.Entry> filtered = null;
        int rowOffset = 0;
        if (cg.getOpenCategory() >= 0) {
            final double searchY = catY + cell + pad;
            drawPickerSearch(cg, cpX, searchY, cpW, pad, scale, font);

            gridX = cpX + pad;
            gridY = searchY + HB_SEARCH * scale + pad;
            cols = hbGridCols(cpW, scale);
            filtered = cg.getFilteredEntries();
            final int totalRows = Math.max(1, (filtered.size() + cols - 1) / cols);
            final double viewW = cpW - 2 * pad;
            final double viewH = HB_VIEW_ROWS * cell + (HB_VIEW_ROWS - 1) * gap;
            rowOffset = pickerScrollRow(cg, totalRows, gridX, gridY, viewW, viewH, mouseX, mouseY);

            for (int r = 0; r < HB_VIEW_ROWS; r++) {
                for (int c = 0; c < cols; c++) {
                    final int idx = (rowOffset + r) * cols + c;
                    if (idx >= filtered.size()) continue;
                    final double cx = gridX + c * (cell + gap);
                    final double cy = gridY + r * (cell + gap);
                    final CleanerGridValue.CleanerMode m = cg.getMode(filtered.get(idx).getKey());
                    final boolean hover = GUIUtil.mouseOver(cx, cy, cell, cell, mouseX, mouseY);
                    if (hover) tooltipText = filtered.get(idx).displayName() + "\n" + modeWord(m);
                    RenderUtil.drawRoundedRect(cx, cy, cell, cell, rad(3, scale), BG_INPUT);
                    RenderUtil.drawRoundedOutline(cx, cy, cell, cell, rad(3, scale), 1.0f, m != null ? modeColor(m) : (hover ? BORDER_FOCUS : BORDER_DEF));
                }
            }
        }

        // icons (categories + open grid) in one GL batch
        drawPickerIcons(cats, filtered, cols, rowOffset, cpX, catY, gridX, gridY, cell, gap, pad, scale);

        // post-icon mode badges (corner square per configured cell, drawn on top of the icons)
        for (int i = 0; i < cats.size(); i++) {
            final CleanerGridValue.CleanerMode m = cg.getMode(HotbarValue.categoryRef(i));
            if (m == null) continue;
            drawModeBadge(m, cpX + pad + i * (cell + gap), catY, cell, scale);
        }
        if (filtered != null) {
            for (int r = 0; r < HB_VIEW_ROWS; r++) {
                for (int c = 0; c < cols; c++) {
                    final int idx = (rowOffset + r) * cols + c;
                    if (idx >= filtered.size()) continue;
                    final CleanerGridValue.CleanerMode m = cg.getMode(filtered.get(idx).getKey());
                    if (m == null) continue;
                    drawModeBadge(m, gridX + c * (cell + gap), gridY + r * (cell + gap), cell, scale);
                }
            }
        }
    }

    private Font contentFont() {
        final ClickGUI cg = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        final int sz = Math.max(8, (int) Math.round(16 * getScale()));
        return new ZoomAwareFont(sz, Weight.REGULAR, (s, w) -> cg != null ? cg.getSelectedFont(s, w) : Fonts.MAIN.get(s, w));
    }

    // readable label for a hotbar slot token (item display name, or the category name for an @cat: binding)
    private String hotbarTokenLabel(final HotbarValue hb, final String key) {
        if (HotbarValue.isCategoryKey(key)) {
            final int idx = hb.categoryIndexOf(key);
            return idx >= 0 ? hb.getCategories().get(idx).getName() : key;
        }
        final ItemStack ic = hb.iconForKey(key);
        return ic != null ? ic.getDisplayName() : key;
    }

    // category index under the cursor in the icon row, or -1
    private int hbCategoryAt(final ItemPicker hb, final double cpX, final double cpY, final double scale, final int mx, final int my) {
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        final double catY = cpY + pad;
        for (int i = 0; i < hb.getCategories().size(); i++) {
            if (GUIUtil.mouseOver(cpX + pad + i * (cell + gap), catY, cell, cell, mx, my)) return i;
        }
        return -1;
    }

    private boolean hbSearchAt(final ItemPicker hb, final double cpX, final double cpY, final double cpW, final double scale, final int mx, final int my) {
        if (hb.getOpenCategory() < 0) return false;
        final double cell = HB_CELL * scale, pad = HB_PAD * scale;
        final double searchY = cpY + pad + cell + pad;
        return GUIUtil.mouseOver(cpX + pad, searchY, cpW - 2 * pad, HB_SEARCH * scale, mx, my);
    }

    // entry under the cursor in the open category grid, or null
    private HotbarValue.Entry hbGridEntryAt(final ItemPicker hb, final double cpX, final double cpY, final double cpW, final double scale, final int mx, final int my) {
        if (hb.getOpenCategory() < 0) return null;
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        final double searchY = cpY + pad + cell + pad;
        final double gridX = cpX + pad;
        final double gridY = searchY + HB_SEARCH * scale + pad;
        final int cols = hbGridCols(cpW, scale);
        final List<HotbarValue.Entry> filtered = hb.getFilteredEntries();
        final int totalRows = Math.max(1, (filtered.size() + cols - 1) / cols);
        final int rowOffset = (int) Math.max(0, Math.min(Math.max(0, totalRows - HB_VIEW_ROWS), Math.round(hb.getScroll())));
        for (int r = 0; r < HB_VIEW_ROWS; r++) {
            for (int c = 0; c < cols; c++) {
                final int idx = (rowOffset + r) * cols + c;
                if (idx >= filtered.size()) continue;
                if (GUIUtil.mouseOver(gridX + c * (cell + gap), gridY + r * (cell + gap), cell, cell, mx, my)) return filtered.get(idx);
            }
        }
        return null;
    }

    // hotbar slot column under the cursor (for drops), or -1
    private int hbSlotColAt(final HotbarValue hb, final double cpX, final double cpY, final double scale, final int mx, final int my) {
        final double cell = HB_CELL * scale, gap = HB_GAP * scale, pad = HB_PAD * scale;
        final double stripY = hbStripY(hb, cpY, scale);
        final double stripH = cell + hbMaxFallbacks(hb) * (cell + gap);
        if (my < stripY || my > stripY + stripH) return -1;
        for (int i = 0; i < HotbarValue.SLOTS; i++) {
            final double cx = cpX + pad + i * (cell + gap);
            if (mx >= cx && mx < cx + cell) return i;
        }
        return -1;
    }

    // {slot, itemIndex} of the specific cell under the cursor (for removal), or null
    private int[] hbSlotItemAt(final HotbarValue hb, final double cpX, final double cpY, final double scale, final int mx, final int my) {
        final int col = hbSlotColAt(hb, cpX, cpY, scale, mx, my);
        if (col < 0) return null;
        final double cell = HB_CELL * scale, gap = HB_GAP * scale;
        final double stripY = hbStripY(hb, cpY, scale);
        final int k = (int) Math.floor((my - stripY) / (cell + gap));
        if (k < 0 || k >= hb.getSlots().get(col).size()) return null;
        return new int[]{col, k};
    }

    private double contentX(final double scale) { return (x + REF_SIDEBAR_WIDTH + REF_CONTENT_PADDING) * scale; }
    private double moduleWidth(final double scale) { return (REF_GUI_WIDTH - REF_SIDEBAR_WIDTH - REF_CONTENT_PADDING * 2.0) * scale; }
    private double sliderX(final double modX, final double scale) { return modX + 150 * scale; }
    private double settingHeight(final double scale) { return REF_SETTING_HEIGHT * scale; }

    /** True when a search is active and this module has any setting whose name matches the query. */
    private boolean isSearchAutoExpanded(final Module m) {
        if (searchQuery.isEmpty()) return false;
        final String q = searchQuery.toLowerCase();
        for (final Value<?> v : m.getSettings()) {
            if (v.isVisible() && v.getName() != null && v.getName().toLowerCase().contains(q)) {
                return true;
            }
        }
        return false;
    }

    /** True when a specific setting matches the search query (for highlight). */
    private boolean settingMatchesSearch(final Value<?> v, final String q) {
        if (q.isEmpty() || v == null) return false;
        final String name = v.getName() != null ? v.getName().toLowerCase() : "";
        final String desc = v.getDescription() != null ? v.getDescription().toLowerCase() : "";
        for (final String word : q.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!name.contains(word) && !desc.contains(word)) return false;
        }
        return true;
    }

    /** When "Remember Search" is on, show the last query on relaunch (module name or any setting name). */
    private String restoreSearchQuery() {
        // saved in a companion .txt next to the redacted folder
        try {
            final java.io.File dir = new java.io.File(mc.mcDataDir, "redacted");
            final java.io.File f = new java.io.File(dir, "lastsearch.txt");
            if (!f.exists()) return "";
            final java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            final String line = r.readLine();
            r.close();
            return line == null ? "" : line.trim();
        } catch (final Exception e) { return ""; }
    }

    private void saveSearchQuery(final String q) {
        try {
            final java.io.File dir = new java.io.File(mc.mcDataDir, "redacted");
            if (!dir.isDirectory()) dir.mkdirs();
            final java.io.File f = new java.io.File(dir, "lastsearch.txt");
            final java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(q == null ? "" : q);
            w.close();
        } catch (final Exception ignored) {}
    }

    // search ignores the category filter - type anything and it hits every module, not just the open tab
    private List<Module> visibleModules() {
        final List<Module> modules = new java.util.ArrayList<>(searchQuery.isEmpty()
                ? Client.getInstance().getModuleManager().getModulesByCategory(selectedCategory)
                : Client.getInstance().getModuleManager().getModules());
        modules.sort((a, b) -> Collator.getInstance().compare(a.getName(), b.getName()));
        if (!searchQuery.isEmpty()) {
            final String q = searchQuery.toLowerCase();
            modules.removeIf(m -> !m.getName().toLowerCase().contains(q));
        }
        return modules;
    }

    private void clampPosition(final double scale) {
        final ScaledResolution sr = new ScaledResolution(mc);
        final double us = getUserScale();
        // the panel zooms about its centre, so its on-screen half-size is (REF/2)*scale*us. keep that
        // zoomed box on screen and back the bound out into the stored fit-space x,y. at us=1 this is
        // identical to the old top-left clamp.
        final double hwRef = REF_GUI_WIDTH / 2.0 * us;
        final double hhRef = REF_GUI_HEIGHT / 2.0 * us;
        final double loX = hwRef - REF_GUI_WIDTH / 2.0;
        final double hiX = sr.getScaledWidth() / scale - hwRef - REF_GUI_WIDTH / 2.0;
        final double loY = hhRef - REF_GUI_HEIGHT / 2.0;
        final double hiY = sr.getScaledHeight() / scale - hhRef - REF_GUI_HEIGHT / 2.0;
        x = Math.max(loX, Math.min(Math.max(loX, hiX), x));
        y = Math.max(loY, Math.min(Math.max(loY, hiY), y));
    }

    private void validatePosition(final double scale) {
        final ScaledResolution sr = new ScaledResolution(mc);
        if (x * scale > sr.getScaledWidth() || y * scale > sr.getScaledHeight()) { x /= scale; y /= scale; }
    }

    private void savePosition(final double scale) {
        clampPosition(scale);
        Client.getInstance().getThemeManager().setRedactedClickGuiPosition(x, y);
    }
}
