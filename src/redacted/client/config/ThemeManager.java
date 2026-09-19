package redacted.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import redacted.client.Client;
import redacted.client.module.Category;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ThemeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = Logger.getLogger(ThemeManager.class.getName());

    private File themesDir;
    private String currentTheme = "default";
    private ThemeData theme = new ThemeData();
    private boolean loaded;
    private boolean batching;
    private boolean dirty;

    public synchronized void load(final File mcDataDir) {
        this.themesDir = new File(new File(mcDataDir, "redacted"), "themes");
        this.theme = readTheme(new File(this.themesDir, currentTheme + ".json"));
        migrateFromConfig(mcDataDir);
        this.loaded = true;
    }

    public synchronized void save() {
        // every setter ends in save(), so a GUI close fires four back-to-back full rewrites
        // inside runBatched the burst collapses to one write at the end
        if (this.batching) {
            this.dirty = true;
            return;
        }
        if (!this.loaded || this.themesDir == null) return;
        writeTheme(new File(this.themesDir, currentTheme + ".json"));
    }

    public synchronized void runBatched(final Runnable burst) {
        this.batching = true;
        try {
            burst.run();
        } finally {
            this.batching = false;
            if (this.dirty) {
                this.dirty = false;
                save();
            }
        }
    }

    public synchronized void save(final String name) {
        if (name == null || name.isEmpty()) return;
        ConfigFiles.sanitize(name);
        writeTheme(new File(this.themesDir, name + ".json"));
    }

    public synchronized void load(final String name) {
        if (name == null || name.isEmpty()) return;
        ConfigFiles.sanitize(name);

        final File file = new File(this.themesDir, name + ".json");
        final ThemeData loaded = readTheme(file);
        if (loaded == null) return;

        this.currentTheme = name;
        this.theme = loaded;
    }

    public synchronized String[] list() {
        return ConfigFiles.list(this.themesDir);
    }

    public synchronized void delete(final String name) {
        ConfigFiles.delete(this.themesDir, name);
    }

    public synchronized String getCurrentTheme() {
        return currentTheme;
    }

    // ClickGUI position

    public synchronized double[] getRedactedClickGuiPosition() {
        if (this.theme.clickGui == null || this.theme.clickGui.redacted == null) return null;
        return new double[] { this.theme.clickGui.redacted.x, this.theme.clickGui.redacted.y };
    }

    public synchronized void setRedactedClickGuiPosition(final double x, final double y) {
        ensureClickGuiConfig();
        this.theme.clickGui.redacted = new PositionConfig(x, y);
        save();
    }

    // Dropdown panel positions

    public synchronized double[] getDropdownPanelPosition(final Category category) {
        if (category == null || this.theme.clickGui == null || this.theme.clickGui.dropdownPanels == null) return null;
        final PositionConfig position = this.theme.clickGui.dropdownPanels.get(category.name());
        return position == null ? null : new double[] { position.x, position.y };
    }

    public synchronized void setDropdownPanelPosition(final Category category, final double x, final double y) {
        if (category == null) return;
        ensureClickGuiConfig();
        this.theme.clickGui.dropdownPanels.put(category.name(), new PositionConfig(x, y));
        save();
    }

    // Expanded modules

    public synchronized List<String> getRedactedExpanded() {
        if (this.theme == null || this.theme.clickGui == null) return null;
        return this.theme.clickGui.labelExpanded;
    }

    public synchronized void setRedactedExpanded(final List<String> modules) {
        ensureClickGuiConfig();
        this.theme.clickGui.labelExpanded = modules;
        save();
    }

    public synchronized List<String> getDropdownExpanded(final Category category) {
        if (category == null || this.theme == null || this.theme.clickGui == null || this.theme.clickGui.dropdownExpanded == null) return null;
        return this.theme.clickGui.dropdownExpanded.get(category.name());
    }

    public synchronized void setDropdownExpanded(final Category category, final List<String> modules) {
        if (category == null) return;
        ensureClickGuiConfig();
        this.theme.clickGui.dropdownExpanded.put(category.name(), modules);
        save();
    }

    // Scroll positions

    public synchronized Double getRedactedScroll() {
        if (this.theme == null || this.theme.clickGui == null) return null;
        return this.theme.clickGui.labelScroll;
    }

    public synchronized void setRedactedScroll(final double scroll) {
        ensureClickGuiConfig();
        this.theme.clickGui.labelScroll = scroll;
        save();
    }

    public synchronized Double getExhibitionScroll() {
        if (this.theme == null || this.theme.clickGui == null) return null;
        return this.theme.clickGui.exhibitionScroll;
    }

    public synchronized void setExhibitionScroll(final double scroll) {
        ensureClickGuiConfig();
        this.theme.clickGui.exhibitionScroll = scroll;
        save();
    }

    public synchronized Double getDropdownScroll(final Category category) {
        if (category == null || this.theme == null || this.theme.clickGui == null || this.theme.clickGui.dropdownScroll == null) return null;
        return this.theme.clickGui.dropdownScroll.get(category.name());
    }

    public synchronized void setDropdownScroll(final Category category, final double scroll) {
        if (category == null) return;
        ensureClickGuiConfig();
        this.theme.clickGui.dropdownScroll.put(category.name(), scroll);
        save();
    }

    // Draggable positions

    public synchronized Map<String, double[]> getDraggablePositions() {
        if (this.theme == null || this.theme.draggables == null) return null;
        return this.theme.draggables;
    }

    public synchronized void setDraggablePositions(final Map<String, double[]> positions) {
        if (this.theme == null) return;
        this.theme.draggables = positions;
        save();
    }

    // Category

    public synchronized String getRedactedCategory() {
        if (this.theme == null || this.theme.clickGui == null) return null;
        return this.theme.clickGui.labelCategory;
    }

    public synchronized void setRedactedCategory(final String category) {
        ensureClickGuiConfig();
        this.theme.clickGui.labelCategory = category;
        save();
    }

    // internal

    private void migrateFromConfig(final File mcDataDir) {
        if (this.theme != null && this.theme.hasData()) return;

        final File configFile = new File(new File(mcDataDir, "redacted"), "configs/latest.json");
        if (!configFile.isFile()) return;

        try (final FileReader reader = new FileReader(configFile)) {
            final OldConfig old = GSON.fromJson(reader, OldConfig.class);
            if (old != null) {
                boolean migrated = false;

                if (old.clickGui != null) {
                    this.theme.clickGui = new ClickGuiConfig();
                    this.theme.clickGui.redacted = old.clickGui.redacted;
                    this.theme.clickGui.labelExpanded = old.clickGui.labelExpanded;
                    this.theme.clickGui.labelScroll = old.clickGui.labelScroll;
                    this.theme.clickGui.exhibitionScroll = old.clickGui.exhibitionScroll;
                    this.theme.clickGui.labelCategory = old.clickGui.labelCategory;
                    if (old.clickGui.dropdownPanels != null) this.theme.clickGui.dropdownPanels = old.clickGui.dropdownPanels;
                    if (old.clickGui.dropdownExpanded != null) this.theme.clickGui.dropdownExpanded = old.clickGui.dropdownExpanded;
                    if (old.clickGui.dropdownScroll != null) this.theme.clickGui.dropdownScroll = old.clickGui.dropdownScroll;
                    migrated = true;
                }

                if (old.draggables != null) {
                    this.theme.draggables = old.draggables;
                    migrated = true;
                }

                if (migrated) {
                    LOG.info("Migrated theme data from legacy config");
                    save();
                }
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE, "No legacy theme data to migrate", e);
        }
    }

    private ThemeData readTheme(final File file) {
        if (file == null || !file.isFile()) return new ThemeData();
        try (final FileReader reader = new FileReader(file)) {
            final ThemeData loaded = GSON.fromJson(reader, ThemeData.class);
            return loaded == null ? new ThemeData() : loaded.withDefaults();
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "Failed to read theme: " + file, e);
            return new ThemeData();
        }
    }

    private void writeTheme(final File file) {
        if (file == null) return;
        ensureClickGuiConfig();
        this.theme.withDefaults();

        final File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) return;

        try (final FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this.theme, writer);
        } catch (final IOException e) {
            LOG.log(Level.WARNING, "Failed to write theme: " + file, e);
        }
    }


    private void ensureClickGuiConfig() {
        this.theme.withDefaults();
    }

    // data classes

    private static final class ThemeData {
        ClickGuiConfig clickGui = new ClickGuiConfig();
        Map<String, double[]> draggables;

        boolean hasData() {
            return (clickGui != null && clickGui.hasData()) || (draggables != null && !draggables.isEmpty());
        }

        ThemeData withDefaults() {
            if (this.clickGui == null) this.clickGui = new ClickGuiConfig();
            this.clickGui.withDefaults();
            return this;
        }
    }

    private static final class ClickGuiConfig {
        PositionConfig redacted;
        List<String> labelExpanded;
        Double labelScroll;
        Double exhibitionScroll;
        String labelCategory;
        Map<String, PositionConfig> dropdownPanels = new LinkedHashMap<>();
        Map<String, List<String>> dropdownExpanded = new LinkedHashMap<>();
        Map<String, Double> dropdownScroll = new LinkedHashMap<>();

        boolean hasData() {
            return redacted != null || labelExpanded != null || labelScroll != null
                    || exhibitionScroll != null
                    || labelCategory != null
                    || (dropdownPanels != null && !dropdownPanels.isEmpty())
                    || (dropdownExpanded != null && !dropdownExpanded.isEmpty())
                    || (dropdownScroll != null && !dropdownScroll.isEmpty());
        }

        void withDefaults() {
            if (this.dropdownPanels == null) this.dropdownPanels = new LinkedHashMap<>();
            if (this.dropdownExpanded == null) this.dropdownExpanded = new LinkedHashMap<>();
            if (this.dropdownScroll == null) this.dropdownScroll = new LinkedHashMap<>();
        }
    }

    private static final class PositionConfig {
        double x, y;
        PositionConfig() {}
        PositionConfig(final double x, final double y) { this.x = x; this.y = y; }
    }

    private static final class OldConfig {
        ClickGuiConfig clickGui;
        Map<String, double[]> draggables;
    }
}
