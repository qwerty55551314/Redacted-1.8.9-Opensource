package org.localts.request;

import com.google.gson.JsonObject;
import org.localts.model.MicrosoftTokenResponse;

import java.io.IOException;
import java.net.URLEncoder;

// Step 1 of the legacy login.live.com chain: refresh_token -> MS access token (+ rotated refresh token).
// Public Minecraft-launcher client id on the MBI_SSL scope, so the pasted M.C... tokens work with no Azure
// app registration. Ported from ColdPlay's MicrosoftAuth.
public class RefreshTokenRequest {
    private static final String CLIENT_ID = "00000000402B5328";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    public static MicrosoftTokenResponse refreshToken(String refreshToken) throws Exception {
        final String token = refreshToken == null ? "" : refreshToken.trim();
        if (token.isEmpty()) {
            throw new IOException("Refresh token is required.");
        }

        final String form = "client_id=" + CLIENT_ID
                + "&grant_type=refresh_token"
                + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                + "&refresh_token=" + URLEncoder.encode(token, "UTF-8");
        final JsonObject json = LegacyHttp.postForm(TOKEN_URL, form,
                "Refresh token rejected by Microsoft (expired? paste a new one).");

        final String accessToken = LegacyHttp.getString(json, "access_token");
        if (accessToken == null) {
            throw new IOException("Microsoft token response was missing access_token.");
        }

        // Microsoft rotates the refresh token; keep the one we sent if it didn't hand back a new one.
        String rotated = LegacyHttp.getString(json, "refresh_token");
        if (rotated == null || rotated.isEmpty()) {
            rotated = token;
        }

        final MicrosoftTokenResponse result = new MicrosoftTokenResponse();
        result.setAccessToken(accessToken);
        result.setRefreshToken(rotated);
        if (json.has("expires_in") && !json.get("expires_in").isJsonNull()) {
            result.setExpiresIn(json.get("expires_in").getAsLong());
        }
        return result;
    }
}
