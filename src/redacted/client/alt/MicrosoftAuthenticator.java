package redacted.client.alt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.Session;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.localts.exception.AuthenticationException;
import org.localts.model.MicrosoftTokenResponse;
import org.localts.model.MinecraftProfileResponse;
import org.localts.model.MinecraftTokenResponse;
import org.localts.model.XboxLiveTokenResponse;
import org.localts.model.XstsTokenResponse;
import org.localts.request.MinecraftProfileRequest;
import org.localts.request.MinecraftTokenRequest;
import org.localts.request.RefreshTokenRequest;
import org.localts.request.XboxLiveTokenRequest;
import org.localts.request.XstsTokenRequest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class MicrosoftAuthenticator {
    private static final Logger LOG = LogManager.getLogger();
    private static final Gson GSON = new Gson();
    private static final boolean DEBUG = true;

    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    // Shared OkHttp client (the lib uses RefreshTokenAuthentication.CLIENT but we need our own for device code)
    static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .followRedirects(false)
            .build();

    // Device code flow

    public static DeviceCodeInfo startDeviceCodeFlow() throws Exception {
        final FormBody body = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE + " offline_access")
                .build();

        final Request request = new Request.Builder()
                .url("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
                .post(body)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() != 200) {
                throw new RuntimeException("Device code request failed: HTTP " + response.code() + " " + response.body().string());
            }

            final JsonObject json = GSON.fromJson(response.body().string(), JsonObject.class);
            if (json == null || !json.has("device_code") || !json.has("user_code")) {
                throw new RuntimeException("Invalid device code response");
            }

            final DeviceCodeInfo info = new DeviceCodeInfo();
            info.deviceCode = json.get("device_code").getAsString();
            info.userCode = json.get("user_code").getAsString();
            info.verificationUri = json.has("verification_uri") ? json.get("verification_uri").getAsString() : "https://microsoft.com/link";
            info.interval = json.has("interval") ? json.get("interval").getAsInt() : 5;
            info.expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 900;
            return info;
        }
    }

    public static MsTokenResponse pollDeviceCode(final String deviceCode, final int interval, final int expiresIn) throws Exception {
        final long deadline = System.currentTimeMillis() + expiresIn * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);

            final FormBody body = new FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", deviceCode)
                    .build();

            final Request request = new Request.Builder()
                    .url("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
                    .post(body)
                    .build();

            try (Response response = CLIENT.newCall(request).execute()) {
                final int responseCode = response.code();
                final String responseBody = response.body().string();

                if (responseCode == 200) {
                    final JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
                    if (json != null && json.has("access_token")) {
                        final MsTokenResponse result = new MsTokenResponse();
                        result.accessToken = json.get("access_token").getAsString();
                        result.refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                        result.expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
                        return result;
                    }
                    throw new RuntimeException("No access_token in poll response");
                }

                final JsonObject err = GSON.fromJson(responseBody, JsonObject.class);
                if (err != null && err.has("error")) {
                    final String error = err.get("error").getAsString();
                    if ("authorization_pending".equals(error)) continue;
                    if ("slow_down".equals(error)) continue;
                    if ("authorization_declined".equals(error)) throw new RuntimeException("Authorization declined by user");
                    if ("expired_token".equals(error)) throw new RuntimeException("Device code expired, please try again");
                    throw new RuntimeException("Device code error: " + error);
                }

                throw new RuntimeException("Unexpected poll response: HTTP " + responseCode);
            }
        }

        throw new RuntimeException("Device code flow timed out");
    }

    // Refresh token -> full auth chain

    public static AuthResult authenticateWithRefreshToken(final String refreshToken) throws Exception {
        debug("authenticateWithRefreshToken starting");

        final MicrosoftTokenResponse msToken = RefreshTokenRequest.refreshToken(refreshToken);
        debug("ms token obtained, expiresIn=" + msToken.getExpiresIn());

        return authenticateWithMsToken(msToken.getAccessToken(), msToken.getRefreshToken());
    }

    public static AuthResult authenticateWithMsToken(final String msAccessToken, final String newRefreshToken) throws Exception {
        debug("authenticateWithMsToken starting");

        final XboxLiveTokenResponse xbl = XboxLiveTokenRequest.getXboxLiveToken(msAccessToken);
        debug("xbl token obtained");

        final XstsTokenResponse xsts = XstsTokenRequest.getXstsToken(xbl.getToken());
        if (!xsts.isSuccessful()) {
            throw new RuntimeException("XSTS error: " + xsts.getError());
        }
        debug("xsts token obtained");

        final MinecraftTokenResponse mc = MinecraftTokenRequest.getMinecraftAccessToken(xsts.getToken(), xbl.getUserHash());
        debug("mc access token obtained, expiresIn=" + mc.getExpiresIn());

        return getMinecraftSession(mc.getAccessToken(), mc.getExpiresIn(), newRefreshToken);
    }

    // Profile fetch

    private static AuthResult getMinecraftSession(final String mcToken, final long expiresIn, final String refreshToken) throws Exception {
        final MinecraftProfileResponse profile = MinecraftProfileRequest.getMinecraftProfile(mcToken);

        final String uuid = profile.getUuid().toString().replace("-", "");
        final String username = profile.getUsername();
        debug("session username=" + username + " uuid=" + uuid + " refreshTokenLen=" + (refreshToken == null ? 0 : refreshToken.length()));

        final Session session = new Session(username, uuid, mcToken, "mojang");
        return new AuthResult(session, refreshToken, expiresIn);
    }

    // Helpers

    private static void debug(final String message) {
        if (DEBUG) {
            final String line = "[MSAuthDebug] " + message;
            System.out.println(line);
            LOG.info(line);
        }
    }

    // Data classes

    public static final class DeviceCodeInfo {
        public String deviceCode;
        public String userCode;
        public String verificationUri;
        public int interval;
        public int expiresIn;
    }

    public static final class MsTokenResponse {
        public String accessToken;
        public String refreshToken;
        public long expiresIn;
    }

    public static final class AuthResult {
        public final Session session;
        public final String refreshToken;
        public final long expiresIn;

        public AuthResult(final Session session, final String refreshToken, final long expiresIn) {
            this.session = session;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }
}
