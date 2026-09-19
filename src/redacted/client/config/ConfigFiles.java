package redacted.client.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// ClientConfigManager and ThemeManager run the same named-json-dir plumbing;
// one home for it keeps the path-traversal guard in a single place.
final class ConfigFiles {
    private ConfigFiles() {
    }

    static void sanitize(final String name) {
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid config name: " + name);
        }
    }

    static String[] list(final File dir) {
        if (dir == null || !dir.isDirectory()) return new String[0];

        final File[] files = dir.listFiles();
        if (files == null) return new String[0];

        final List<String> names = new ArrayList<String>();
        for (final File file : files) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                names.add(file.getName().substring(0, file.getName().length() - 5));
            }
        }
        return names.toArray(new String[0]);
    }

    static void delete(final File dir, final String name) {
        if (name == null || name.isEmpty()) return;
        sanitize(name);
        final File file = new File(dir, name + ".json");
        if (file.exists()) file.delete();
    }
}
