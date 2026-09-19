package redacted.client.alt;

import redacted.client.util.JsonFile;

import java.io.File;
import java.io.IOException;

final class AltProxyPrefs {
    private String proxyAddress = "";
    private String proxyUsername = "";
    private String proxyPassword = "";

    private AltProxyPrefs() {
    }

    static AltProxyPrefs load(final File mcDataDir) {
        final AltProxyPrefs prefs = JsonFile.read(getPrefsFile(mcDataDir), AltProxyPrefs.class);
        return prefs == null ? new AltProxyPrefs() : prefs;
    }

    static void save(final File mcDataDir, final String proxyAddress, final String proxyUsername, final String proxyPassword) throws IOException {
        final AltProxyPrefs prefs = new AltProxyPrefs();
        prefs.proxyAddress = emptyIfNull(proxyAddress);
        prefs.proxyUsername = emptyIfNull(proxyUsername);
        prefs.proxyPassword = emptyIfNull(proxyPassword);
        JsonFile.write(getPrefsFile(mcDataDir), prefs);
    }

    String getProxyAddress() {
        return emptyIfNull(this.proxyAddress);
    }

    String getProxyUsername() {
        return emptyIfNull(this.proxyUsername);
    }

    String getProxyPassword() {
        return emptyIfNull(this.proxyPassword);
    }

    private static File getPrefsFile(final File mcDataDir) {
        return new File(new File(mcDataDir, "redacted"), "alt-proxy.json");
    }

    private static String emptyIfNull(final String value) {
        return value == null ? "" : value;
    }
}
