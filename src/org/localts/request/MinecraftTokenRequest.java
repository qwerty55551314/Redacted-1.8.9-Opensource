package org.localts.request;

import com.google.gson.JsonObject;
import org.localts.model.MinecraftTokenResponse;

import java.io.IOException;

// Step 4: XSTS token -> Minecraft access token. Ported from ColdPlay's MicrosoftAuth.
public class MinecraftTokenRequest {
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    public static MinecraftTokenResponse getMinecraftAccessToken(String token, String userHash) throws Exception {
        final JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + token);

        final JsonObject json = LegacyHttp.postJson(MC_LOGIN_URL, body.toString(), "Minecraft Services login failed.");
        final String accessToken = LegacyHttp.getString(json, "access_token");
        if (accessToken == null) {
            throw new IOException("Minecraft login response was missing access_token.");
        }

        final MinecraftTokenResponse result = new MinecraftTokenResponse();
        result.setAccessToken(accessToken);
        if (json.has("expires_in") && !json.get("expires_in").isJsonNull()) {
            result.setExpiresIn(json.get("expires_in").getAsLong());
        }
        return result;
    }
}
