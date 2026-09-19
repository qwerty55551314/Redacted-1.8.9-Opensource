package redacted.client.alt;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

// singleton for alt history, baseline restore, and proxy prefs
public final class AltManager {
    private static final AltManager INSTANCE = new AltManager();
    private static final int MAX_HISTORY_SIZE = 50;
    private static final Logger LOG = LogManager.getLogger();
    private static final boolean DEBUG = true;

    private final List<AltHistoryEntry> history = new ArrayList<AltHistoryEntry>();
    private ProxyConfig activeProxy;
    private Session activeSession;
    private Session baselineSession;     // original session before any alt switch, used by "Restore Original"
    private ProxyConfig baselineProxy;
    private String lastProxyAddress = "";
    private String lastProxyUsername = "";
    private String lastProxyPassword = "";
    private String lastAlteningApiKey = "";
    private AltLastPrefs lastAltPrefs = new AltLastPrefs();
    private boolean loaded;

    private AltManager() {}

    public static AltManager getInstance() {
        return INSTANCE;
    }

    // load history from disk once; no-op on subsequent calls
    public synchronized void load(final File mcDataDir) {
        if (this.loaded) return;

        this.history.clear();
        this.history.addAll(AltHistoryStore.load(mcDataDir));

        final AltProxyPrefs prefs = AltProxyPrefs.load(mcDataDir);
        this.lastProxyAddress = prefs.getProxyAddress();
        this.lastProxyUsername = prefs.getProxyUsername();
        this.lastProxyPassword = prefs.getProxyPassword();

        final AltAlteningPrefs alteningPrefs = AltAlteningPrefs.load(mcDataDir);
        this.lastAlteningApiKey = alteningPrefs.getApiKey();

        this.lastAltPrefs = AltLastPrefs.load(mcDataDir);

        this.debug("load historySize=" + this.history.size()
                + " proxyAddressLen=" + this.lastProxyAddress.length()
                + " proxyUserLen=" + this.lastProxyUsername.length()
                + " alteningKeyLen=" + this.lastAlteningApiKey.length());

        this.loaded = true;
    }

    // activate (save a session to history)

    public synchronized void activate(final Session session, final ProxyConfig proxyConfig, final File mcDataDir,
                                        final String status, final String proxyInput, final String proxyUsernameInput,
                                        final String proxyPasswordInput) throws IOException {
        this.activate(session, proxyConfig, mcDataDir, status, proxyInput, proxyUsernameInput, proxyPasswordInput, false, session.getToken());
    }

    public synchronized void activate(final Session session, final ProxyConfig proxyConfig, final File mcDataDir,
                                        final String status, final String proxyInput, final String proxyUsernameInput,
                                        final String proxyPasswordInput, final boolean cracked) throws IOException {
        this.activate(session, proxyConfig, mcDataDir, status, proxyInput, proxyUsernameInput, proxyPasswordInput, cracked, session.getToken());
    }

    public synchronized void activate(final Session session, final ProxyConfig proxyConfig, final File mcDataDir,
                                        final String status, final String proxyInput, final String proxyUsernameInput,
                                        final String proxyPasswordInput, final boolean cracked, final String token) throws IOException {
        this.activate(session, proxyConfig, mcDataDir, status, proxyInput, proxyUsernameInput, proxyPasswordInput, cracked, false, token);
    }

    public synchronized void activate(final Session session, final ProxyConfig proxyConfig, final File mcDataDir,
                                        final String status, final String proxyInput, final String proxyUsernameInput,
                                        final String proxyPasswordInput, final boolean cracked, final boolean altening,
                                        final String token) throws IOException {
        this.activate(session, proxyConfig, mcDataDir, status, proxyInput, proxyUsernameInput, proxyPasswordInput, cracked, altening, token, null);
    }

    public synchronized void activate(final Session session, final ProxyConfig proxyConfig, final File mcDataDir,
                                        final String status, final String proxyInput, final String proxyUsernameInput,
                                        final String proxyPasswordInput, final boolean cracked, final boolean altening,
                                        final String token, final String refreshToken) throws IOException {
        this.debug("activate status=" + status
                + " cracked=" + cracked
                + " altening=" + altening
                + " user=" + (session == null ? "(null)" : session.getUsername())
                + " uuid=" + (session == null ? "(null)" : mask(session.getPlayerID()))
                + " tokenLen=" + (token == null ? 0 : token.trim().length())
                + " refreshTokenLen=" + (refreshToken == null ? 0 : refreshToken.trim().length())
                + " proxy=" + (proxyConfig == null ? "(none)" : proxyConfig.getDisplayAddress()));
        this.activeSession = session;
        this.activeProxy = proxyConfig;
        this.upsertHistory(session, proxyConfig, status, emptyIfNull(token), cracked, altening, refreshToken);
        AltHistoryStore.save(mcDataDir, this.history);
        this.saveLastProxyPrefs(mcDataDir, proxyInput, proxyUsernameInput, proxyPasswordInput);
        this.saveLastAlt(mcDataDir, session, refreshToken, cracked, altening);
    }

