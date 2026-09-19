package celeritas.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Native Celeritas video-settings screen, opened from the OptiFine "Other..." screen.
 * <p>
 * Deliberately does NOT touch any OptiFine option. It only edits the Celeritas terrain renderer's
 * own settings (the {@code ofCeleritas*} fields on {@link GameSettings}), which the renderer reads
 * live. Tabs mirror the reference Celeritas option pages (General / Performance / Advanced), and
 * every control here is wired to real behavior in the port.
 */
public class GuiCeleritasSettings extends GuiScreen {

    public enum Tab {
        GENERAL("celeritas.options.page.general"),
        PERFORMANCE("celeritas.options.page.performance"),
        ADVANCED("celeritas.options.page.advanced");

        final String key;
        Tab(String key) { this.key = key; }
        public String getKey() { return this.key; }
    }

    private static final int TAB_START_ID = 300;
    private static final int OPTION_START_ID = 400;
    private static final int DONE_ID = 200;

    private final GuiScreen prevScreen;
    private final GameSettings settings;
    private Tab currentTab = Tab.GENERAL;
    private String title;
    private static GuiCeleritasSettings current;

    public GuiCeleritasSettings(GuiScreen prevScreen, GameSettings settings) {
        this.prevScreen = prevScreen;
        this.settings = settings;
    }

    /** The active Celeritas settings screen, if one is on screen (for the slider callback). */
    public static GuiCeleritasSettings currentScreen() {
        return current;
    }

    /** Called by a slider when the user releases it; persists the new value. */
    public void onSliderReleased(int optionId, int value) {
        this.applyValue(optionId, value);
        this.settings.saveOptions();
        this.maybeReload(optionId);
    }

    /** Structural options need a full renderer reload to take effect (they are read at renderer creation). */
    private void maybeReload(int optionId) {
        switch (optionId) {
            case CHUNK_THREADS_ID:
            case ASYNC_ID:
            case TRANSLUCENT_ID:
                celeritas.CeleritasRenderHook.onReload();
                break;
            default:
                break;
        }
    }

    @Override
    public void initGui() {
        current = this;
        this.buttonList.clear();
        this.title = I18n.format("celeritas.options.title");

        // Tab row across the top.
        int tabCount = Tab.values().length;
        int tabWidth = Math.min(120, this.width / (tabCount + 1));
        int tabX = this.width / 2 - (tabWidth * tabCount) / 2;
        int tabY = 24;
        for (int i = 0; i < tabCount; i++) {
            Tab tab = Tab.values()[i];
            GuiButton b = new GuiButton(TAB_START_ID + i, tabX + i * tabWidth, tabY, tabWidth - 4, 20,
                I18n.format(tab.getKey()));
            this.buttonList.add(b);
        }

        // Option rows for the current tab.
        List<Object[]> rows = rowsFor(this.currentTab);
        int rowY = tabY + 28;
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            if (row[0] instanceof Integer) { // slider: optionId, min, max, step, label
                int optionId = (Integer) row[0];
                int min = (Integer) row[1];
                int max = (Integer) row[2];
                int step = (Integer) row[3];
                String label = I18n.format((String) row[4]) + ": ";
                int val = currentInt(optionId);
                GuiSliderCeleritas slider = new GuiSliderCeleritas(OPTION_START_ID + optionId,
                    this.width / 2 - 155, rowY, 310, 20, label, "", min, max, val, true, true);
                slider.setOptionId(optionId);
                slider.setStep(step);
                this.buttonList.add(slider);
            } else { // cycling bool/int: label, optionId
                String labelKey = (String) row[0];
                int optionId = (Integer) row[1];
                String label = I18n.format(labelKey) + ": ";
                int val = currentInt(optionId);
                String display = label + displayFor(optionId, val);
                this.buttonList.add(new GuiButton(OPTION_START_ID + optionId, this.width / 2 - 155, rowY, 310, 20, display));
            }
            rowY += 24;
        }

