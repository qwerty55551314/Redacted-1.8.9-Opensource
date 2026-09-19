package redacted.client.team;

import redacted.client.util.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public final class TeamStore {
    private TeamStore() {
    }

    public static Set<String> load(final File mcDataDir) {
        final Set<String> members = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        final String[] names = JsonFile.read(getTeamFile(mcDataDir), String[].class);
        if (names != null) {
            for (final String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    members.add(name.trim());
                }
            }
        }
        return members;
    }

    public static void save(final File mcDataDir, final Set<String> members) {
        try {
            JsonFile.write(getTeamFile(mcDataDir), new ArrayList<String>(members));
        } catch (final IOException ignored) {
        }
    }

    private static File getTeamFile(final File mcDataDir) {
        return new File(new File(mcDataDir, "redacted"), "team.json");
    }
}
