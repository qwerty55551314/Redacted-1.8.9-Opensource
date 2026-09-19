package redacted.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

// the gson-file plumbing every small store kept hand-rolling (alts, proxy prefs, team list).
// read returns null on a missing or corrupt file so each caller picks its own fallback; write
// mkdirs the parent and throws, callers that don't care just swallow it.
public final class JsonFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonFile() {
    }

    public static <T> T read(final File file, final Class<T> type) {
        if (!file.isFile()) return null;
        try (final Reader reader = new FileReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (final Exception ignored) {
            return null;
        }
    }

    public static void write(final File file, final Object value) throws IOException {
        final File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory.getAbsolutePath());
        }
        try (final Writer writer = new FileWriter(file)) {
            GSON.toJson(value, writer);
        }
    }
}
