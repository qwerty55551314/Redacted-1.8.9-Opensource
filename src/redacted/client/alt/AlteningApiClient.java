package redacted.client.alt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

public final class AlteningApiClient {
    private static final Logger LOG = LogManager.getLogger();
    private static final String BASE_URL_HTTPS = "https://api.thealtening.com/v2/";
    private static final String ALTENING_API_HOST = "api.thealtening.com";
    private static final Gson GSON = new Gson();
    private static final int MAX_REDIRECTS = 3;
    private static final boolean DEBUG = true;
    private static final Object AUTH_LOCK = new Object();

    private static final Set<String> ALLOWED_HOSTS = new HashSet<String>(Arrays.asList(
            "api.thealtening.com",
            "authserver.thealtening.com",
            "sessionserver.thealtening.com"
    ));

    private AlteningApiClient() {
    }

    public static AltAccount generate(final String apiKey) throws Exception {
        return generate(apiKey, null);
    }

    public static AltAccount generate(final String apiKey, final ProxyConfig proxyConfig) throws Exception {
        final String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new Exception("TheAltening API key is required.");
        }

        debug("generate start key=" + maskKey(key) + " proxy=" + proxyLabel(proxyConfig));
        final String path = "generate?key=" + URLEncoder.encode(key, "UTF-8");
        final String responseBody = fetchGenerateResponse(path, proxyConfig);
        debug("generate raw response length=" + responseBody.length() + " preview=" + preview(responseBody));

        final JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
        if (json == null) {
            throw new Exception("Empty response from TheAltening API");
        }

        if (json.has("limit") && json.get("limit").getAsBoolean()) {
        }

        if (!json.has("token")) {
            throw new Exception("TheAltening API response was missing a token.");
        }