    // last alt marker

    /** Persist the last-used account so startup can re-log it (or fail over to a random name). */
    public synchronized void saveLastAlt(final File mcDataDir, final Session session,
                                         final String refreshToken, final boolean cracked,
                                         final boolean altening) {
        if (session == null) return;
        try {
            AltLastPrefs.save(mcDataDir, session.getUsername(), session.getPlayerID(),
                    refreshToken, cracked, altening, System.currentTimeMillis());
            this.lastAltPrefs = AltLastPrefs.load(mcDataDir);
            this.debug("saveLastAlt user=" + session.getUsername() + " cracked=" + cracked
                    + " altening=" + altening + " refreshTokenLen=" + emptyIfNull(refreshToken).length());
        } catch (final Exception e) {
            this.debug("saveLastAlt failed: " + e.getMessage());
        }
    }

    /** The last-used alt username (cracked names or real accounts). Empty if never logged in via alt manager. */
    public synchronized String getLastAltUsername() { return this.lastAltPrefs.getUsername(); }
    public synchronized String getLastAltUuid() { return this.lastAltPrefs.getUuid(); }
    public synchronized String getLastAltRefreshToken() { return this.lastAltPrefs.getRefreshToken(); }
    public synchronized boolean isLastAltCracked() { return this.lastAltPrefs.isCracked(); }
    public synchronized boolean isLastAltAltening() { return this.lastAltPrefs.isAltening(); }
    public synchronized boolean hasLastAlt() {
        return !this.lastAltPrefs.getUsername().isEmpty();
    }

    /**
     * Startup username: last used alt if there is one, otherwise a generated random
     * cracked name (AltManager's prefix+suffix+number generator, not the vanilla
     * "ghost<uuid>" default).
     */
    public static String startupUsername(final File mcDataDir) {
        final AltManager m = getInstance();
        m.load(mcDataDir);
        if (m != null && m.hasLastAlt()) {
            return m.getLastAltUsername();
        }
        return generateRandomName();
    }

