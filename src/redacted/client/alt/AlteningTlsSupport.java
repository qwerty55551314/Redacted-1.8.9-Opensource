package redacted.client.alt;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class AlteningTlsSupport {
    private static final String INSECURE_FALLBACK_PROPERTY = "redacted.altening.insecure_tls_fallback";

    private static volatile SSLSocketFactory fallbackSocketFactory;
    private static volatile SSLSocketFactory insecureSocketFactory;
    private static volatile boolean fallbackInstalledGlobally;

    private AlteningTlsSupport() {
    }

    static void installFallbackAsGlobalDefault() throws Exception {
        if (fallbackInstalledGlobally) {
            return;
        }
        synchronized (AlteningTlsSupport.class) {
            if (fallbackInstalledGlobally) {
                return;
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(getFallbackSocketFactory());
            fallbackInstalledGlobally = true;
        }
    }

    static boolean allowInsecureFallback() {
        final String value = System.getProperty(INSECURE_FALLBACK_PROPERTY);
        return value == null || value.trim().isEmpty() || Boolean.parseBoolean(value.trim());
    }

    static void applyFallback(final HttpsURLConnection connection) throws Exception {
        connection.setSSLSocketFactory(getFallbackSocketFactory());
    }

    static void applyInsecureFallback(final HttpsURLConnection connection, final Set<String> allowedHosts) throws Exception {
        connection.setSSLSocketFactory(getInsecureSocketFactory());
        connection.setHostnameVerifier((host, session) ->
                host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT)));
    }

    private static SSLSocketFactory getFallbackSocketFactory() throws Exception {
        SSLSocketFactory factory = fallbackSocketFactory;
        if (factory != null) {
            return factory;
        }
        synchronized (AlteningTlsSupport.class) {
            if (fallbackSocketFactory == null) {
                fallbackSocketFactory = buildFallbackSocketFactory();
            }
            return fallbackSocketFactory;
        }
    }

    private static SSLSocketFactory buildFallbackSocketFactory() throws Exception {
        final X509TrustManager jvmTrustManager = trustManagerFor(null);
        final X509TrustManager windowsRootTrustManager = tryWindowsRootTrustManager();

        final X509TrustManager compositeTrustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
                jvmTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
                try {
                    jvmTrustManager.checkServerTrusted(chain, authType);
                    return;
                } catch (final CertificateException ignored) {
                }

                if (windowsRootTrustManager != null) {
                    windowsRootTrustManager.checkServerTrusted(chain, authType);
                    return;
                }

                throw new CertificateException("Server certificate chain not trusted by JVM or Windows trust stores");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                final X509Certificate[] jvmIssuers = jvmTrustManager.getAcceptedIssuers();
                final X509Certificate[] windowsIssuers = windowsRootTrustManager == null
                        ? new X509Certificate[0]
                        : windowsRootTrustManager.getAcceptedIssuers();

                final X509Certificate[] all = new X509Certificate[jvmIssuers.length + windowsIssuers.length];
                System.arraycopy(jvmIssuers, 0, all, 0, jvmIssuers.length);
                if (windowsIssuers.length > 0) {
                    System.arraycopy(windowsIssuers, 0, all, jvmIssuers.length, windowsIssuers.length);
                }
                return all;
            }
        };

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{compositeTrustManager}, null);
        return context.getSocketFactory();
    }

    private static SSLSocketFactory getInsecureSocketFactory() throws Exception {
        SSLSocketFactory factory = insecureSocketFactory;
        if (factory != null) {
            return factory;
        }

        synchronized (AlteningTlsSupport.class) {
            if (insecureSocketFactory == null) {
                insecureSocketFactory = buildInsecureSocketFactory();
            }
            return insecureSocketFactory;
        }
    }

    private static SSLSocketFactory buildInsecureSocketFactory() throws Exception {
        final TrustManager[] trustManagers = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        return context.getSocketFactory();
    }

    private static X509TrustManager trustManagerFor(final KeyStore keyStore) throws Exception {
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        for (final TrustManager trustManager : tmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new Exception("No X509TrustManager available");
    }

    private static X509TrustManager tryWindowsRootTrustManager() {
        final String osName = Objects.toString(System.getProperty("os.name"), "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return null;
        }
        try {
            final KeyStore windowsRoot = KeyStore.getInstance("Windows-ROOT");
            windowsRoot.load(null, null);
            return trustManagerFor(windowsRoot);
        } catch (final Exception ignored) {
            return null;
        }
    }

    static Set<String> hosts(final String... names) {
        final Set<String> allowed = new HashSet<String>();
        if (names != null) {
            for (final String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    allowed.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return allowed;
    }
}