        final String token = json.get("token").getAsString();
        final String username = json.has("username") ? json.get("username").getAsString() : "Unknown";
        debug("generate success username=" + safe(username) + " token=" + maskToken(token));
        return new AltAccount(token, username);
    }

    private static String fetchGenerateResponse(final String path, final ProxyConfig proxyConfig) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                debug("generate attempt=" + (attempt + 1) + " url=" + BASE_URL_HTTPS + path + " proxy=" + proxyLabel(proxyConfig));
                return withProxyAuthenticator(proxyConfig, new RequestCallable<String>() {
                    @Override
                    public String call() throws Exception {
                        return httpGet(BASE_URL_HTTPS + path, proxyConfig);
                    }
                });
            } catch (final Exception exception) {
                lastError = exception;
                debug("generate attempt=" + (attempt + 1) + " failed type=" + exception.getClass().getSimpleName() + " msg=" + safeMessage(exception));
                if (isPkixPathFailure(exception)) {
                    throw new Exception("Certificate trust failed (PKIX). Check clock and disable HTTPS scanning in proxy/VPN/AV.", exception);
                }
                if (!(exception instanceof SocketTimeoutException)
                        && !(exception.getCause() instanceof SocketTimeoutException)) {
                    break;
                }
            }
        }

        throw new Exception("TheAltening API timed out while generating a token. Retry or check your connection.", lastError);
    }

    public static void installTlsFallbackAsGlobalDefault() throws Exception {
        AlteningTlsSupport.installFallbackAsGlobalDefault();
    }

    public static boolean isPkixPathFailure(final Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            final String message = cursor.getMessage();
            if (message != null) {
                final String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("pkix path building failed")
                        || normalized.contains("unable to find valid certification path")
                        || normalized.contains("suncertpathbuilderexception")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String httpGet(final String url, final ProxyConfig proxyConfig) throws Exception {
        final long startedAt = System.nanoTime();
        String currentUrl = url;
        if (!isHttpsUrl(currentUrl) || !isAllowedApiHost(currentUrl)) {
            throw new Exception("Blocked non-approved Altening API URL");
        }

        int redirects = 0;
        boolean pkixFallback = false;
        boolean insecureFallback = false;

        while (redirects < MAX_REDIRECTS) {
            final HttpURLConnection conn = insecureFallback
                    ? openInsecureAllowedHostConnection(currentUrl, proxyConfig)
                    : openAllowedConnection(currentUrl, proxyConfig, pkixFallback);

            try {
                debug("httpGet connect url=" + currentUrl + " pkixFallback=" + pkixFallback
                        + " insecureFallback=" + insecureFallback + " redirect=" + redirects
                        + " proxy=" + proxyLabel(proxyConfig));
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "RedactedClient/1.0");
                conn.setRequestProperty("Accept", "application/json");
                applyProxyAuth(conn, proxyConfig);

                final int code = conn.getResponseCode();
                debug("httpGet status=" + code + " url=" + currentUrl);
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    final String location = conn.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new Exception("Redirect with no Location header");
                    }
                    final String redirectedUrl = URI.create(currentUrl).resolve(location).toString();
                    debug("httpGet redirect -> " + redirectedUrl);
                    if (!isHttpsUrl(redirectedUrl) || !isAllowedApiHost(redirectedUrl)) {
                        throw new Exception("Blocked redirect to non-approved Altening API host");
                    }
                    currentUrl = redirectedUrl;
                    redirects++;
                    continue;
                }

                if (code == 401 || code == 403) {
                    throw new Exception("Invalid API key or no API access on your plan");
                }
                if (code == 429) {
                    throw new Exception("Rate limited, try again later");
                }
                if (code != 200) {
                    debug("httpGet non-200 response body=" + preview(readBody(conn.getErrorStream())));
                    throw new Exception("TheAltening API returned HTTP " + code);
                }

                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    final StringBuilder builder = new StringBuilder();
                    final char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        builder.append(buffer, 0, read);
                    }
                    debug("httpGet success bytes=" + builder.length() + " elapsedMs=" + elapsedMs(startedAt));
                    return builder.toString();
                }
            } catch (final SSLHandshakeException exception) {
                debug("httpGet ssl handshake failure msg=" + safeMessage(exception));
                if (!pkixFallback && isPkixPathFailure(exception)) {
                    pkixFallback = true;
                    AlteningTlsSupport.installFallbackAsGlobalDefault();
                    continue;
                }
                if (isPkixPathFailure(exception)
                        && !insecureFallback
                        && AlteningTlsSupport.allowInsecureFallback()) {
                    insecureFallback = true;
                    continue;
                }
                throw exception;
            } finally {
                conn.disconnect();
            }
        }

        throw new Exception("Too many redirects");
    }

    private static boolean isHttpsUrl(final String url) {
        return "https".equalsIgnoreCase(URI.create(url).getScheme());
    }

    private static boolean isAllowedApiHost(final String url) {
        final String host = URI.create(url).getHost();
        return ALTENING_API_HOST.equalsIgnoreCase(host);
    }

    private static HttpURLConnection openAllowedConnection(final String url, final ProxyConfig proxyConfig, final boolean usePkixFallback) throws Exception {
        final URI uri = URI.create(url);
        if (!isAllowed(uri)) {
            throw new Exception("Blocked non-approved Altening HTTPS URL");
        }

        final URLConnection raw = openConnection(uri, proxyConfig);
        if (!(raw instanceof HttpURLConnection)) {
            throw new Exception("Unsupported connection type");
        }
        final HttpURLConnection connection = (HttpURLConnection) raw;

        if (usePkixFallback) {
            if (!(connection instanceof HttpsURLConnection)) {
                throw new Exception("PKIX fallback requires HTTPS connection");
            }
            AlteningTlsSupport.applyFallback((HttpsURLConnection) connection);
        }

        return connection;
    }

    private static HttpURLConnection openInsecureAllowedHostConnection(final String url, final ProxyConfig proxyConfig) throws Exception {
        final URI uri = URI.create(url);
        if (!isAllowed(uri)) {
            throw new Exception("Blocked non-approved Altening HTTPS URL");
        }

        final URLConnection raw = openConnection(uri, proxyConfig);
        if (!(raw instanceof HttpsURLConnection)) {
            throw new Exception("Insecure fallback requires HTTPS connection");
        }

        final HttpsURLConnection httpsConnection = (HttpsURLConnection) raw;
        AlteningTlsSupport.applyInsecureFallback(httpsConnection, ALLOWED_HOSTS);
        return httpsConnection;
    }

    private static URLConnection openConnection(final URI uri, final ProxyConfig proxyConfig) throws Exception {
        if (proxyConfig == null) {
            return uri.toURL().openConnection();
        }
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()));
        return uri.toURL().openConnection(proxy);
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

    private static boolean isAllowed(final URI uri) {
        if (uri == null) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        final String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            return false;
        }
        return ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private static String safeMessage(final Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return "unknown error";
        }
        return throwable.getMessage().trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static String readBody(final InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            final StringBuilder builder = new StringBuilder();
            final char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
                if (builder.length() >= 512) {
                    break;
                }
            }
            return builder.toString();
        } catch (final Exception ignored) {
            return "";
        }
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

    private static String maskKey(final String key) {
        if (key == null || key.trim().isEmpty()) {
            return "(empty)";
        }
        final String cleaned = key.trim();
        if (cleaned.length() <= 8) {
            return "***" + cleaned.substring(cleaned.length() - 2);
        }
        return cleaned.substring(0, 4) + "..." + cleaned.substring(cleaned.length() - 4);
    }

    private static String maskToken(final String token) {
        if (token == null || token.trim().isEmpty()) {
            return "(empty)";
        }
        final String cleaned = token.trim();
        if (cleaned.length() <= 8) {
            return "***" + cleaned.substring(cleaned.length() - 2);
        }
        return cleaned.substring(0, 4) + "..." + cleaned.substring(cleaned.length() - 4);
    }

    private static String safe(final String value) {
        return value == null ? "(null)" : preview(value);
    }

    private static String proxyLabel(final ProxyConfig proxyConfig) {
        if (proxyConfig == null) {
            return "(direct)";
        }
        final String user = proxyConfig.getUsername();
        return proxyConfig.getDisplayAddress() + (user == null || user.trim().isEmpty() ? "" : " user=" + preview(user));
    }

    private static long elapsedMs(final long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1000000L;
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

    private interface RequestCallable<T> {
        T call() throws Exception;
    }

    private static void debug(final String message) {
        if (DEBUG) {
            final String line = "[AlteningDebug] " + message;
            System.out.println(line);
            LOG.info(line);
        }
    }
}
