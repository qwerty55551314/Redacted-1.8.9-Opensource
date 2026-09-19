package redacted.client.alt;

public class AltHistoryEntry {
    private String username;
    private String uuid;
    private String proxyAddress;
    private String proxyUsername;
    private String proxyPassword;
    private String status;
    private long lastConnected;
    private String token;
    private String refreshToken;
    private boolean cracked;
    private boolean altening;

    public AltHistoryEntry() {
    }

    // proxy details + cracked flag
    public AltHistoryEntry(final String username, final String uuid,
                           final String proxyAddress, final String proxyUsername, final String proxyPassword,
                           final String status, final long lastConnected, final String token,
                           final boolean cracked) {
        this(username, uuid, proxyAddress, proxyUsername, proxyPassword, status, lastConnected, token, cracked, false);
    }

    public AltHistoryEntry(final String username, final String uuid,
                           final String proxyAddress, final String proxyUsername, final String proxyPassword,
                           final String status, final long lastConnected, final String token,
                           final boolean cracked, final boolean altening) {
        this.username = username;
        this.uuid = uuid;
        this.proxyAddress = proxyAddress;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        this.status = status;
        this.lastConnected = lastConnected;
        this.token = token;
        this.refreshToken = null;
        this.cracked = cracked;
        this.altening = altening;
    }

    // getters

    public String getUsername()                  { return this.username; }
    public String getUuid()                      { return this.uuid; }
    public String getProxyAddress()              { return emptyIfNull(this.proxyAddress); }
    public String getProxyUsername()             { return emptyIfNull(this.proxyUsername); }
    public String getProxyPassword()             { return emptyIfNull(this.proxyPassword); }
    public String getStatus()                    { return this.status; }
    public long getLastConnected()               { return this.lastConnected; }
    public String getToken()                     { return emptyIfNull(this.token); }
    public String getRefreshToken()              { return emptyIfNull(this.refreshToken); }
    public boolean isCracked()                   { return this.cracked; }
    public boolean isAltening()                  { return this.altening; }

    public boolean isMicrosoft() {
        return !this.cracked && !this.altening;
    }

    public boolean hasRefreshToken() {
        return this.refreshToken != null && !this.refreshToken.trim().isEmpty();
    }

    // update

    public void update(final String proxyAddress, final String proxyUsername, final String proxyPassword,
                       final String status, final long lastConnected, final String token,
                       final boolean cracked) {
        this.update(proxyAddress, proxyUsername, proxyPassword, status, lastConnected, token, cracked, this.altening);
    }

    public void update(final String proxyAddress, final String proxyUsername, final String proxyPassword,
                       final String status, final long lastConnected, final String token,
                       final boolean cracked, final boolean altening) {
        this.proxyAddress = proxyAddress;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        this.status = status;
        this.lastConnected = lastConnected;
        this.token = token;
        this.cracked = cracked;
        this.altening = altening;
    }

    public void updateWithRefresh(final String proxyAddress, final String proxyUsername, final String proxyPassword,
                                   final String status, final long lastConnected, final String token,
                                   final String refreshToken) {
        this.proxyAddress = proxyAddress;
        this.proxyUsername = proxyUsername;
        this.proxyPassword = proxyPassword;
        this.status = status;
        this.lastConnected = lastConnected;
        this.token = token;
        this.refreshToken = refreshToken;
        this.cracked = false;
        this.altening = false;
    }

    private static String emptyIfNull(final String value) {
        return value == null ? "" : value;
    }
}
