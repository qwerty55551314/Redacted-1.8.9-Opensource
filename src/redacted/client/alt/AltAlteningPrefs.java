package redacted.client.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

final class AltAlteningPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String apiKey = "";

    private AltAlteningPrefs() {
    }

    static AltAlteningPrefs load(final File mcDataDir) {
        final File file = getPrefsFile(mcDataDir);
        if (!file.isFile()) {
            return new AltAlteningPrefs();
        }

        try (Reader reader = new FileReader(file)) {
            final AltAlteningPrefs prefs = GSON.fromJson(reader, AltAlteningPrefs.class);
            return prefs == null ? new AltAlteningPrefs() : prefs;
        } catch (final Exception ignored) {
            return new AltAlteningPrefs();
        }
    }

    static void save(final File mcDataDir, final String apiKey) throws IOException {
        final AltAlteningPrefs prefs = new AltAlteningPrefs();
        prefs.apiKey = apiKey == null ? "" : apiKey.trim();

        final File file = getPrefsFile(mcDataDir);
        final File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory.getAbsolutePath());
        }

        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(prefs, writer);
        }
    }

    String getApiKey() {
        return this.apiKey == null ? "" : this.apiKey;
    }

    private static File getPrefsFile(final File mcDataDir) {
        return new File(new File(mcDataDir, "redacted"), "altening.json");
    }
}
