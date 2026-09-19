package org.localts.model;

public class MinecraftTokenResponse {
    private String accessToken;
    private long expiresIn;

    public String getAccessToken() { return accessToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setAccessToken(String v) { accessToken = v; }
    public void setExpiresIn(long v) { expiresIn = v; }
}