        this.buttonList.add(new GuiButton(DONE_ID, this.width / 2 - 100, this.height - 28,
            I18n.format("gui.done")));
    }

    /** Builds the rows for a tab. Each row is either a slider (Integer id first) or a cycling control (String label first). */
    private List<Object[]> rowsFor(Tab tab) {
        List<Object[]> rows = new ArrayList<Object[]>();
        switch (tab) {
            case GENERAL:
                rows.add(new Object[]{"celeritas.options.renderer", RENDERER_ID});
                rows.add(new Object[]{"celeritas.options.translucent_sorting", TRANSLUCENT_ID});
                rows.add(new Object[]{RENDER_DIST_ID, 2, 32, 1, "celeritas.options.render_distance"});
                break;
            case PERFORMANCE:
                rows.add(new Object[]{"celeritas.options.async_occlusion", ASYNC_ID});
                rows.add(new Object[]{"celeritas.options.fog_occlusion", FOGOCCL_ID});
                rows.add(new Object[]{CHUNK_THREADS_ID, 0, 16, 1, "celeritas.options.chunk_threads"});
                break;
            case ADVANCED:
                // CPU render-ahead limit is not exposed by the 1.8.9 port's chunk pipeline, so the
                // Advanced page holds nothing extra yet (kept as a tab for parity/future additions).
                break;
        }
        return rows;
    }

    // Option ids (kept separate from the tab/option button id space so getOptionId stays unambiguous).
    public static final int RENDERER_ID = 1;
    public static final int TRANSLUCENT_ID = 2;
    public static final int RENDER_DIST_ID = 4;
    public static final int ASYNC_ID = 5;
    public static final int FOGOCCL_ID = 6;
    public static final int CHUNK_THREADS_ID = 7;

    private int currentInt(int id) {
        switch (id) {
            case RENDERER_ID: return this.settings.ofCeleritasRenderer ? 1 : 0;
            case TRANSLUCENT_ID: return this.settings.ofCeleritasTranslucentSorting ? 1 : 0;
            case RENDER_DIST_ID: return this.settings.renderDistanceChunks;
            case ASYNC_ID: return this.settings.ofCeleritasAsyncOcclusion;
            case FOGOCCL_ID: return this.settings.ofCeleritasFogOcclusion ? 1 : 0;
            case CHUNK_THREADS_ID: return this.settings.ofCeleritasChunkThreads;
            default: return 0;
        }
    }

    private void applyValue(int id, int value) {
        switch (id) {
            case RENDERER_ID: this.settings.ofCeleritasRenderer = value != 0; break;
            case TRANSLUCENT_ID: this.settings.ofCeleritasTranslucentSorting = value != 0; break;
            case RENDER_DIST_ID:
                this.settings.renderDistanceChunks = Math.max(2, Math.min(32, value));
                this.mc.gameSettings.saveOptions();
                break;
            case ASYNC_ID: this.settings.ofCeleritasAsyncOcclusion = value % 3; break;
            case FOGOCCL_ID: this.settings.ofCeleritasFogOcclusion = value != 0; break;
            case CHUNK_THREADS_ID: this.settings.ofCeleritasChunkThreads = Math.max(0, Math.min(16, value)); break;
            default: break;
        }
    }

    private String displayFor(int id, int value) {
        switch (id) {
            case RENDERER_ID: return value != 0 ? I18n.format("celeritas.options.on") : I18n.format("celeritas.options.off");
            case TRANSLUCENT_ID: return value != 0 ? I18n.format("celeritas.options.on") : I18n.format("celeritas.options.off");
            case ASYNC_ID:
                switch (value) {
                    case 0: return I18n.format("celeritas.options.async.off");
                    case 1: return I18n.format("celeritas.options.async.shadows");
                    default: return I18n.format("celeritas.options.async.everything");
                }
            case FOGOCCL_ID: return value != 0 ? I18n.format("celeritas.options.on") : I18n.format("celeritas.options.off");
            default: return String.valueOf(value);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == DONE_ID) {
            this.settings.saveOptions();
            this.mc.displayGuiScreen(this.prevScreen);
            return;
        }
        // Tab buttons.
        if (button.id >= TAB_START_ID && button.id < TAB_START_ID + Tab.values().length) {
            this.currentTab = Tab.values()[button.id - TAB_START_ID];
            this.initGui();
            return;
        }
        // Cycling option buttons (sliders are handled by mouseReleased, not here).
        int optId = button.id - OPTION_START_ID;
        if (button.id >= OPTION_START_ID && optId >= 1 && optId <= 9 && !(button instanceof GuiSliderCeleritas)) {
            int val = currentInt(optId);
            int next = (optId == ASYNC_ID) ? (val + 1) % 3 : (val == 0 ? 1 : 0);
            applyValue(optId, next);
            button.displayString = labelFor(optId) + ": " + displayFor(optId, currentInt(optId));
            this.settings.saveOptions();
            this.maybeReload(optId);
        }
    }

    private String labelFor(int id) {
        switch (id) {
            case RENDERER_ID: return I18n.format("celeritas.options.renderer");
            case TRANSLUCENT_ID: return I18n.format("celeritas.options.translucent_sorting");
            case ASYNC_ID: return I18n.format("celeritas.options.async_occlusion");
            case FOGOCCL_ID: return I18n.format("celeritas.options.fog_occlusion");
            default: return "";
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 1) {
            for (Object o : this.buttonList) {
                if (o instanceof GuiButton && !(o instanceof GuiSliderCeleritas)) {
                    GuiButton b = (GuiButton) o;
                    if (b.visible && mouseX >= b.xPosition && mouseX < b.xPosition + b.width
                        && mouseY >= b.yPosition && mouseY < b.yPosition + b.height) {
                        this.actionPerformed(b);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, this.title, this.width / 2, 12, 16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
