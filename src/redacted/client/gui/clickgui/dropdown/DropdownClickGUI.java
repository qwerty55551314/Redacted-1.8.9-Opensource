package redacted.client.gui.clickgui.dropdown;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import redacted.client.Client;
import redacted.client.font.Fonts;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.render.ClickGUI;
import redacted.client.util.font.Font;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.SettingMath;
import redacted.client.util.render.RenderUtil;
import redacted.client.value.Value;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.client.value.impl.RangeValue;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of Snipeware's dropdown ClickGUI (Panel/GuiFrame design by sendQueue <Vinii>,
 * github@vinii.de), extended the way the better sendQueue-lineage clients did it
 * (Mega 1.2.0): a continuous column of module rows with 2px side rails whose accent
 * fill sweeps across when enabled, a settings panel docked right of the frame at the
 * clicked row -- pill toggles, Mega-style sliders with a small round handle on a
 * rounded track, inline combo boxes and a keybind row -- now scrollable with the
 * mouse wheel and clipped to the viewport, plus a top search bar filtering module
 * rows by name/description. One module panel can be open at a time, like the
 * reference's static expandedID. Hovering a module row shows a description tooltip.
 */
public class DropdownClickGUI extends GuiScreen {

    // reference palette (Panel.java); accent comes from the ClickGUI module's Color setting
    private static final int TITLE_BG = new Color(24, 24, 24).getRGB();  // black195
    private static final int ROW_BG   = new Color(42, 42, 42).getRGB();  // black100
    private static final int TRACK_BG = new Color(50, 50, 50).getRGB();  // Mega's slider track
    private static final int SCROLLBAR = new Color(70, 70, 70).getRGB();
    private static final int MODES_BG = new Color(32, 32, 32, 215).getRGB(); // backdrop of an open combo list

    // same cog the redacted theme marks settings-capable modules with
    private static final ResourceLocation ICON_SETTINGS =
            new ResourceLocation("minecraft", "redacted/clickgui/settings.png");
    private static final int TEXT     = Color.white.getRGB();
    private static final int TEXT_DIM = new Color(170, 170, 170).getRGB();
    private static final int PILL_OFF = Color.gray.getRGB();

    // reference metrics (Panel.FRAME_WIDTH etc.), multiplied by the gui scale at draw time
    private static final double FRAME_W    = 110.0;
    private static final double FRAME_STEP = 140.0;
    private static final double FRAME_X0   = 25.0;
    private static final double FRAME_Y0   = 50.0;
    private static final double TITLE_H    = 18.0;
    private static final double TITLE_LIFT = 6.0;   // title bar sits this far above the frame y
    private static final double ROW_TOP    = 12.0;  // first row starts this far below the frame y
    private static final double PANEL_W    = 101.0; // GuiSlider/GuiComboBox/GuiGetKey.getWidth()
    private static final double MAX_PANEL_H = 200.0; // own height cap, independent of screen space
    private static final double MIN_VIEW_H = 60.0;  // smallest scroll viewport before shifting the panel up

    private final List<Frame> frames = new ArrayList<>();

    // single open module across all frames, like GuiButton.expandedID being static
    private Module openModule;
    private ModeValue openCombo;
    // the setting that matched the active search query; its row gets a highlight
    // in the auto-opened panel so the user sees exactly where the match is
    private Value<?> searchHighlightedValue;
    private Module bindingModule;
    private Frame draggingFrame;

    private Value<?> draggingValue;
    private int draggingRangeHandle;

    // animated slider fill/handle: target follows the value, offset eases after it so
    // the track glides instead of snapping (matches the row-fill/scroll easing style).
    // single sliders store {pct}; range sliders store {lo, hi}.
    private final Map<Value<?>, double[]> sliderFill = new HashMap<>();
    // animated pill-toggle thumb progress (0 = off, 1 = on) so toggles glide instead of snap
    private final Map<BooleanValue, Double> toggleProgress = new HashMap<>();

    // settings-panel scroll (target follows the wheel, offset eases after it like Mega)
    private double panelScroll, panelScrollTarget;

    // top search bar state
    private String searchQuery = "";
    private boolean searchFocused;

    // tooltip shown when hovering a module row or a truncated panel label
    private String tooltipTitle;
    private String tooltipDesc;
    private double tooltipX, tooltipY, tooltipW;

    // the scale setting is dragged inside this GUI; applying it live makes every frame
    // re-layout mid-drag, so the GUI keeps laying out at the last settled scale and
    // only switches once the value has been still for 500ms
    private double appliedScale = -1.0;
    private double pendingScale;
    private long pendingSince = -1L;

    private double getScale() {
        return appliedScale < 0.0 ? ClickGUI.guiScale() : appliedScale;
    }

    /** debounce the ClickGUI Scale setting: apply it 500ms after the last change */
    private void updateScale() {
        final double live = ClickGUI.guiScale();
        if (appliedScale < 0.0) {
            appliedScale = live;
            pendingScale = live;
            return;
        }
        if (live != pendingScale) {
            pendingScale = live;
            pendingSince = System.currentTimeMillis();
        }
        if (pendingSince > 0L && appliedScale != pendingScale
                && System.currentTimeMillis() - pendingSince >= 500L) {
            appliedScale = pendingScale;
            pendingSince = -1L;
            for (final Frame frame : frames) {
                frame.refreshFonts();
            }
        }
    }

    private int accent() {
        final ClickGUI clickGui = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        return clickGui != null ? clickGui.getColorValue().getValue() : 0xFF8888FF;
    }

    /** translucent accent-tinted backdrop for the search-highlighted setting row */
    private void drawSearchHighlight(final double x, final double y, final double w, final double h) {
        final int rgb = accent() & 0xFFFFFF;
        RenderUtil.drawRect(x, y, w, h, (0x40 << 24) | rgb);
    }

