package redacted.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import redacted.client.Client;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.ModuleManager;
import redacted.client.value.Value;
import redacted.client.util.time.StopWatch;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClientConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;
    private static final Logger LOG = Logger.getLogger(ClientConfigManager.class.getName());

    private File configsDir;
    private File latestFile;
    private ClientConfig config = new ClientConfig();
    private boolean applying;
    private boolean acceptsSaves;
    private final StopWatch saveTimer = new StopWatch();
    private boolean dirty;

    public synchronized void load(final File mcDataDir, final ModuleManager moduleManager) {
        migrateFromRedacted(mcDataDir);
        ensurePaths(mcDataDir);
        migrateOldConfig(mcDataDir);
        this.config = readConfig(this.latestFile);
        if (this.config == null) this.config = new ClientConfig();
        this.applying = true;

        try {
            // latest.json holds the full session state (incl. render), so restore it completely
            applyModules(moduleManager, true);
        } finally {
            this.applying = false;
            this.acceptsSaves = true;
        }
    }

    public synchronized void save() {
        if (!this.acceptsSaves || this.applying || this.latestFile == null) return;
        // slider drags hit setValue every mouse-move frame, and each save is a full pretty-printed
        // rewrite on a OneDrive folder -- coalesce, pending state flushes on the next save/shutdown
        if (!this.saveTimer.finished(500L)) {
            this.dirty = true;
            return;
        }
        flush();
    }

    public synchronized void saveNow() {
        if (this.latestFile == null) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.mcDataDir == null) return;
            ensurePaths(mc.mcDataDir);
        }
        flush();
    }

    private void ensurePaths(final File mcDataDir) {
        this.configsDir = new File(new File(mcDataDir, "redacted"), "configs");
        this.latestFile = new File(this.configsDir, "latest.json");
    }

    public synchronized void disableSaves() {
        if (this.dirty && this.latestFile != null) flush();
        this.acceptsSaves = false;
    }

    private void flush() {
        // latest.json is the live session state -- it keeps render/cosmetics so they
        // restore across restarts. Only user-named profiles are gameplay-only by default.
        writeConfig(Client.getInstance().getModuleManager(), this.latestFile, true);
        this.saveTimer.reset();
        this.dirty = false;
    }

    // boolean variants return real success/failure so UIs (GuiConfigManager) can show
    // truthful status instead of assuming every call worked. the void names below are
    // kept for ConfigCommand / ConfigTools and simply delegate.
    //
    // By default RENDER + COSMETICS modules are left OUT of a saved profile (and
    // therefore of any load of one) so a config stays gameplay-only unless the user
    // explicitly asks for the render stuff too.
    public synchronized boolean saveProfile(final String name) {
        return saveProfile(name, false);
    }

    public synchronized boolean saveProfile(final String name, final boolean includeRender) {
        if (name == null || name.isEmpty() || this.configsDir == null) return false;
        try {
            ConfigFiles.sanitize(name);
        } catch (final IllegalArgumentException e) {
            return false;
        }
        return writeConfig(Client.getInstance().getModuleManager(),
                new File(this.configsDir, name + ".json"), includeRender);
    }

    public synchronized void save(final String name) {
        saveProfile(name, false);
    }

    public synchronized void save(final String name, final boolean includeRender) {
        saveProfile(name, includeRender);
    }

    // false (and NO state change) when the profile is missing or corrupt -- the old path
    // silently applied an empty config here, wiping live settings while callers reported success
    public synchronized boolean loadProfile(final String name) {
        // loading applies RENDER+COSMETICS from the file by default; --pr opts out
        return loadProfile(name, true);
    }

    public synchronized boolean loadProfile(final String name, final boolean includeRender) {
        if (name == null || name.isEmpty()) return false;
        try {
            ConfigFiles.sanitize(name);
        } catch (final IllegalArgumentException e) {
            return false;
        }
        if (this.configsDir == null) return false;

        final File file = new File(this.configsDir, name + ".json");
        final ClientConfig loaded = readConfig(file);
        if (loaded == null) return false;

        this.config = loaded;
        this.applying = true;

        try {
            applyModules(Client.getInstance().getModuleManager(), includeRender);
        } finally {
            this.applying = false;
            this.acceptsSaves = true;
        }
        return true;
    }

    public synchronized void load(final String name) {
        loadProfile(name);
    }

    public synchronized void load(final String name, final boolean includeRender) {
        loadProfile(name, includeRender);
    }

    public synchronized String[] list() {
        return ConfigFiles.list(this.configsDir);
    }

    public synchronized File getConfigsDir() {
        return this.configsDir;
    }

    public synchronized void delete(final String name) {
        deleteProfile(name);
    }

    // true only when the profile file existed and was removed
    public synchronized boolean deleteProfile(final String name) {
        if (name == null || name.isEmpty() || this.configsDir == null) return false;
        try {
            ConfigFiles.sanitize(name);
        } catch (final IllegalArgumentException e) {
            return false;
        }
        final File file = new File(this.configsDir, name + ".json");
        return file.isFile() && file.delete();
    }

    // resolved profile file for extra row info (size/modified), or null when the name is unusable
    public synchronized File profileFile(final String name) {
        if (name == null || name.isEmpty() || this.configsDir == null) return null;
        try {
            ConfigFiles.sanitize(name);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        return new File(this.configsDir, name + ".json");
    }

    // copies an external .json config into the configs dir so it appears in the list.
    // returns the imported profile name (without extension), or null on any failure.
    public synchronized String importConfig(final File src) {
        if (src == null || !src.isFile()) return null;
        String base = src.getName();
        if (base.toLowerCase().endsWith(".json")) base = base.substring(0, base.length() - 5);
        if (base.isEmpty()) return null;
        try {
            ConfigFiles.sanitize(base);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        if ("latest".equalsIgnoreCase(base)) base = "latest_import";
        final File dst = new File(this.configsDir, base + ".json");
        try {
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            return null;
        }
        return base;
    }

    // returns false (no throw) when the new name is taken or the source is gone, so the GUI can just show a status
    public synchronized boolean rename(final String oldName, final String newName) {
        if (oldName == null || oldName.isEmpty() || newName == null || newName.isEmpty()) return false;
        ConfigFiles.sanitize(oldName);
        ConfigFiles.sanitize(newName);
        if (oldName.equals(newName)) return false;
        final File src = new File(this.configsDir, oldName + ".json");
        final File dst = new File(this.configsDir, newName + ".json");
        if (!src.isFile() || dst.exists()) return false;
        return src.renameTo(dst);
    }

    // one-time copy of the old redacted/ folder so nobody loses their stuff after the rename.
    // copy, not move - an old build pointed at the same .minecraft still reads the original.
    // staged through a temp dir + rename so a crash mid-copy (OneDrive file locks...) doesn't
    // leave a half-populated redacted/ that looks migrated forever.
    private static void migrateFromRedacted(final File mcDataDir) {
        final File oldRoot = new File(mcDataDir, "redacted");
        final File newRoot = new File(mcDataDir, "redacted");
        if (!oldRoot.isDirectory() || newRoot.exists()) return;

        final File staging = new File(mcDataDir, "redacted.migrating");
        try {
            deleteTree(staging); // leftover from a crashed attempt
            copyTree(oldRoot, staging);
            if (!staging.renameTo(newRoot)) {
                throw new IOException("rename failed: " + staging + " -> " + newRoot);
            }
        } catch (final IOException e) {
            LOG.log(Level.WARNING, "Couldn't migrate old redacted/ folder", e);
        }
    }

    private static void deleteTree(final File file) {
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (final File child : children) deleteTree(child);
            }
        }
        file.delete();
    }

    private static void copyTree(final File from, final File to) throws IOException {
        if (from.isDirectory()) {
            if (!to.mkdirs() && !to.isDirectory()) throw new IOException("mkdirs failed: " + to);
            final File[] children = from.listFiles();
            if (children == null) return;
            for (final File child : children) {
                copyTree(child, new File(to, child.getName()));
            }
        } else {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void migrateOldConfig(final File mcDataDir) {
        final File oldFile = new File(new File(mcDataDir, "redacted"), "client.json");
        if (oldFile.isFile() && !this.latestFile.isFile()) {
            if (this.configsDir.mkdirs() || this.configsDir.isDirectory()) {
                oldFile.renameTo(this.latestFile);
            }
        }
    }

    private void applyModules(final ModuleManager moduleManager) {
        applyModules(moduleManager, false);
    }

    private void applyModules(final ModuleManager moduleManager, final boolean includeRender) {
        if (moduleManager == null || this.config.modules == null) return;

        final Map<String, ModuleConfig> moduleConfigs = new LinkedHashMap<String, ModuleConfig>();
        for (final ModuleConfig moduleConfig : this.config.modules) {
            if (moduleConfig != null && moduleConfig.name != null) {
                moduleConfigs.put(normalize(moduleConfig.name), moduleConfig);
            }
        }

        for (final Module module : moduleManager.getModules()) {
            if (!includeRender && isVisualModule(module)) continue;
            final ModuleConfig moduleConfig = moduleConfigs.get(normalize(module.getName()));
            if (moduleConfig == null) continue;

            if (moduleConfig.key != null) module.setKey(moduleConfig.key.intValue());
            applySettings(module, moduleConfig.settings);
        }

        for (final Module module : moduleManager.getModules()) {
            if (!includeRender && isVisualModule(module)) continue;
            final ModuleConfig moduleConfig = moduleConfigs.get(normalize(module.getName()));
            if (moduleConfig == null) continue;
            module.setEnabled(Boolean.TRUE.equals(moduleConfig.enabled) && !isClickGuiModule(module) && module.persistEnabled());
        }
    }

    private void applySettings(final Module module, final Map<String, Object> settings) {
        if (module == null || settings == null) return;

        final Map<String, Object> normalizedSettings = new LinkedHashMap<String, Object>();
        for (final Map.Entry<String, Object> entry : settings.entrySet()) {
            if (entry.getKey() != null) normalizedSettings.put(normalize(entry.getKey()), entry.getValue());
        }

        for (final Value<?> value : module.getSettings()) {
            Object rawValue = normalizedSettings.get(normalize(settingKey(value)));
            // legacy configs keyed children by plain name -- fall back so they still load (then re-save qualified)
            if (rawValue == null && value.getParent() != null) {
                rawValue = normalizedSettings.get(normalize(value.getName()));
            }
            if (rawValue == null) continue;
            value.applyConfig(rawValue);
        }
    }

    // child settings are keyed by "<owner>/<name>" so two owners can each have an "Instant Swap"
    // without colliding in the per-module map; top-level settings keep their plain name.
    private String settingKey(final Value<?> value) {
        return value.getParent() == null ? value.getName() : value.getParent().getName() + "/" + value.getName();
    }

    private boolean writeConfig(final ModuleManager moduleManager, final File file, final boolean includeRender) {
        if (file == null) return false;

        this.config.version = VERSION;
        this.config.modules = collectModules(moduleManager, includeRender);

        final File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) return false;

        try (final FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this.config, writer);
            return true;
        } catch (final IOException e) {
            LOG.log(Level.WARNING, "Failed to write config: " + file, e);
            return false;
        }
    }

    private List<ModuleConfig> collectModules(final ModuleManager moduleManager, final boolean includeRender) {
        final List<ModuleConfig> modules = new ArrayList<ModuleConfig>();
        for (final Module module : moduleManager.getModules()) {
            // dev-only modules are never persisted to config (also stripped from release JARs)
            if (!Client.DEV_BUILD && module.getCategory() == Category.DEVELOPER) continue;
            // unless includeRender, leave render + cosmetics modules out of the profile
            if (!includeRender && isVisualModule(module)) continue;
            final ModuleConfig moduleConfig = new ModuleConfig();
            moduleConfig.name = module.getName();
            moduleConfig.enabled = Boolean.valueOf(module.isEnabled());
            moduleConfig.key = Integer.valueOf(module.getKey());
            moduleConfig.settings = new LinkedHashMap<String, Object>();
            for (final Value<?> setting : module.getSettings()) {
                moduleConfig.settings.put(settingKey(setting), setting.getValue());
            }
            modules.add(moduleConfig);
        }
        return modules;
    }

    /** RENDER + COSMETICS modules are the "visual" set excluded from gameplay configs by default. */
    private boolean isVisualModule(final Module module) {
        if (module == null) return false;
        return module.getCategory() == Category.RENDER || module.getCategory() == Category.COSMETICS;
    }

    // null when the file is missing or unreadable -- callers decide what "missing" means
    // (startup falls back to defaults, profile loads report failure instead)
    private ClientConfig readConfig(final File file) {
        if (file == null || !file.isFile()) return null;
        try (final FileReader reader = new FileReader(file)) {
            final ClientConfig loadedConfig = GSON.fromJson(reader, ClientConfig.class);
            return loadedConfig == null ? null : loadedConfig.withDefaults();
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "Failed to read config: " + file, e);
            return null;
        }
    }

    private boolean isClickGuiModule(final Module module) {
        return module != null && "ClickGUI".equalsIgnoreCase(module.getName());
    }

    private String normalize(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final class ClientConfig {
        int version = VERSION;
        List<ModuleConfig> modules = new ArrayList<ModuleConfig>();

        ClientConfig withDefaults() {
            if (this.modules == null) this.modules = new ArrayList<ModuleConfig>();
            return this;
        }
    }

    private static final class ModuleConfig {
        String name;
        Boolean enabled;
        Integer key;
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
    }
}