    /** Public random cracked-name generator (prefix + suffix + 3-digit number). */
    public static String generateRandomName() {
        final String[] prefixes = {
            "xX", "ii", "Craft", "Night", "Dark", "Fire", "Ice", "Blaze", "Storm", "Shadow",
            "Ghost", "Wolf", "Dragon", "Pixel", "Nova", "Aero", "Volt", "Frost", "Ember", "Lunar",
            "Solar", "Omega", "Alpha", "Neon", "Cyber", "Hyper", "Crypto", "Echo", "Ion", "Void"
        };
        final String[] suffixes = {
            "Wolf", "Lord", "King", "Pro", "MC", "Craft", "Xx", "Boy", "Girl", "God",
            "Warrior", "Knight", "Hunter", "Slayer", "Ninja", "Gamer", "Master", "One", "HD", "FTW",
            "Boss", "Star", "Fury", "Blade", "Core", "Dash", "Byte", "Flux", "Hawk", "PvP"
        };
        final java.util.Random rng = new java.util.Random();
        final String name = prefixes[rng.nextInt(prefixes.length)]
                + suffixes[rng.nextInt(suffixes.length)]
                + (100 + rng.nextInt(900));
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    // proxy prefs

    public synchronized void saveLastProxyPrefs(final File mcDataDir, final String proxyAddress, final String proxyUsername,
                                                 final String proxyPassword) throws IOException {
        this.lastProxyAddress = emptyIfNull(proxyAddress);
        this.lastProxyUsername = emptyIfNull(proxyUsername);
        this.lastProxyPassword = emptyIfNull(proxyPassword);
        AltProxyPrefs.save(mcDataDir, this.lastProxyAddress, this.lastProxyUsername, this.lastProxyPassword);
    }

    public synchronized String getLastProxyAddress() { return this.lastProxyAddress; }
    public synchronized String getLastProxyUsername() { return this.lastProxyUsername; }
    public synchronized String getLastProxyPassword() { return this.lastProxyPassword; }

    public synchronized String getLastAlteningApiKey() { return this.lastAlteningApiKey; }

    public synchronized void saveLastAlteningApiKey(final File mcDataDir, final String apiKey) throws IOException {
        this.lastAlteningApiKey = emptyIfNull(apiKey).trim();
        this.debug("saveLastAlteningApiKey len=" + this.lastAlteningApiKey.length());
        AltAlteningPrefs.save(mcDataDir, this.lastAlteningApiKey);
    }

    // baseline (original session before first alt switch)

    public synchronized void captureBaselineIfNeeded(final Session session, final ProxyConfig proxy) {
        if (this.baselineSession != null || session == null) return;
        this.baselineSession = session;
        this.baselineProxy = proxy;
    }

    public synchronized boolean hasBaseline() {
        return this.baselineSession != null;
    }

    public synchronized boolean isOnBaseline(final Session current) {
        return this.baselineSession != null && current != null
                && this.baselineSession.getPlayerID().equals(current.getPlayerID());
    }

    // restore the session that was active before any alt was used
    public synchronized boolean restoreBaseline(final Minecraft mc) {
        if (this.baselineSession == null || mc == null) return false;
        mc.setSession(this.baselineSession);
        this.activeSession = this.baselineSession;
        this.activeProxy = this.baselineProxy;
        AlteningService.switchToMojang();
        return true;
    }

    // last-alt auto-login (called from the main menu at startup)
    //
    // Reloads the last logged-in account from redactedoptions.txt:
    // - cracked -> offline session with the remembered username immediately
    // - Microsoft-> refresh-token auth on a background thread; falls back to a
    //               random cracked name if it fails
    // - altening -> falls back to a random cracked name (needs the API key flow
    //               which the alt manager handles interactively)
    // - nothing remembered -> random cracked name.
    public static void tryAutoLoginFromLast(final Minecraft mc) {
        if (mc == null) return;
        final AltManager m = getInstance();
        m.load(mc.mcDataDir);
        if (!m.hasLastAlt()) return;

        final String name = m.getLastAltUsername();
        final boolean cracked = m.isLastAltCracked();
        final boolean altening = m.isLastAltAltening();
        final String refreshToken = m.getLastAltRefreshToken();

        if (cracked) {
            // cracked: reuse the remembered name directly.
            final String offlineUuid = java.util.UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            mc.setSession(new Session(name, offlineUuid, "0", "legacy"));
            m.debug("tryAutoLoginFromLast cracked name=" + name);
            return;
        }

        if (altening) {
            // Find the history entry for the last alt (match by UUID) - it has the
            // stored altening TOKEN, which is what double-click login uses.
            // (The API key is only for generating NEW accounts, and isn't saved.)
            final String lastUuid = m.getLastAltUuid();
            AltHistoryEntry entry = null;
            for (final AltHistoryEntry e : m.getHistory()) {
                if (!lastUuid.isEmpty() && lastUuid.equalsIgnoreCase(e.getUuid())) { entry = e; break; }
            }
            final AltHistoryEntry tokEntry = entry;
            final String token = tokEntry != null ? tokEntry.getToken() : "";
            if (token == null || token.isEmpty()) {
                m.debug("tryAutoLoginFromLast altening skip (no stored token for uuid=" + lastUuid + ") keeping name=" + mc.getSession().getUsername());
                return;
            }
            // proxy from the stored entry (fall back to direct)
            final String proxy = tokEntry.getProxyAddress();
            final String proxyUser = tokEntry.getProxyUsername();
            final String proxyPass = tokEntry.getProxyPassword();
            final ProxyConfig proxyConfig = ProxyConfig.parse(proxy, proxyUser, proxyPass);
            new Thread("AltManager Altening AutoLogin") {
                @Override
                public void run() {
                    try {
                        m.captureBaselineIfNeeded(mc.getSession(), m.getActiveProxy());
                        final Session session = AlteningService.login(token, proxyConfig);
                        mc.addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                AlteningService.switchToTheAltening();
                                mc.setSession(session);
                                try {
                                    m.activate(session, proxyConfig, mc.mcDataDir, "Auto-login (altening)",
                                            proxy, proxyUser, proxyPass, false, true, token, null);
                                } catch (final IOException ignored) {
                                }
                                m.debug("tryAutoLoginFromLast altening success user=" + session.getUsername());
                            }
                        });
                    } catch (final Exception e) {
                        m.debug("tryAutoLoginFromLast altening failed: " + e.getMessage());
                        // fall back to random cracked name
                        final String nm = AltManager.generateRandomName();
                        final String offUuid = java.util.UUID.nameUUIDFromBytes(
                                ("OfflinePlayer:" + nm).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                        mc.addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                mc.setSession(new Session(nm, offUuid, "0", "legacy"));
                            }
                        });
                    }
                }
            }.start();
            return;
        }

        if (refreshToken.isEmpty()) {
            // Microsoft without refresh token: no re-login path - keep current session
            m.debug("tryAutoLoginFromLast microsoft skip (no refresh token) keeping name=" + mc.getSession().getUsername());
            return;
        }

        // Microsoft: refresh token -> session on a background thread
        new Thread("AltManager AutoLogin") {
            @Override
            public void run() {
                try {
                    final MicrosoftAuthenticator.AuthResult result =
                            MicrosoftAuthenticator.authenticateWithRefreshToken(refreshToken);
                    final Session session = result.session;
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            AlteningService.switchToMojang();
                            mc.setSession(session);
                            try {
                                m.activate(session, null, mc.mcDataDir, "Auto-login (last alt)",
                                        "", "", "", false, false, session.getToken(), result.refreshToken);
                            } catch (final IOException ignored) {
                            }
                            m.debug("tryAutoLoginFromLast microsoft success user=" + session.getUsername());
                        }
                    });
                } catch (final Exception e) {
                    m.debug("tryAutoLoginFromLast microsoft failed: " + e.getMessage());
                    final String nm = AltManager.generateRandomName();
                    final String offUuid = java.util.UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:" + nm).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            mc.setSession(new Session(nm, offUuid, "0", "legacy"));
                        }
                    });
                }
            }
        }.start();
    }

    // history CRUD

    public synchronized void clearHistory(final File mcDataDir) throws IOException {
        this.history.clear();
        AltHistoryStore.save(mcDataDir, this.history);
    }

    public synchronized boolean removeHistoryEntry(final File mcDataDir, final String uuid) throws IOException {
        if (uuid == null || uuid.isEmpty()) return false;

        final Iterator<AltHistoryEntry> it = this.history.iterator();
        while (it.hasNext()) {
            if (uuid.equals(it.next().getUuid())) {
                it.remove();
                AltHistoryStore.save(mcDataDir, this.history);
                return true;
            }
        }
        return false;
    }

    public synchronized ProxyConfig getActiveProxy() {
        return this.activeProxy;
    }

    public synchronized Session getActiveSession() {
        return this.activeSession;
    }

    public synchronized List<AltHistoryEntry> getHistory() {
        return Collections.unmodifiableList(new ArrayList<AltHistoryEntry>(this.history));
    }

    // internal

    private void upsertHistory(final Session session, final ProxyConfig proxyConfig, final String status,
                                final String token, final boolean cracked, final boolean altening) {
        upsertHistory(session, proxyConfig, status, token, cracked, altening, null);
    }

    private void upsertHistory(final Session session, final ProxyConfig proxyConfig, final String status,
                                final String token, final boolean cracked, final boolean altening,
                                final String refreshToken) {
        final String uuid = session.getPlayerID();
        final String proxyAddress = proxyConfig == null ? "" : proxyConfig.getDisplayAddress();
        final String proxyUsername = proxyConfig == null ? "" : proxyConfig.getUsername();
        final String proxyPassword = proxyConfig == null ? "" : proxyConfig.getPassword();
        final long now = System.currentTimeMillis();

        // if we already have this UUID, bump it to the top instead of duplicating
        for (int i = 0; i < this.history.size(); i++) {
            final AltHistoryEntry entry = this.history.get(i);
            if (uuid.equals(entry.getUuid())) {
                if (refreshToken != null) {
                    entry.updateWithRefresh(proxyAddress, proxyUsername, proxyPassword, status, now, token, refreshToken);
                } else {
                    entry.update(proxyAddress, proxyUsername, proxyPassword, status, now, token, cracked, altening);
                }
                this.history.remove(i);
                this.history.add(0, entry);
                trimHistory();
                return;
            }
        }

        this.history.add(0, new AltHistoryEntry(session.getUsername(), uuid, proxyAddress, proxyUsername, proxyPassword, status, now, token, cracked, altening));
        trimHistory();
    }

    private void trimHistory() {
        while (this.history.size() > MAX_HISTORY_SIZE) {
            this.history.remove(this.history.size() - 1);
        }
    }

    private static String emptyIfNull(final String value) {
        return value == null ? "" : value;
    }

    private static String mask(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return "(empty)";
        }
        final String cleaned = value.trim();
        if (cleaned.length() <= 8) {
            return "***" + cleaned.substring(Math.max(0, cleaned.length() - 2));
        }
        return cleaned.substring(0, 4) + "..." + cleaned.substring(cleaned.length() - 4);
    }

    private void debug(final String message) {
        if (DEBUG) {
            final String line = "[AltManagerDebug] " + message;
            System.out.println(line);
            LOG.info(line);
        }
    }
}
