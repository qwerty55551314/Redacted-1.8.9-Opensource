package org.localts.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.localts.model.XstsTokenResponse;

import java.io.IOException;

// Step 3: Xbox Live token -> XSTS token (authorize for Minecraft). Ported from ColdPlay's MicrosoftAuth.
// The orchestrator reads isSuccessful()/getError() rather than catching, so XErr rejections come back as a
// failed result instead of an exception.
public class XstsTokenRequest {
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public static XstsTokenResponse getXstsToken(String token) throws Exception {
        final JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        final JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(token)); // Gson 2.2.4 has no add(String) overload
        props.add("UserTokens", userTokens);

        final JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        final XstsTokenResponse result = new XstsTokenResponse();
        try {
            final JsonObject json = LegacyHttp.postXsts(XSTS_URL, body.toString());
            final String xstsToken = LegacyHttp.getString(json, "Token");
            if (xstsToken == null) {
                result.setSuccessful(false);
                result.setError("XSTS response was missing a token.");
                return result;
            }
            result.setToken(xstsToken);
            result.setUserHash(LegacyHttp.extractUserHash(json));
            result.setSuccessful(true);
        } catch (final IOException e) {
            result.setSuccessful(false);
            result.setError(e.getMessage());
        }
        return result;
    }
}
