package org.localts.model;

public class MicrosoftTokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setAccessToken(String v) { accessToken = v; }
    public void setRefreshToken(String v) { refreshToken = v; }
    public void setExpiresIn(long v) { expiresIn = v; }
}
