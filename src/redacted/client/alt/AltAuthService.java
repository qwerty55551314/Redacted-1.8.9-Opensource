package redacted.client.alt;

import com.google.gson.Gson;
import net.minecraft.util.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class AltAuthService {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final Gson GSON = new Gson();

    public Session login(final String tokenInput) throws IOException {
        final String token = tokenInput == null ? "" : tokenInput.trim();
        if (token.isEmpty()) {
            throw new IOException("MS token is required.");
        }

        final HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");

        final int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            final String detail = readBody(connection.getErrorStream());
            if (responseCode == 401 || responseCode == 403) {
                throw new IOException("Token rejected by Minecraft Services.");
            }
            throw new IOException("Minecraft Services returned HTTP " + responseCode + (detail.isEmpty() ? "." : ": " + detail));
        }

        final String body = readBody(connection.getInputStream());
        final ProfileResponse profile = GSON.fromJson(body, ProfileResponse.class);
        if (profile == null || isEmpty(profile.id) || isEmpty(profile.name)) {
            throw new IOException("Minecraft profile response was missing id or name.");
        }

        return new Session(profile.name, profile.id, token, "mojang");
    }

    private static boolean isEmpty(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String readBody(final InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return builder.toString();
    }

    private static class ProfileResponse {
        private String id;
        private String name;
    }
}
