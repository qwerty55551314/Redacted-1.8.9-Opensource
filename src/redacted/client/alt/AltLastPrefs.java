package redacted.client.alt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Last-used alt marker. Stored in the client's own built-in settings file
 * ({@code redactedoptions.txt}, the same file Celeritas settings use) so we
 * don't add yet another JSON file under redacted/.
 *
 * Written on every successful account activation (login), read at client
 * startup so the client can re-log the same account (or fail over to a
 * random cracked name).
 */
final class AltLastPrefs {
    private static final String KEY_USERNAME = "lastAltUsername";
    private static final String KEY_UUID = "lastAltUuid";
    private static final String KEY_REFRESH = "lastAltRefreshToken";
    private static final String KEY_CRACKED = "lastAltCracked";
    private static final String KEY_ALTENING = "lastAltAltening";
    private static final String KEY_CONNECTED = "lastAltConnected";

    private String username = "";
    private String uuid = "";
    private String refreshToken = "";
    private boolean cracked;
    private boolean altening;
    private long lastConnected;

    AltLastPrefs() {
    }

    static AltLastPrefs load(final File mcDataDir) {
        final AltLastPrefs prefs = new AltLastPrefs();
        final File file = getPrefsFile(mcDataDir);
        if (!file.isFile()) return prefs;
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int colon = line.indexOf(':');
                if (colon < 1) continue;
                final String key = line.substring(0, colon);
                final String value = line.substring(colon + 1);
                switch (key) {
                    case KEY_USERNAME: prefs.username = value; break;
                    case KEY_UUID: prefs.uuid = value; break;
                    case KEY_REFRESH: prefs.refreshToken = value; break;
                    case KEY_CRACKED: prefs.cracked = Boolean.parseBoolean(value); break;
                    case KEY_ALTENING: prefs.altening = Boolean.parseBoolean(value); break;
                    case KEY_CONNECTED:
                        try { prefs.lastConnected = Long.parseLong(value); } catch (final NumberFormatException ignored) {}
                        break;
                }
            }
        } catch (final Exception ignored) {
        }
        return prefs;
    }

    static void save(final File mcDataDir, final String username, final String uuid,
                     final String refreshToken, final boolean cracked, final boolean altening,
                     final long lastConnected) throws IOException {
        // load existing redactedoptions.txt to preserve Celeritas + other keys
        final File file = getPrefsFile(mcDataDir);
        final java.util.Map<String, String> existing = new java.util.LinkedHashMap<>();
        if (file.isFile()) {
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int colon = line.indexOf(':');
                    if (colon < 1) continue;
                    existing.put(line.substring(0, colon), line.substring(colon + 1));
                }
            } catch (final Exception ignored) {
            }
        }

        existing.put(KEY_USERNAME, emptyIfNull(username));
        existing.put(KEY_UUID, emptyIfNull(uuid));
        existing.put(KEY_REFRESH, emptyIfNull(refreshToken));
        existing.put(KEY_CRACKED, String.valueOf(cracked));
        existing.put(KEY_ALTENING, String.valueOf(altening));
        existing.put(KEY_CONNECTED, String.valueOf(lastConnected));

        final File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        try (final PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            for (final java.util.Map.Entry<String, String> entry : existing.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
        }
    }

    String getUsername() { return emptyIfNull(this.username); }
    String getUuid()     { return emptyIfNull(this.uuid); }
    String getRefreshToken() { return emptyIfNull(this.refreshToken); }
    boolean isCracked()  { return this.cracked; }
    boolean isAltening() { return this.altening; }
    long getLastConnected() { return this.lastConnected; }

    private static File getPrefsFile(final File mcDataDir) {
        return new File(mcDataDir, "redactedoptions.txt");
    }

    private static String emptyIfNull(final String value) {
        return value == null ? "" : value;
    }
}