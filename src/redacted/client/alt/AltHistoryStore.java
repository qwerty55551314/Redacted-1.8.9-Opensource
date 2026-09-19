package redacted.client.alt;

import redacted.client.util.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AltHistoryStore {
    private AltHistoryStore() {
    }

    static List<AltHistoryEntry> load(final File mcDataDir) {
        final AltHistoryEntry[] entries = JsonFile.read(getHistoryFile(mcDataDir), AltHistoryEntry[].class);
        final List<AltHistoryEntry> history = new ArrayList<AltHistoryEntry>();
        if (entries != null) {
            Collections.addAll(history, entries);
        }
        return history;
    }

    static void save(final File mcDataDir, final List<AltHistoryEntry> history) throws IOException {
        JsonFile.write(getHistoryFile(mcDataDir), history);
    }

    private static File getHistoryFile(final File mcDataDir) {
        return new File(new File(mcDataDir, "redacted"), "alts.json");
    }
}
