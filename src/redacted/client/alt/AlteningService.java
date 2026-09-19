package redacted.client.alt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class AlteningService {
    private static final Logger LOG = LogManager.getLogger();
    private static final String AUTH_URL = "http://authserver.thealtening.com/authenticate";
    private static final Gson GSON = new Gson();
    private static final boolean DEBUG = true;
    private static final Object AUTH_LOCK = new Object();

    private static volatile boolean altSessionActive;
    private static volatile Session originalSession;

    private AlteningService() {
    }

    public static void switchToTheAltening() {
        try {
            AlteningApiClient.installTlsFallbackAsGlobalDefault();
        } catch (final Throwable t) {
            debug("Failed to install Altening TLS fallback: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    public static void switchToMojang() {
        altSessionActive = false;
    }

    public static void joinServer(final GameProfile profile, final String authToken, final String serverId) throws AuthenticationException {
        if (!altSessionActive) {
            Minecraft.getMinecraft().getSessionService().joinServer(profile, authToken, serverId);
            return;
        }

        debug("joinServer profile=" + profile.getName() + " serverId=" + serverId);

        boolean pkixFallback = false;
        boolean insecureFallback = false;
        Exception lastError = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            HttpsURLConnection conn = null;
            try {
                conn = (HttpsURLConnection) URI.create("https://sessionserver.thealtening.com/session/minecraft/join").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("User-Agent", "RedactedClient/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                if (insecureFallback) {
                    AlteningTlsSupport.applyInsecureFallback(conn, AlteningTlsSupport.hosts("sessionserver.thealtening.com"));
                    debug("joinServer using insecure TLS fallback");
                } else if (pkixFallback) {
                    AlteningTlsSupport.applyFallback(conn);
                    debug("joinServer using PKIX TLS fallback");
                }

                final JsonObject body = new JsonObject();
                body.addProperty("accessToken", authToken);
                body.addProperty("selectedProfile", profile.getId().toString());
                body.addProperty("serverId", serverId);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
                }

                final int code = conn.getResponseCode();
                debug("joinServer response code=" + code);

                if (code == 204 || code == 200) {
                    return;
                }

                final String errorBody = readErrorStream(conn);
                final String message;
                if (errorBody != null) {
                    message = "Altening session server returned HTTP " + code + ": " + errorBody;
                } else {
                    message = "Altening session server returned HTTP " + code;
                }

                if (code == 403) {
                    debug("joinServer invalid session: " + message);
                    throw new InvalidCredentialsException(message);
                }

                throw new AuthenticationException(message);
            } catch (final javax.net.ssl.SSLHandshakeException sslEx) {
                lastError = sslEx;
                debug("joinServer SSL handshake failure: " + sslEx.getMessage());
                if (!pkixFallback && AlteningApiClient.isPkixPathFailure(sslEx)) {
                    pkixFallback = true;
                    try { AlteningApiClient.installTlsFallbackAsGlobalDefault(); } catch (final Exception ignored) {}
                    continue;
                }
                if (!insecureFallback && AlteningTlsSupport.allowInsecureFallback()) {
                    insecureFallback = true;
                    continue;
                }
                break;
            } catch (final AuthenticationException e) {
                throw e;
            } catch (final Exception e) {
                lastError = e;
                debug("joinServer attempt " + (attempt + 1) + " failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                if (attempt < 2) {
                    continue;
                }
                break;
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (final Exception ignored) {}
                }
            }
        }

        debug("joinServer all attempts failed");
        if (lastError instanceof AuthenticationException) {
            throw (AuthenticationException) lastError;
        }
        throw new AuthenticationUnavailableException("Altening auth servers are down" + (lastError != null ? ": " + lastError.getMessage() : ""));
    }

    public static boolean isAltSessionActive() {
        return altSessionActive;
    }

    public static boolean hasOriginalSession() {
        return originalSession != null;
    }

    public static Session authenticate(final String token) throws Exception {
        return authenticate(token, null);
    }

    public static Session authenticate(final String token, final ProxyConfig proxyConfig) throws Exception {
        final String cleaned = token == null ? "" : token.trim();
        if (cleaned.isEmpty()) {
            throw new Exception("TheAltening token is required.");
        }

        debug("auth start token=" + maskToken(cleaned) + " proxy=" + proxyLabel(proxyConfig));

        final JsonObject payload = new JsonObject();
        final JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        payload.add("agent", agent);
        payload.addProperty("username", cleaned);
        payload.addProperty("password", "RedactedClient");
        payload.addProperty("clientToken", java.util.UUID.randomUUID().toString());
        payload.addProperty("requestUser", true);

        final JsonObject authResult = withProxyAuthenticator(proxyConfig, () -> tryAuthenticate(payload, proxyConfig));

        if (authResult.has("accessToken")) {
            final String accessToken = authResult.get("accessToken").getAsString();
            final JsonObject profile = authResult.getAsJsonObject("selectedProfile");
            if (profile == null) {
                throw new Exception("TheAltening auth server returned no selected profile");
            }

            final String username = profile.get("name").getAsString();
            final String uuid = profile.get("id").getAsString();
            if (username == null || username.trim().isEmpty() || uuid == null || uuid.trim().isEmpty() || accessToken == null || accessToken.trim().isEmpty()) {
                throw new Exception("TheAltening auth server returned an incomplete profile");
            }

            debug("auth success username=" + preview(username) + " uuid=" + maskUuid(uuid) + " token=" + maskToken(accessToken));
            return new Session(username, normalizeUUID(uuid), accessToken, "mojang");
        }

        final String username = authResult.get("username").getAsString();
        final String uuid = authResult.get("uuid").getAsString();
        final String altToken = authResult.get("token").getAsString();
        debug("auth fallback username=" + preview(username) + " uuid=" + maskUuid(uuid) + " token=" + maskToken(altToken));
        return new Session(username, normalizeUUID(uuid), altToken, "mojang");
    }

    public static Session login(final String token) throws Exception {
        return login(token, null);
    }

    public static Session login(final String token, final ProxyConfig proxyConfig) throws Exception {
        final Minecraft mc = Minecraft.getMinecraft();
        if (originalSession == null && mc != null) {
            originalSession = mc.getSession();
        }

        switchToTheAltening();
        final Session session = authenticate(token, proxyConfig);
        altSessionActive = true;
        return session;
    }

    public static Session login(final AltAccount alt) throws Exception {
        return login(alt, null);
    }

    public static Session login(final AltAccount alt, final ProxyConfig proxyConfig) throws Exception {
        final Session session = login(alt.getToken(), proxyConfig);
        alt.setSession(session.getToken(), session.getPlayerID(), session.getUsername());
        return session;
    }

    public static void restoreOriginalSession() {
        if (originalSession == null) {
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.setSession(originalSession);
        }
        switchToMojang();
        altSessionActive = false;
        originalSession = null;
    }

    private static JsonObject tryAuthenticate(final JsonObject payload, final ProxyConfig proxyConfig) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(URI.create(AUTH_URL), proxyConfig);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "RedactedClient/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(false);
            applyProxyAuth(conn, proxyConfig);

            final byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            final int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                throw new Exception("Unexpected redirect from authentication endpoint");
            }

            if (code == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    final StringBuilder builder = new StringBuilder();
                    final char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        builder.append(buffer, 0, read);
                    }
                    final JsonObject json = GSON.fromJson(builder.toString(), JsonObject.class);
                    if (json != null) {
                        debug("auth server returned valid response");
                        return json;
                    }
                }
            }

            if (code != 200) {
                final String error = readError(conn, "Authentication failed (HTTP " + code + ")");
                debug("auth server returned error: " + error);
                throw new Exception(error);
            }
        } catch (final java.net.SocketTimeoutException e) {
            debug("auth server timed out, falling back to direct token use");
        } catch (final Exception e) {
            if (!(e.getMessage() != null && e.getMessage().contains("redirect"))) {
                debug("auth server error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                        + ", falling back to direct token use");
            } else {
                throw e;
            }
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (final Exception ignored) {
                }
            }
        }

        final JsonObject fallback = new JsonObject();
        final String token = payload.get("username").getAsString();
        final String username = extractUsernameFromToken(token);
        final String uuid = java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
        fallback.addProperty("username", username);
        fallback.addProperty("uuid", uuid);
        fallback.addProperty("token", token);
        return fallback;
    }

    private static String extractUsernameFromToken(final String token) {
        final String emailPrefix = token.contains("@") ? token.substring(0, token.indexOf('@')) : token;
        final String clean = emailPrefix.replaceAll("[^a-zA-Z0-9_]", "");
        if (clean.length() >= 3 && clean.length() <= 16) {
            return clean;
        }
        return "Alt" + Math.abs(token.hashCode() % 100000);
    }

    private static String readErrorStream(final HttpURLConnection conn) {
        try {
            final InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(errorStream, StandardCharsets.UTF_8)) {
                final StringBuilder builder = new StringBuilder();
                final char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
                final String body = builder.toString().trim();
                return body.isEmpty() ? null : body;
            }
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static String readError(final HttpURLConnection conn, final String fallback) throws Exception {
        final InputStream errorStream = conn.getErrorStream();
        if (errorStream == null) {
            return fallback;
        }

        try (InputStreamReader reader = new InputStreamReader(errorStream, StandardCharsets.UTF_8)) {
            final BufferedReader buffered = new BufferedReader(reader);
            final StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffered.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }

            final String body = builder.toString().trim();
            if (body.isEmpty()) {
                return fallback;
            }

            try {
                final JsonObject json = GSON.fromJson(body, JsonObject.class);
                if (json != null && json.has("errorMessage")) {
                    return json.get("errorMessage").getAsString();
                }
            } catch (final Exception ignored) {
            }

            return body;
        }
    }

    // Data persistence

    public static AltData loadData() {
        final File file = getDataFile();
        if (!file.exists()) {
            return new AltData();
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            final AltData data = GSON.fromJson(reader, AltData.class);
            return data != null ? data : new AltData();
        } catch (final Exception e) {
            debug("Failed to load alt data: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return new AltData();
        }
    }

    public static void saveData(final AltData data) {
        final File file = getDataFile();
        final File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (final Exception e) {
            debug("Failed to save alt data: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static File getDataFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "Redacted/alts.json");
    }

    // Helpers

    private static String normalizeUUID(final String uuid) {
        if (uuid == null) {
            return "";
        }
        final String cleaned = uuid.trim();
        if (cleaned.isEmpty() || cleaned.contains("-")) {
            return cleaned;
        }
        return cleaned.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
    }

    private static String maskToken(final String token) {
        if (token == null || token.trim().isEmpty()) {
            return "(empty)";
        }
        final String cleaned = token.trim();
        if (cleaned.length() <= 8) {
            return "***" + cleaned.substring(Math.max(0, cleaned.length() - 2));
        }
        return cleaned.substring(0, 4) + "..." + cleaned.substring(cleaned.length() - 4);
    }

    private static String maskUuid(final String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return "(empty)";
        }
        final String cleaned = uuid.trim();
        if (cleaned.length() <= 12) {
            return cleaned;
        }
        return cleaned.substring(0, 8) + "..." + cleaned.substring(cleaned.length() - 4);
    }

    private static String preview(final String value) {
        if (value == null) {
            return "";
        }
        final String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= 160) {
            return cleaned;
        }
        return cleaned.substring(0, 160) + "...";
    }

    private static void debug(final String message) {
        if (DEBUG) {
            final String line = "[AlteningDebug] " + message;
            System.out.println(line);
            LOG.info(line);
        }
    }

    private static HttpURLConnection openConnection(final URI uri, final ProxyConfig proxyConfig) throws Exception {
        final java.net.URLConnection raw;
        if (proxyConfig == null) {
            raw = uri.toURL().openConnection();
        } else {
            final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()));
            raw = uri.toURL().openConnection(proxy);
        }

        if (!(raw instanceof HttpURLConnection)) {
            throw new Exception("Unsupported connection type");
        }
        return (HttpURLConnection) raw;
    }

    private static void applyProxyAuth(final HttpURLConnection conn, final ProxyConfig proxyConfig) {
        if (conn == null || proxyConfig == null) {
            return;
        }
        final String username = proxyConfig.getUsername();
        final String password = proxyConfig.getPassword();
        if (username == null || username.trim().isEmpty() || password == null) {
            return;
        }

        final String token = username.trim() + ":" + password;
        final String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Proxy-Authorization", "Basic " + encoded);
    }

    private static String proxyLabel(final ProxyConfig proxyConfig) {
        if (proxyConfig == null) {
            return "(direct)";
        }
        final String user = proxyConfig.getUsername();
        return proxyConfig.getDisplayAddress() + (user == null || user.trim().isEmpty() ? "" : " user=" + preview(user));
    }

    private static <T> T withProxyAuthenticator(final ProxyConfig proxyConfig, final RequestCallable<T> callable) throws Exception {
        if (proxyConfig == null) {
            return callable.call();
        }

        final String username = proxyConfig.getUsername();
        final String password = proxyConfig.getPassword();
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return callable.call();
        }

        synchronized (AUTH_LOCK) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(username.trim(), password.toCharArray());
                    }
                    return null;
                }
            });
            return callable.call();
        }
    }

    // Data classes

    public static final class AltData {
        public String apiKey = "";
        public List<AltAccount> alts = new ArrayList<>();
    }

    private interface RequestCallable<T> {
        T call() throws Exception;
    }
}