    private boolean isSearching() {
        return !searchQuery.isEmpty();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        final List<Frame> previous = new ArrayList<>(frames);
        frames.clear();

        double startX = FRAME_X0;
        for (final Category category : Category.values()) {
            Frame frame = new Frame(category, startX, FRAME_Y0);

            // window resize re-runs initGui: keep live positions/state instead of resetting
            for (final Frame old : previous) {
                if (old.category == category) {
                    frame.x = old.x;
                    frame.y = old.y;
                    frame.expanded = old.expanded;
                    frame.titleAnimY = old.titleAnimY;
                    break;
                }
            }
            frames.add(frame);
            startX += FRAME_STEP;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, final float partialTicks) {
        drawDefaultBackground();

        updateScale();
        tooltipTitle = null;
        tooltipDesc = null;

        // single wheel read per frame; drives the open panel's scroll (Mega's 30px/notch)
        final int wheel = Mouse.getDWheel();
        if (openModule != null) {
            final Frame owner = frameOwning(openModule);
            if (owner != null) {
                final double viewH = owner.panelGeom(openModule)[3];
                final double contentH = owner.panelContentH(openModule);
                if (overOpenPanel(mouseX, mouseY) && wheel != 0) {
                    panelScrollTarget += wheel / 120.0 * 30.0 * getScale();
                }
                // combo boxes opening/closing changes the content height -- re-clamp
                final double maxScroll = Math.max(0.0, contentH - viewH);
                panelScrollTarget = Math.max(-maxScroll, Math.min(0.0, panelScrollTarget));
                panelScroll = animate(panelScrollTarget, panelScroll, 0.3);
            }
        }

        for (final Frame frame : frames) {
            frame.draw(mouseX, mouseY);
        }

        autoOpenSearchResult();

        drawSearchBar();

        if (tooltipTitle != null) {
            drawTooltip();
        }
    }

    /**
     * When the search bar uniquely narrows the grid to ONE module, automatically open
     * its settings panel so the matching setting is right there (e.g. searching
     * "target hud" finds the TargetHUD setting and drops you into EntityESP's panel).
     */
    private void autoOpenSearchResult() {
        if (!isSearching()) {
            searchHighlightedValue = null;
            return;
        }

        Module single = null;
        int total = 0;
        for (final Frame frame : frames) {
            for (final Module match : frame.modules()) {
                total++;
                single = match;
            }
        }

        if (total != 1 || single == null || !hasPanelSettings(single)) {
            searchHighlightedValue = null;
            return;
        }

        // find the exact setting that matched so its row can be highlighted
        searchHighlightedValue = findMatchingSetting(single, searchQuery);
        if (openModule != single) {
            openPanel(single);
        }
    }

    /** returns the first visible setting whose name/description contains any query word, else null */
    private Value<?> findMatchingSetting(final Module m, final String query) {
        final String q = query.toLowerCase();
        for (final Value<?> v : m.getSettings()) {
            if (!v.isVisible()) continue;
            final String name = v.getName() == null ? "" : v.getName().toLowerCase();
            final String desc = v.getDescription() == null ? "" : v.getDescription().toLowerCase();
            for (final String word : q.split("\\s+")) {
                if (word.isEmpty()) continue;
                if (name.contains(word) || desc.contains(word)) return v;
            }
        }
        return null;
    }

    // search bar

    private double[] searchBarGeom() {
        final double scale = getScale();
        final double w = Math.min(220.0 * scale, width * 0.6);
        return new double[]{(width - w) / 2.0, 6.0 * scale, w, TITLE_H * scale};
    }

    private boolean overSearchBar(int mx, int my) {
        final double[] g = searchBarGeom();
        return GUIUtil.mouseOver(g[0], g[1], g[2], g[3], mx, my);
    }

    private void drawSearchBar() {
        final Font font = frames.get(0).rowFont;
        final double scale = getScale();
        final double[] g = searchBarGeom();

        RenderUtil.drawRoundedRect(g[0], g[1], g[2], g[3], 3.0 * scale, TITLE_BG);
        RenderUtil.drawRoundedOutline(g[0], g[1], g[2], g[3], 3.0 * scale, 1.0f,
                searchFocused ? accent() : new Color(60, 60, 60).getRGB());

        final double tx = g[0] + 5.0 * scale;
        final double ty = g[1] + (g[3] - font.height()) / 2.0 + 2.5 * scale;
        if (searchQuery.isEmpty()) {
            // focused + empty: show only the caret, never the placeholder
            if (!searchFocused) {
                font.draw("Search...", tx, ty, TEXT_DIM);
            }
        } else {
            font.draw(searchQuery, tx, ty, TEXT);
        }
        if (searchFocused && System.currentTimeMillis() % 1000 < 500) {
            final double caretX = searchQuery.isEmpty()
                    ? tx
                    : tx + font.width(searchQuery) + 1.0 * scale;
            RenderUtil.drawRect(caretX, ty, 1.0 * scale, font.height() - 4.0 * scale, accent());
        }
    }

    private void drawTooltip() {
        final Font font = frames.get(0).rowFont;
        final double scale = getScale();
        final String name = tooltipTitle;
        final String desc = tooltipDesc;

        double w = font.width(name) + 8 * scale;
        double h = font.height() + 8 * scale;
        if (desc != null && !desc.isEmpty()) {
            w = Math.max(w, font.width(desc) + 8 * scale);
            h = font.height() * 2.0 + 11 * scale;
        }

        double tx = tooltipX + tooltipW + 8 * scale;
        if (tx + w > width - 4 * scale) tx = tooltipX - w - 8 * scale;
        double ty = Math.min(tooltipY, Math.max(4 * scale, height - h - 4 * scale));

        RenderUtil.drawRect(tx, ty, w, h, TITLE_BG);
        RenderUtil.drawRect(tx, ty, 2 * scale, h, accent());
        font.draw(name, tx + 5 * scale, ty + 4 * scale, TEXT);
        if (desc != null && !desc.isEmpty()) {
            font.draw(desc, tx + 5 * scale, ty + font.height() + 7 * scale, TEXT_DIM);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, final int mouseButton) throws IOException {
        if (overSearchBar(mouseX, mouseY)) {
            searchFocused = true;
            bindingModule = null;
            return;
        }
        searchFocused = false;

        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.get(i).click(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, final int state) {
        draggingFrame = null;
        draggingValue = null;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Client.getInstance().getThemeManager().runBatched(() -> {
            for (final Frame frame : frames) {
                frame.saveState();
            }
        });
    }

    @Override
    protected void keyTyped(char typedChar, final int keyCode) throws IOException {
        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                searchFocused = false;
            } else if (keyCode == Keyboard.KEY_BACK && isCtrlKeyDown()) {
                // Ctrl+Backspace clears the whole query
                searchQuery = "";
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            } else if (typedChar != 0 && typedChar >= 32 && searchQuery.length() < 40) {
                searchQuery += typedChar;
            }
            return;
        }

        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_SPACE) {
                bindingModule.setKey(Keyboard.KEY_NONE);
            } else if (keyCode != 0) {
                bindingModule.setKey(keyCode);
            }
            bindingModule = null;
            return;
        }

