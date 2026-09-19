package redacted.client.alt;

public final class AltAccount {
    private String token;
    private String username;

    private transient String accessToken;
    private transient String uuid;

    public AltAccount(final String token, final String username) {
        this.token = token;
        this.username = username;
    }

    public String getToken() {
        return this.token;
    }

    public String getUsername() {
        return this.username;
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public String getUuid() {
        return this.uuid;
    }

    public void setSession(final String accessToken, final String uuid, final String resolvedUsername) {
        this.accessToken = accessToken;
        this.uuid = uuid;
        if (resolvedUsername != null && !resolvedUsername.trim().isEmpty()) {
            this.username = resolvedUsername;
        }
    }
}
