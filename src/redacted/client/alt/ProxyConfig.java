package redacted.client.alt;

import java.net.InetSocketAddress;

public final class ProxyConfig {
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private ProxyConfig(final String host, final int port, final String username, final String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public static ProxyConfig parse(final String proxyInput, final String usernameInput, final String passwordInput) {
        final String proxy = proxyInput == null ? "" : proxyInput.trim();
        final String username = usernameInput == null ? "" : usernameInput.trim();
        final String password = passwordInput == null ? "" : passwordInput;

        if (proxy.isEmpty()) {
            return null;
        }

        if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("Proxy username and password are required.");
        }

        final String host;
        final String portPart;

        if (proxy.startsWith("[")) {
            final int close = proxy.indexOf(']');
            if (close < 0 || close + 2 > proxy.length() || proxy.charAt(close + 1) != ':') {
                throw new IllegalArgumentException("Proxy must be host:port.");
            }
            host = proxy.substring(1, close).trim();
            portPart = proxy.substring(close + 2).trim();
        } else {
            final int split = proxy.lastIndexOf(':');
            if (split <= 0 || split == proxy.length() - 1) {
                throw new IllegalArgumentException("Proxy must be host:port.");
            }
            host = proxy.substring(0, split).trim();
            portPart = proxy.substring(split + 1).trim();
        }

        if (host.isEmpty()) {
            throw new IllegalArgumentException("Proxy host is required.");
        }

        final int port;
        try {
            port = Integer.parseInt(portPart);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Proxy port must be a number.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535.");
        }

        return new ProxyConfig(host, port, username, password);
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public String getDisplayAddress() {
        return this.host + ":" + this.port;
    }

    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(this.host, this.port);
    }
}
