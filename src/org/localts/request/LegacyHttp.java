package org.localts.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Shared HTTP/JSON plumbing for the legacy login.live.com Microsoft auth chain.
// Ported from ColdPlay's MicrosoftAuth.send/readBody/getString/extractUserHash/describeXstsError so
// the five request classes don't each carry their own copy. Plain HttpURLConnection + Gson 2.2.4,
// Java-8 only.
final class LegacyHttp {
    private static final JsonParser JSON = new JsonParser();

    private LegacyHttp() {
    }

    static JsonObject postForm(final String url, final String body, final String rejectMessage) throws IOException {
        return send(url, "application/x-www-form-urlencoded", body, rejectMessage, false);
    }

    static JsonObject postJson(final String url, final String body, final String rejectMessage) throws IOException {
        return send(url, "application/json", body, rejectMessage, false);
    }

    // XSTS POST with XErr-aware 401 handling for the common "no Xbox account / child account" cases.
    static JsonObject postXsts(final String url, final String body) throws IOException {
        return send(url, "application/json", body, "Xbox security token (XSTS) request failed.", true);
    }

    static JsonObject get(final String url, final String bearerToken, final String rejectMessage) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        connection.setRequestProperty("Accept", "application/json");

        final int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            if (code == 401 || code == 403) {
                throw new IOException(rejectMessage);
            }
            throw new IOException(rejectMessage + " (HTTP " + code + ")");
        }
        return parse(readBody(connection.getInputStream()), url);
    }

    private static JsonObject send(final String urlString, final String contentType, final String body,
                                   final String rejectMessage, final boolean xstsErr) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Accept", "application/json");

        final byte[] payload = body.getBytes("UTF-8");
        OutputStream out = null;
        try {
            out = connection.getOutputStream();
            out.write(payload);
        } finally {
            if (out != null) {
                out.close();
            }
        }

        final int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            final String detail = readBody(connection.getErrorStream());
            if (xstsErr && code == 401) {
                throw new IOException(describeXstsError(detail));
            }
            if (code == 401 || code == 403) {
                throw new IOException(rejectMessage);
            }
            throw new IOException(rejectMessage + " (HTTP " + code + ")");
        }
        return parse(readBody(connection.getInputStream()), urlString);
    }

    private static JsonObject parse(final String responseBody, final String url) throws IOException {
        try {
            return JSON.parse(responseBody).getAsJsonObject();
        } catch (final Exception e) {
            throw new IOException("Unexpected response from " + url + ".");
        }
    }

    // Maps the XSTS XErr code (when present) to a human-readable reason.
    static String describeXstsError(final String detail) {
        long xErr = 0L;
        try {
            final JsonObject json = JSON.parse(detail).getAsJsonObject();
            if (json.has("XErr")) {
                xErr = json.get("XErr").getAsLong();
            }
        } catch (final Exception ignored) {
            // fall through to generic message
        }
        if (xErr == 2148916233L) {
            return "This Microsoft account has no Xbox profile - sign in at xbox.com once to create one.";
        }
        if (xErr == 2148916235L) {
            return "Xbox Live is not available in this account's region.";
        }
        if (xErr == 2148916236L || xErr == 2148916237L) {
            return "This account needs adult verification before it can use Xbox Live.";
        }
        if (xErr == 2148916238L) {
            return "This is a child account - add it to a Microsoft Family to enable Xbox Live.";
        }
        return "Xbox security token (XSTS) request was rejected.";
    }

    static String getString(final JsonObject json, final String member) {
        if (json == null || !json.has(member) || json.get(member).isJsonNull()) {
            return null;
        }
        return json.get(member).getAsString();
    }

    // Pulls DisplayClaims.xui[0].uhs out of an Xbox Live / XSTS response.
    static String extractUserHash(final JsonObject json) {
        try {
            final JsonArray xui = json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui");
            if (xui.size() > 0) {
                return getString(xui.get(0).getAsJsonObject(), "uhs");
            }
        } catch (final Exception ignored) {
            // missing/odd shape -> treated as no user hash by the caller
        }
        return null;
    }

    static String readBody(final InputStream inputStream) throws IOException {
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
}
