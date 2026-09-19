package org.localts.request;

import com.google.gson.JsonObject;
import org.localts.model.MinecraftProfileResponse;

import java.io.IOException;
import java.util.UUID;

// Final step: Minecraft access token -> profile (id + name). Ported from ColdPlay's AltAuthService.
public class MinecraftProfileRequest {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public static MinecraftProfileResponse getMinecraftProfile(String accessToken) throws Exception {
        final JsonObject json = LegacyHttp.get(PROFILE_URL, accessToken, "Token rejected by Minecraft Services.");
        final String id = LegacyHttp.getString(json, "id");
        final String name = LegacyHttp.getString(json, "name");
        if (id == null || id.isEmpty() || name == null || name.isEmpty()) {
            throw new IOException("Minecraft profile response was missing id or name.");
        }

        final MinecraftProfileResponse result = new MinecraftProfileResponse();
        result.setUuid(toUuid(id));
        result.setUsername(name);
        return result;
    }

    // The services API returns the id as dashless hex; the model wants a java.util.UUID, so re-insert dashes.
    private static UUID toUuid(final String id) {
        if (id.indexOf('-') >= 0) {
            return UUID.fromString(id);
        }
        final String dashed = id.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }
}
