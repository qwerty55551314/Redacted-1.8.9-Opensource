package org.localts.request;

import com.google.gson.JsonObject;
import org.localts.model.XboxLiveTokenResponse;

import java.io.IOException;

// Step 2: MS access token -> Xbox Live token + user hash. Ported from ColdPlay's MicrosoftAuth.
public class XboxLiveTokenRequest {
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";

    public static XboxLiveTokenResponse getXboxLiveToken(String accessToken) throws Exception {
        final JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", accessToken); // legacy MBI_SSL ticket: no "d=" prefix

        final JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        final JsonObject json = LegacyHttp.postJson(XBL_URL, body.toString(), "Xbox Live authentication failed.");
        final String token = LegacyHttp.getString(json, "Token");
        final String userHash = LegacyHttp.extractUserHash(json);
        if (token == null || userHash == null) {
            throw new IOException("Xbox Live response was missing a token or user hash.");
        }

        final XboxLiveTokenResponse result = new XboxLiveTokenResponse();
        result.setToken(token);
        result.setUserHash(userHash);
        return result;
    }
}