        if (keyCode == 1 || Client.getInstance().getModuleManager().getModule(ClickGUI.class).shouldCloseOn(keyCode)) {
            this.mc.displayGuiScreen(null);
            if (this.mc.currentScreen == null) this.mc.setIngameFocus();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // scissor helpers matching the exhibition theme's setup (scaled-resolution aware)
    private void startScissor(double x, double y, double width, double height) {
        final ScaledResolution sr = new ScaledResolution(mc);
        final double scaleFactor = sr.getScaleFactor();
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

    /** icon draw via RenderUtil's trilinear/mipmap path; untinted so the PNG shows as authored */
    private static void drawSmoothIcon(final ResourceLocation loc, final double x, final double y,
                                       final double w, final double h) {
        RenderUtil.drawIcon(loc, x, y, w, h, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    // RenderUtil.animate from the reference -- eased approach with a minimum step, which
    // gives the snappy trailing motion of the original frames and rows
    private static double animate(double target, double current, double speed) {
        final boolean larger = target > current;
        if (speed < 0.0D) speed = 0.0D;
        else if (speed > 1.0D) speed = 1.0D;

        final double dif = Math.max(target, current) - Math.min(target, current);
        double factor = dif * speed;
        if (factor < 0.1D) factor = 0.1D;

        current = larger ? current + factor : current - factor;
        if (larger && current > target) current = target;
        if (!larger && current < target) current = target;
        return current;
    }

    // gentle glide for the slider fill: no minimum step, so tiny per-frame deltas ease
    // smoothly instead of snapping like the rows (which want the 0.1 minimum)
    private static double glide(double target, double current, double speed) {
        if (speed < 0.0D) speed = 0.0D;
        else if (speed > 1.0D) speed = 1.0D;
        current += (target - current) * speed;
        if (Math.abs(target - current) < 0.0005) current = target;
        return current;
    }

    private String format(double val, double inc) {
        if (inc >= 1) return String.valueOf(Math.round(val));
        if (inc >= 0.1) return String.format("%.1f", val);
        return String.format("%.2f", val);
    }

    /** clip text to maxW with a ".." tail; hovering the row shows the full text */
    private String fit(final String text, final Font font, final double maxW) {
        if (font.width(text) <= maxW) return text;
        String t = text;
        while (t.length() > 1 && font.width(t + "..") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "..";
    }

    private void openPanel(final Module m) {
        openModule = m;
        openCombo = null;
        bindingModule = null;
        panelScroll = 0;
        panelScrollTarget = 0;
    }

    /** true when the module has at least one setting the settings panel can show */
    private boolean hasPanelSettings(final Module m) {
        for (final Value<?> v : m.getSettings()) {
            if (!v.isVisible()) continue;
            if (v instanceof BooleanValue || v instanceof ModeValue
                    || v instanceof NumberValue || v instanceof RangeValue) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walks the settings panel of a module in a single fixed order (header label,
     * settings, keybind row) and reports every element's rect to the visitor, offset
     * by the current scroll. Draw, click and height are all derived from this walk so
     * the three can never drift apart.
     */
    private void walkPanel(final Module m, final PanelVisitor visitor, double scroll) {
        final Frame owner = frameOwning(m);
        if (owner == null) return;

        final double scale = getScale();
        final double fh = owner.rowFont.height();

        final double[] geom = owner.panelGeom(m);
        double x = geom[0];
        // +3 like GuiFrame's component offset; the header sits a bit lower so it
        // never crowds the panel's top edge
        double y = geom[1] + 3 * scale + scroll;
        double w = geom[2];

        visitor.label(x, y, w, m.getName() + " Settings");
        y += fh + 4 * scale;

        for (final Value<?> v : m.getSettings()) {
            if (!v.isVisible()) continue;

            if (v instanceof BooleanValue) {
                visitor.toggle(x, y, w, (BooleanValue) v);
                y += fh + 5 * scale;
            } else if (v instanceof NumberValue) {
                visitor.slider(x, y, w, (NumberValue) v);
                y += fh + 8 * scale; // +2 breathing room below the slider track
            } else if (v instanceof RangeValue) {
                visitor.range(x, y, w, (RangeValue) v);
                y += fh + 8 * scale;
            } else if (v instanceof ModeValue) {
                final ModeValue mv = (ModeValue) v;
                visitor.combo(x, y, w, mv);
                y += fh + 4 * scale;
                if (openCombo == mv) {
                    final List<String> modes = mv.getModes();
                    for (int i = 0; i < modes.size(); i++) {
                        visitor.comboMode(x, y, w, mv, modes.get(i), i, modes.get(i).equalsIgnoreCase(mv.getValue()));
                        y += fh + 2 * scale;
                    }
                    y += 3 * scale;
                }
            }
        }

        visitor.keybind(x, y, w, m);
    }

    private Frame frameOwning(final Module m) {
        for (final Frame frame : frames) {
            if (frame.category == m.getCategory()) return frame;
        }
        return null;
    }

    private boolean overOpenPanel(int mx, int my) {
        if (openModule == null) return false;
        final Frame owner = frameOwning(openModule);
        if (owner == null) return false;
        final double[] g = owner.panelGeom(openModule);
        return GUIUtil.mouseOver(g[0], g[1], g[2], g[3], mx, my);
    }

    private interface PanelVisitor {
        void label(double x, double y, double w, String text);

        void toggle(double x, double y, double w, BooleanValue v);

        void slider(double x, double y, double w, NumberValue v);

        void range(double x, double y, double w, RangeValue v);

        void combo(double x, double y, double w, ModeValue v);

        void comboMode(double x, double y, double w, ModeValue v, String mode, int index, boolean selected);

        void keybind(double x, double y, double w, Module m);
    }

    private class Frame {
        private final Category category;
        private double x, y;
        private double dragX, dragY;
        private boolean expanded = true;
        private double titleAnimY;

        private final Map<Module, Double> rowAnimY = new HashMap<>();
        private final Map<Module, Double> rowFill = new HashMap<>();

        private Font titleFont; // Display 22
        private Font rowFont;   // Display 17
        private Font valueFont; // Display 16, slider numbers
        private Font iconFont;  // Snipeware 32 category glyphs

        Frame(final Category category, final double x, final double y) {
            this.category = category;
            this.x = x;
            this.y = y;

            final double[] saved = Client.getInstance().getThemeManager().getDropdownPanelPosition(category);
            if (saved != null) {
                this.x = saved[0];
                this.y = saved[1];
            }
            final List<String> exp = Client.getInstance().getThemeManager().getDropdownExpanded(category);
            this.expanded = exp == null || !exp.isEmpty();
            this.titleAnimY = this.y;

            refreshFonts();
        }

        /** rebuild the per-frame fonts after the debounced gui scale changed */
        private void refreshFonts() {
            final double scale = getScale();
            titleFont = Fonts.DISPLAY.get(Fonts.size(22 * scale));
            rowFont = Fonts.DISPLAY.get(Fonts.size(17 * scale));
            valueFont = Fonts.DISPLAY.get(Fonts.size(16 * scale));
            iconFont = Fonts.SNIPA_ICONS.get(Fonts.size(32 * scale));
        }

        // reference icon glyphs: COMBAT=E MOVEMENT=A VISUALS(render)=C WORLD=D PLAYER=B;
        // RENDER uses the -4 y offset the reference gives VISUALS, the rest -3.5
        private String iconGlyph() {
            switch (category) {
                case COMBAT: return "E";
                case MOVEMENT: return "A";
                case RENDER: return "C";
                case WORLD: return "D";
                case DEVELOPER: return "F";
                default: return "B";
            }
        }

        private double iconYOffset() {
            return category == Category.RENDER ? -4.5 : -4.0;
        }

        /** modules of this category, filtered by the search bar when one is active */
        private List<Module> modules() {
            final List<Module> all = Client.getInstance().getModuleManager().getModulesByCategory(category);
            if (!isSearching()) return all;

            final List<Module> out = new ArrayList<>();
            final String q = searchQuery.toLowerCase();
            for (final Module m : all) {
                if (moduleOrSettingMatches(m, q)) {
                    out.add(m);
                }
            }
            return out;
        }

        /** true when the module name, description, or any visible setting name/description matches all query words */
        private boolean moduleOrSettingMatches(final Module m, final String q) {
            final StringBuilder sb = new StringBuilder();
            if (m.getName() != null) sb.append(m.getName()).append(' ');
            if (m.getDescription() != null) sb.append(m.getDescription()).append(' ');
            for (final Value<?> v : m.getSettings()) {
                if (!v.isVisible()) continue;
                if (v.getName() != null) sb.append(v.getName()).append(' ');
                if (v.getDescription() != null) sb.append(v.getDescription()).append(' ');
            }
            final String haystack = sb.toString().toLowerCase();
            // every whitespace-split word must appear somewhere in the module+settings text
            for (final String word : q.split("\\s+")) {
                if (word.isEmpty()) continue;
                if (!haystack.contains(word.toLowerCase())) return false;
            }
            return true;
        }

        private double scale() {
            return getScale();
        }

        // GuiFrame: width = max(110, titleWidth + 15, longest button + 15) where a
        // button is 5 + textWidth wide, so the frame grows to fit long module names
        private double frameW() {
            double w = Math.max(FRAME_W, titleFont.width(category.name()) + 15.0);
            for (final Module m : modules()) {
                w = Math.max(w, rowFont.width(m.getName()) + 20.0);
            }
            return w;
        }

        private double rowH() {
            return rowFont.height() + 3.0 * scale();
        }

        private double rowY(int index) {
            return (y + ROW_TOP) * scale() + index * rowH();
        }

        private int moduleIndex(final Module m) {
            int i = 0;
            for (final Module mod : modules()) {
                if (mod == m) return i;
                i++;
            }
            return -1;
        }

        /**
         * Settings panel geometry: flush right of the frame, top at the clicked row
         * (GuiFrame's left/top). Returns {left, top, width, viewportHeight} -- the
         * viewport is capped by the screen; anything taller scrolls inside it.
         */
        private double[] panelGeom(final Module m) {
            final double scale = scale();
            double w = PANEL_W * scale;
            // widen so the header ("<Module> Settings") and every visible setting name
            // fit without truncating -- long names like "HitSounds Settings" otherwise
            // clip against the 101px reference width
            w = Math.max(w, rowFont.width(m.getName() + " Settings") + 12.0 * scale);
            for (final Value<?> v : m.getSettings()) {
                if (!v.isVisible()) continue;
                if (v instanceof BooleanValue) {
                    w = Math.max(w, rowFont.width(v.getName()) + 26.0 * scale);
                } else if (v instanceof NumberValue) {
                    final NumberValue nv = (NumberValue) v;
                    w = Math.max(w, rowFont.width(nv.getName())
                            + rowFont.width(format(nv.getValue(), nv.getIncrement())) + 22.0 * scale);
                } else if (v instanceof RangeValue) {
                    final RangeValue rv = (RangeValue) v;
                    final String val = format(rv.getRangeMin(), rv.getIncrement())
                            + "-" + format(rv.getRangeMax(), rv.getIncrement());
                    w = Math.max(w, rowFont.width(rv.getName()) + rowFont.width(val) + 22.0 * scale);
                } else if (v instanceof ModeValue) {
                    w = Math.max(w, rowFont.width(v.getName()) + 64.0 * scale);
                }
            }

            double left = x * scale + frameW() * scale + 2 * scale;
            if (left + w > width - 2 * scale) {
                left = x * scale - w - 2 * scale;
            }

            double top = rowY(moduleIndex(m));
            // own cap first so tall-setting modules don't grow a screen-long panel,
            // then the remaining screen space as the hard limit
            double maxView = Math.min(MAX_PANEL_H * scale, height - 4 * scale - top);
            if (maxView < MIN_VIEW_H * scale) {
                // frame dragged near the bottom: shift the panel up so the viewport
                // keeps a few rows instead of collapsing to a sliver
                top = Math.max((y + TITLE_H) * scale, height - 4 * scale - MIN_VIEW_H * scale);
                maxView = Math.min(MAX_PANEL_H * scale, height - 4 * scale - top);
            }
            return new double[]{left, top, w, Math.min(panelContentH(m), maxView)};
        }

        private double panelContentH(final Module m) {
            final double scale = scale();
            final double fh = rowFont.height();
            double h = fh + 4 * scale; // header label (extra top padding so it never touches the edge)
            for (final Value<?> v : m.getSettings()) {
                if (!v.isVisible()) continue;
                if (v instanceof BooleanValue) h += fh + 5 * scale;
                else if (v instanceof NumberValue || v instanceof RangeValue) h += fh + 8 * scale;
                else if (v instanceof ModeValue) {
                    h += fh + 4 * scale;
                    if (openCombo == v) {
                        h += ((ModeValue) v).getModes().size() * (fh + 2 * scale) + 3 * scale;
                    }
                }
            }
            h += fh + 4 * scale; // keybind row
            return h;
        }

        private boolean overTitle(int mx, int my) {
            final double scale = scale();
            // reference hover region: (posX, posY - 8, width, 19)
            return GUIUtil.mouseOver(x * scale, (y - 8) * scale, frameW() * scale, 19 * scale, mx, my);
        }

        public void draw(int mouseX, int mouseY) {
            final double scale = scale();
            final double fw = frameW();
            final double px = x * scale;
            final double pw = fw * scale;

            if (draggingFrame == this) {
                if (Mouse.isButtonDown(0)) {
                    x = (mouseX - dragX) / scale;
                    y = (mouseY - dragY) / scale;
                    snapToBounds();
                } else {
                    draggingFrame = null;
                }
            }

            // searching forces the rows visible even in collapsed frames
            final boolean rowsVisible = expanded || isSearching();

            // title bar (GuiFrame): dark bar floating 6px above the frame origin
            titleAnimY = animate(y, titleAnimY, 0.1);
            final double ty = (titleAnimY - TITLE_LIFT) * scale;

            RenderUtil.drawRect(px + scale, ty, pw - scale, TITLE_H * scale, TITLE_BG);
            titleFont.drawWithShadow(category.name(), px + 5 * scale, titleAnimY * scale - scale, TEXT);
            iconFont.drawCentered(iconGlyph(), px + pw - pw / 8.0, (titleAnimY + iconYOffset()) * scale, TEXT);

            if (!rowsVisible) return;

            // module rows: contiguous column, 2px side rails, sweeping accent fill
            double targetY = y + ROW_TOP;
            double lastRowBottom = 0.0;
            for (final Module m : modules()) {
                double rowTarget = targetY * scale;
                double anim = rowAnimY.containsKey(m) ? rowAnimY.get(m) : rowTarget - 40 * scale;
                anim = animate(rowTarget, anim, 0.1);
                rowAnimY.put(m, anim);

                double rh = rowH();
                double rw = pw - scale; // width - 1, rows start one in like the title bar
                double rx = px + scale;

                RenderUtil.drawRect(rx, anim, rw, rh, ROW_BG);

                // enabled fill sweeps across the row and sweeps back out on disable.
                // ClickGUI self-disables on open, so while this screen is up its row
                // reads as active (mirrors RedactedClickGUI's "|| instanceof ClickGUI")
                double fillTarget = (m.isEnabled() || m instanceof ClickGUI) ? rw : 0.0;
                double fill = rowFill.containsKey(m) ? rowFill.get(m) : fillTarget;
                fill = animate(fillTarget, fill, 0.1);
                rowFill.put(m, fill);
                if (fill > 0.5) {
                    RenderUtil.drawRect(rx, anim, fill, rh, accent());
                }

                rowFont.drawWithShadow(m.getName(), rx + 5 * scale, anim + (rh - rowFont.height()) / 2.0 + 2.5 * scale, TEXT);

                // settings cog at the row's right edge for modules with panel settings,
                // drawn untinted so it looks exactly like settings.png
                if (hasPanelSettings(m)) {
                    final double cogSize = 10 * scale;
                    drawSmoothIcon(ICON_SETTINGS, rx + rw - 3 * scale - cogSize,
                            anim + (rh - cogSize) / 2.0 + scale, cogSize, cogSize);
                }

                // 2px dark rails flush left/right of every row -- the continuous edge
                RenderUtil.drawRect(rx, anim, 2 * scale, rh, TITLE_BG);
                RenderUtil.drawRect(rx + rw - 2 * scale, anim, 2 * scale, rh, TITLE_BG);

                if (GUIUtil.mouseOver(rx, anim, rw, rh, mouseX, mouseY) && draggingFrame == null
                        && m != openModule && !overOpenPanel(mouseX, mouseY)) {
                    tooltipTitle = m.getName();
                    tooltipDesc = m.getDescription();
                    tooltipX = rx;
                    tooltipY = anim;
                    tooltipW = rw;
                }

                targetY += rowH() / scale;
                lastRowBottom = anim + rh;
            }

            // bottom border closing the column's rectangle, aligned with the side rails
            if (lastRowBottom > 0) {
                RenderUtil.drawRect(px + scale, lastRowBottom, pw - scale, 2 * scale, TITLE_BG);
            }

            // settings panel of the open module, docked right of the frame
            if (openModule != null && openModule.getCategory() == category && modules().contains(openModule)) {
                drawPanel(mouseX, mouseY);
            }
        }

        private void drawPanel(int mouseX, int mouseY) {
            final Module m = openModule;
            final double scale = scale();
            final double fh = rowFont.height();
            final double[] g = panelGeom(m);
            final double left = g[0], top = g[1], w = g[2], viewH = g[3];
            final double contentH = panelContentH(m);

            // black100 fill with 1px black195 borders, the reference's three
            // overlapping coord-rects generalized to any panel width
            RenderUtil.drawRect(left + scale, top + scale, w - 2 * scale, viewH, ROW_BG);
            // side rails run the full viewport height
            RenderUtil.drawRect(left, top + scale, scale, viewH, TITLE_BG);
            RenderUtil.drawRect(left + w - scale, top + scale, scale, viewH, TITLE_BG);
            // top + bottom rails so the border is a complete rectangle (top sits at
            // the panel's actual top edge, not below it)
            RenderUtil.drawRect(left, top, w, scale, TITLE_BG);
            RenderUtil.drawRect(left, top + viewH + scale, w, scale, TITLE_BG);

            // Mega-style: content scrolls inside a scissored viewport
            startScissor(left + scale, top + scale, w - 2 * scale, viewH);
            walkPanel(m, new PanelVisitor() {
                private void truncationHover(String full, String shown, String desc,
                                             double x, double y, double w, double h, int mx, int my) {
                    if (!shown.equals(full) && GUIUtil.mouseOver(x, y, w, h, mx, my)) {
                        tooltipTitle = full;
                        tooltipDesc = desc;
                        tooltipX = x;
                        tooltipY = y;
                        tooltipW = w;
                    }
                }

                @Override
                public void label(double x, double y, double w, String text) {
                    // the panel's top rail (drawn in drawPanel) is the single top border
                    // no separate header underline, or the top edge reads as two lines
                    String shown = fit(text, rowFont, w - 8 * scale);
                    truncationHover(text, shown, null, x, y, w, fh + 4 * scale, mouseX, mouseY);
                    rowFont.draw(shown, x + w / 2.0 - rowFont.width(shown) / 2.0, y + 3 * scale, TEXT);
                }

                @Override
                public void toggle(double x, double y, double w, BooleanValue v) {
                    if (searchHighlightedValue == v) {
                        drawSearchHighlight(x, y, w, fh + 5 * scale);
                    }
                    final double pillW = 16 * scale, pillH = 9 * scale, r = pillH / 2.0;

                    // glide the thumb between off/on and ease the track colour so the
                    // toggle animates instead of snapping (row-fill style)
                    final double target = v.getValue() ? 1.0 : 0.0;
                    final Double prevP = toggleProgress.get(v);
                    final double prog = glide(target, prevP == null ? target : prevP, 0.22);
                    toggleProgress.put(v, prog);

                    final int trackOn = accent();
                    final int trackOff = PILL_OFF;
                    final int trackR = (int) (((trackOn >> 16) & 0xFF) + (((trackOff >> 16) & 0xFF) - ((trackOn >> 16) & 0xFF)) * (1.0 - prog));
                    final int trackG = (int) (((trackOn >> 8) & 0xFF) + (((trackOff >> 8) & 0xFF) - ((trackOn >> 8) & 0xFF)) * (1.0 - prog));
                    final int trackB = (int) ((trackOn & 0xFF) + ((trackOff & 0xFF) - (trackOn & 0xFF)) * (1.0 - prog));
                    final int trackColor = 0xFF000000 | (trackR << 16) | (trackG << 8) | trackB;

                    final double thumbX = x + 3 * scale + (pillW - pillH) * prog + pillH / 2.0;
                    RenderUtil.drawRoundedRect(x + 3 * scale, y + 3 * scale, pillW, pillH, r, trackColor);
                    RenderUtil.drawFilledCircle(thumbX, y + 3 * scale + pillH / 2.0, 3.5 * scale, TEXT);

                    String shown = fit(v.getName(), rowFont, w - 26 * scale);
                    truncationHover(v.getName(), shown, v.getDescription(), x, y, w, fh + 5 * scale, mouseX, mouseY);
                    // +2px so the label sits centred against the pill
                    rowFont.drawWithShadow(shown, x + 22 * scale, y + 5 * scale, TEXT);
                }

                @Override
                public void slider(double x, double y, double w, NumberValue v) {
                    if (draggingValue == v && Mouse.isButtonDown(0)) {
                        v.setValue(sliderValue(v.getMinimum(), v.getMaximum(), v.getIncrement(), x + 3 * scale, w - 6 * scale, mouseX));
                    } else if (draggingValue == v && !Mouse.isButtonDown(0)) {
                        draggingValue = null;
                    }

                    if (searchHighlightedValue == v) {
                        drawSearchHighlight(x, y, w, fh + 8 * scale);
                    }
                    String val = format(v.getValue(), v.getIncrement());
                    String shown = fit(v.getName(), rowFont, w - rowFont.width(val) - 12 * scale);
                    truncationHover(v.getName(), shown, v.getDescription(), x, y, w, fh + 8 * scale, mouseX, mouseY);
                    // label + value sit 3px lower so they read centred against the track
                    rowFont.drawWithShadow(shown + ":", x + 3 * scale, y + 2 * scale, TEXT);
                    valueFont.drawWithShadow(val, x + w - rowFont.width(val) - 5 * scale, y + 2 * scale, TEXT);

                    final double targetPct = SettingMath.valueToPercent(v.getMinimum(), v.getMaximum(), v.getValue());
                    final double[] prev = sliderFill.get(v);
                    final double animPct = glide(targetPct, prev == null ? targetPct : prev[0], 0.22);
                    sliderFill.put(v, new double[]{animPct});
                    drawSliderTrack(x, y, w, animPct, -1.0);
                }

                @Override
                public void range(double x, double y, double w, RangeValue v) {
                    if (draggingValue == v && Mouse.isButtonDown(0)) {
                        double val = sliderValue(v.getMinimum(), v.getMaximum(), v.getIncrement(), x + 3 * scale, w - 6 * scale, mouseX);
                        if (draggingRangeHandle == 0) v.setRangeMin(Math.min(val, v.getRangeMax()));
                        else v.setRangeMax(Math.max(val, v.getRangeMin()));
                    } else if (draggingValue == v && !Mouse.isButtonDown(0)) {
                        draggingValue = null;
                    }

                    if (searchHighlightedValue == v) {
                        drawSearchHighlight(x, y, w, fh + 8 * scale);
                    }
                    String val = format(v.getRangeMin(), v.getIncrement()) + "-" + format(v.getRangeMax(), v.getIncrement());
                    String shown = fit(v.getName(), rowFont, w - rowFont.width(val) - 12 * scale);
                    truncationHover(v.getName(), shown, v.getDescription(), x, y, w, fh + 8 * scale, mouseX, mouseY);
                    rowFont.drawWithShadow(shown + ":", x + 3 * scale, y + 2 * scale, TEXT);
                    valueFont.drawWithShadow(val, x + w - rowFont.width(val) - 5 * scale, y + 2 * scale, TEXT);

                    double minPct = SettingMath.valueToPercent(v.getMinimum(), v.getMaximum(), v.getRangeMin());
                    double maxPct = SettingMath.valueToPercent(v.getMinimum(), v.getMaximum(), v.getRangeMax());
                    drawSliderTrackAnimated(x, y, w, v, minPct, maxPct);
                }

                @Override
                public void combo(double x, double y, double w, ModeValue v) {
                    if (searchHighlightedValue == v) {
                        drawSearchHighlight(x, y, w, fh + 4 * scale);
                    }
                    // reference draws the label at posX + width/2 - 47 (== +3.5 for w=101)
                    String suffix = "  " + (openCombo == v ? "-" : "+");
                    String labelX = v.getName();
                    String shown = fit(labelX, rowFont, w / 2.0 + 43 * scale - rowFont.width(suffix));
                    truncationHover(labelX, shown, v.getDescription(), x, y, w, fh + 4 * scale, mouseX, mouseY);
                    rowFont.drawWithShadow(shown + suffix, x + w / 2.0 - 47 * scale, y + 2 * scale, TEXT);
                    if (openCombo == v) {
                        // darker backdrop under the expanded mode list
                        final double modesH = v.getModes().size() * (fh + 2 * scale) + 3 * scale;
                        RenderUtil.drawRect(x + 0.5 * scale, y + fh + 3.5 * scale, w - scale, modesH, MODES_BG);
                    }
                }

                @Override
                public void comboMode(double x, double y, double w, ModeValue v, String mode, int index, boolean selected) {
                    rowFont.drawWithShadow(mode.toUpperCase(), x + 4 * scale, y + 4 * scale, selected ? accent() : TEXT);
                }

                @Override
                public void keybind(double x, double y, double w, Module m) {
                    if (bindingModule == m) {
                        String msg = "Press any key...";
                        rowFont.draw(msg, x + w / 2.0 - rowFont.width(msg) / 2.0, y + 3 * scale, TEXT);
                    } else {
                        String key = m.getKey() == 0 ? "NONE" : Keyboard.getKeyName(m.getKey());
                        // both sit 2px lower so they line up with the slider rows above
                        rowFont.drawWithShadow("KeyBind:", x + 3 * scale, y + 4 * scale, TEXT);
                        rowFont.drawWithShadow(key, x + w - rowFont.width(key) - 7 * scale, y + 3 * scale, TEXT_DIM);
                    }
                    RenderUtil.drawRect(x, y + fh + 2 * scale, w, scale, TITLE_BG);
                }
            }, panelScroll);
            endScissor();

            // scrollbar thumb when the content overflows the viewport
            if (contentH > viewH) {
                final double innerH = viewH - 2 * scale;
                final double thumbH = Math.max(10 * scale, innerH * viewH / contentH);
                final double maxScroll = contentH - viewH;
                final double t = maxScroll > 0 ? -panelScroll / maxScroll : 0;
                RenderUtil.drawRect(left + w - 2.5 * scale, top + scale + t * (innerH - thumbH),
                        1.5 * scale, thumbH, SCROLLBAR);
            }
        }

        /**
         * Mega-style slider: rounded 3px track inset from the panel edge, accent fill
         * and a small round handle. Single sliders pass maxPct < 0 (handle at the fill
         * end); range sliders get a fill between the two handles.
         */
        private void drawSliderTrack(double x, double y, double w, double minPct, double maxPct) {
            final double scale = scale();
            final double fh = rowFont.height();
            final double trackX = x + 3 * scale;
            final double trackW = w - 6 * scale;
            final double trackY = y + fh + 1.5 * scale;
            final double trackH = 3 * scale;
            final double r = trackH / 2.0;

            RenderUtil.drawRoundedRect(trackX, trackY, trackW, trackH, r, TRACK_BG);

            if (maxPct < 0) {
                double pct = Math.max(0.0, Math.min(1.0, minPct));
                double fillW = pct <= 0 ? 0 : Math.max(trackH, pct * trackW);
                if (fillW > 0) {
                    RenderUtil.drawRoundedRect(trackX, trackY, Math.min(fillW, trackW), trackH, r, accent());
                    RenderUtil.drawFilledCircle(trackX + Math.max(r, Math.min(fillW, trackW - r)),
                            trackY + r, 2.5 * scale, TEXT);
                } else {
                    RenderUtil.drawFilledCircle(trackX + r, trackY + r, 2.5 * scale, TEXT);
                }
            } else {
                double lo = Math.max(0.0, Math.min(1.0, minPct));
                double hi = Math.max(0.0, Math.min(1.0, maxPct));
                if (hi > lo) {
                    double fillW = Math.max(trackH, (hi - lo) * trackW);
                    RenderUtil.drawRoundedRect(trackX + lo * trackW, trackY,
                            Math.min(fillW, trackW - lo * trackW), trackH, r, accent());
                }
                RenderUtil.drawFilledCircle(trackX + Math.max(r, lo * trackW), trackY + r, 2.5 * scale, TEXT);
                RenderUtil.drawFilledCircle(trackX + Math.max(r, Math.min(hi * trackW, trackW - r)), trackY + r, 2.5 * scale, TEXT);
            }
        }

        private double sliderValue(double min, double max, double inc, double trackX, double trackW, double mouseX) {
            double pct = (mouseX - trackX) / trackW;
            pct = Math.max(0.0, Math.min(1.0, pct));
            return SettingMath.percentToValue(min, max, inc, pct);
        }

        /** range slider: animates both handles so the fill glides between min and max */
        private void drawSliderTrackAnimated(double x, double y, double w, RangeValue v, double loPct, double hiPct) {
            final double[] prev = sliderFill.get(v);
            final double prevLo = prev == null ? loPct : prev[0];
            final double prevHi = prev == null ? hiPct : (prev.length > 1 ? prev[1] : hiPct);
            final double animLo = glide(loPct, prevLo, 0.22);
            final double animHi = glide(hiPct, prevHi, 0.22);
            sliderFill.put(v, new double[]{animLo, animHi});
            drawSliderTrack(x, y, w, animLo, animHi);
        }

        public boolean click(int mouseX, int mouseY, final int mouseButton) {
            if (draggingFrame != null) return false;

            // panel first -- when flipped left it overlaps rows
            if (openModule != null && openModule.getCategory() == category
                    && modules().contains(openModule) && overOpenPanel(mouseX, mouseY)) {
                return clickPanel(mouseX, mouseY, mouseButton);
            }

            // title bar next, so a collapsed frame can still be expanded and dragged
            // (its rows are hidden, but the title is the only thing left to click)
            if (overTitle(mouseX, mouseY)) {
                bindingModule = null;
                if (mouseButton == 0) {
                    draggingFrame = this;
                    dragX = mouseX - x * scale();
                    dragY = mouseY - y * scale();
                } else if (mouseButton == 1) {
                    expanded = !expanded;
                }
                return true;
            }

            if (!(expanded || isSearching())) return false;

            // module rows: LMB toggles, RMB opens/closes the settings panel
            final double scale = scale();
            final double fw = frameW() * scale;
            double targetY = (y + ROW_TOP) * scale;
            for (final Module m : modules()) {
                double rh = rowH();
                double anim = rowAnimY.getOrDefault(m, targetY);
                if (GUIUtil.mouseOver(x * scale + scale, anim, fw - scale, rh, mouseX, mouseY)) {
                    bindingModule = null;
                    if (mouseButton == 0) {
                        m.toggle();
                    } else if (mouseButton == 1) {
                        if (openModule == m) {
                            openModule = null;
                            openCombo = null;
                        } else {
                            openPanel(m);
                        }
                    }
                    return true;
                }
                targetY += rh;
            }
            return false;
        }

        private boolean clickPanel(int mouseX, int mouseY, final int mouseButton) {
            final Module m = openModule;
            final double scale = scale();
            final double fh = rowFont.height();
            final double[] g = panelGeom(m);
            // elements clipped out of the viewport must not be clickable
            final double viewTop = g[1] + scale;
            final double viewBottom = g[1] + g[3];

            final boolean[] consumed = {false};
            walkPanel(m, new PanelVisitor() {
                private boolean hit(double x, double y, double w, double h) {
                    return y + h > viewTop && y < viewBottom
                            && GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY);
                }

                @Override
                public void label(double x, double y, double w, String text) {}

                @Override
                public void toggle(double x, double y, double w, BooleanValue v) {
                    if (mouseButton == 0 && hit(x, y, w, fh + 5 * scale)) {
                        bindingModule = null;
                        v.toggle();
                        consumed[0] = true;
                    }
                }

                @Override
                public void slider(double x, double y, double w, NumberValue v) {
                    if (mouseButton == 0 && hit(x, y, w, fh + 8 * scale)) {
                        bindingModule = null;
                        draggingValue = v;
                        v.setValue(sliderValue(v.getMinimum(), v.getMaximum(), v.getIncrement(), x + 3 * scale, w - 6 * scale, mouseX));
                        consumed[0] = true;
                    }
                }

                @Override
                public void range(double x, double y, double w, RangeValue v) {
                    if (mouseButton == 0 && hit(x, y, w, fh + 8 * scale)) {
                        bindingModule = null;
                        draggingValue = v;
                        draggingRangeHandle = SettingMath.nearestHandle(v, x + 3 * scale, w - 6 * scale, mouseX);
                        consumed[0] = true;
                    }
                }

                @Override
                public void combo(double x, double y, double w, ModeValue v) {
                    // left or right click opens/closes the mode list (matches the module
                    // rows where both buttons act)
                    if ((mouseButton == 0 || mouseButton == 1) && hit(x, y, w, fh + 4 * scale)) {
                        bindingModule = null;
                        openCombo = openCombo == v ? null : v;
                        consumed[0] = true;
                    }
                }

                @Override
                public void comboMode(double x, double y, double w, ModeValue v, String mode, int index, boolean selected) {
                    if (mouseButton == 0 && hit(x, y - scale, w, fh + 2 * scale)) {
                        bindingModule = null;
                        v.setValue(mode);
                        openCombo = null;
                        consumed[0] = true;
                    }
                }

                @Override
                public void keybind(double x, double y, double w, Module m) {
                    if (mouseButton == 0 && hit(x, y, w, fh + 4 * scale)) {
                        bindingModule = bindingModule == m ? null : m;
                        consumed[0] = true;
                    }
                }
            }, panelScroll);
            return consumed[0];
        }

        public void saveState() {
            snapToBounds();
            Client.getInstance().getThemeManager().setDropdownPanelPosition(category, x, y);
            final List<String> names = new ArrayList<>();
            if (expanded) names.add(category.name()); // marker so collapsed stays collapsed
            Client.getInstance().getThemeManager().setDropdownExpanded(category, names);
        }

        /**
         * Keeps the frame sanely placed while dragging and saving: the title bar stays
         * fully on screen and is pushed back below the search bar whenever the two
         * would overlap, in any shape or form.
         */
        private void snapToBounds() {
            final double scale = scale();
            final double fwScreen = frameW() * scale;

            x = Math.max(0.0, Math.min(x, Math.max(0.0, (width - fwScreen) / scale)));
            y = Math.max(TITLE_LIFT + 4.0, Math.min(y, height / scale - 11.0));

            final double[] bar = searchBarGeom();
            final double titleLeft = x * scale;
            final double titleRight = x * scale + fwScreen;
            if (titleRight > bar[0] && titleLeft < bar[0] + bar[2]) {
                final double minTitleTop = bar[1] + bar[3] + 2 * scale; // screen px
                y = Math.max(y, minTitleTop / scale + 8.0);
            }
        }
    }
}
