package org.localts.model;

public class XstsTokenResponse {
    private String token;
    private String userHash;
    private boolean successful;
    private String error;

    public String getToken() { return token; }
    public String getUserHash() { return userHash; }
    public boolean isSuccessful() { return successful; }
    public String getError() { return error; }
    public void setToken(String v) { token = v; }
    public void setUserHash(String v) { userHash = v; }
    public void setSuccessful(boolean v) { successful = v; }
    public void setError(String v) { error = v; }
}
