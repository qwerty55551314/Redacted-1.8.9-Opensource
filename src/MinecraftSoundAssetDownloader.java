import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinecraftSoundAssetDownloader {
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCE_BASE_URL = "https://resources.download.minecraft.net/";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private MinecraftSoundAssetDownloader() {
    }

    public static void ensureSoundAssets(File assetsDir, String minecraftVersion, String assetIndexId) throws IOException {
        File indexFile = new File(new File(assetsDir, "indexes"), assetIndexId + ".json");
        JsonObject indexJson = tryReadJson(indexFile);

        if (!hasSoundObjects(indexJson)) {
            AssetIndexInfo assetIndexInfo = fetchAssetIndexInfo(minecraftVersion);

            if (!assetIndexId.equals(assetIndexInfo.id)) {
                System.out.println("[Assets] Version " + minecraftVersion + " uses asset index " + assetIndexInfo.id + ", saving it as " + assetIndexId + " for this launch.");
            }

            System.out.println("[Assets] Downloading Minecraft asset index " + assetIndexId + "...");
            downloadFile(assetIndexInfo.url, indexFile, assetIndexInfo.sha1, assetIndexInfo.size);
            indexJson = readJson(indexFile);
        }

        List<AssetObject> soundAssets = collectSoundAssets(getObject(indexJson, "objects"));

        if (soundAssets.isEmpty()) {
            throw new IOException("Asset index " + indexFile + " does not contain Minecraft sound assets.");
        }

        int missing = 0;

        for (AssetObject asset : soundAssets) {
            if (!isValidObjectFile(getObjectFile(assetsDir, asset.hash), asset.hash, asset.size)) {
                ++missing;
            }
        }

        if (missing == 0) {
            System.out.println("[Assets] Minecraft sound assets are ready (" + soundAssets.size() + " files).");
            return;
        }

        System.out.println("[Assets] Downloading " + missing + " missing Minecraft sound assets...");
        int downloaded = 0;

        for (AssetObject asset : soundAssets) {
            File objectFile = getObjectFile(assetsDir, asset.hash);

            if (isValidObjectFile(objectFile, asset.hash, asset.size)) {
                continue;
            }

            String url = RESOURCE_BASE_URL + asset.hash.substring(0, 2) + "/" + asset.hash;
            downloadFile(url, objectFile, asset.hash, asset.size);
            ++downloaded;

            if (downloaded == missing || downloaded % 50 == 0) {
                System.out.println("[Assets] Downloaded " + downloaded + "/" + missing + " sound assets.");
            }
        }
    }

    private static AssetIndexInfo fetchAssetIndexInfo(String minecraftVersion) throws IOException {
        JsonObject manifest = readJsonFromUrl(VERSION_MANIFEST_URL);
        JsonArray versions = getArray(manifest, "versions");

        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();

            if (minecraftVersion.equals(getString(version, "id"))) {
                JsonObject versionJson = readJsonFromUrl(getString(version, "url"));
                JsonObject assetIndex = getObject(versionJson, "assetIndex");
                return new AssetIndexInfo(getString(assetIndex, "id"), getString(assetIndex, "url"), getString(assetIndex, "sha1"), getLong(assetIndex, "size", -1L));
            }
        }

        throw new IOException("Unable to find Minecraft version " + minecraftVersion + " in the version manifest.");
    }

    private static boolean hasSoundObjects(JsonObject indexJson) {
        if (indexJson == null) {
            return false;
        }

        JsonObject objects = getObject(indexJson, "objects");

        if (objects == null) {
            return false;
        }

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            if (isSoundAsset(entry.getKey())) {
                return true;
            }
        }

        return false;
    }

    private static List<AssetObject> collectSoundAssets(JsonObject objects) throws IOException {
        Map<String, AssetObject> assetsByHash = new LinkedHashMap<String, AssetObject>();

        if (objects == null) {
            return new ArrayList<AssetObject>();
        }

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            if (!isSoundAsset(entry.getKey())) {
                continue;
            }

            JsonObject object = entry.getValue().getAsJsonObject();
            String hash = getString(object, "hash");

            if (hash.length() < 2) {
                throw new IOException("Invalid asset hash for " + entry.getKey() + ": " + hash);
            }

            if (!assetsByHash.containsKey(hash)) {
                assetsByHash.put(hash, new AssetObject(entry.getKey(), hash, getLong(object, "size", -1L)));
            }
        }

        List<AssetObject> assets = new ArrayList<AssetObject>(assetsByHash.values());

        Collections.sort(assets, new Comparator<AssetObject>() {
            @Override
            public int compare(AssetObject left, AssetObject right) {
                return left.name.compareTo(right.name);
            }
        });

        return assets;
    }

    private static boolean isSoundAsset(String name) {
        return "minecraft/sounds.json".equals(name) || name.startsWith("minecraft/sounds/");
    }

    private static JsonObject tryReadJson(File file) throws IOException {
        if (!file.isFile()) {
            return null;
        }

        try {
            return readJson(file);
        } catch (IOException exception) {
            System.out.println("[Assets] Existing asset index could not be read, it will be downloaded again: " + exception.getMessage());
            return null;
        }
    }

    private static JsonObject readJson(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);

            if (!element.isJsonObject()) {
                throw new IOException("Expected a JSON object in " + file);
            }

            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("Unable to parse JSON from " + file, exception);
        }
    }

    private static JsonObject readJsonFromUrl(String url) throws IOException {
        HttpURLConnection connection = openConnection(url);

        try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);

            if (!element.isJsonObject()) {
                throw new IOException("Expected a JSON object from " + url);
            }

            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("Unable to parse JSON from " + url, exception);
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadFile(String url, File target, String expectedSha1, long expectedSize) throws IOException {
        if (isValidObjectFile(target, expectedSha1, expectedSize)) {
            return;
        }

        File parent = target.getParentFile();

        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory " + parent);
        }

        File tempFile = File.createTempFile(target.getName(), ".download", parent);
        HttpURLConnection connection = openConnection(url);

        try {
            MessageDigest digest = newSha1Digest();
            long bytesCopied = 0L;

            try (InputStream inputStream = new DigestInputStream(connection.getInputStream(), digest);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    bytesCopied += read;
                }
            }

            if (expectedSize >= 0L && bytesCopied != expectedSize) {
                throw new IOException("Downloaded " + bytesCopied + " bytes from " + url + ", expected " + expectedSize);
            }

            String actualSha1 = toHex(digest.digest());

            if (expectedSha1 != null && !expectedSha1.equalsIgnoreCase(actualSha1)) {
                throw new IOException("Downloaded " + url + " with SHA-1 " + actualSha1 + ", expected " + expectedSha1);
            }

            Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();

            if (tempFile.isFile() && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }
    }

    private static HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "RedactedClientV2");

        int responseCode = connection.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("HTTP " + responseCode + " while downloading " + url);
        }

        return connection;
    }

    private static boolean isValidObjectFile(File file, String expectedSha1, long expectedSize) throws IOException {
        if (!file.isFile()) {
            return false;
        }

        if (expectedSize >= 0L && file.length() != expectedSize) {
            return false;
        }

        return expectedSha1 == null || expectedSha1.equalsIgnoreCase(sha1(file));
    }

    private static File getObjectFile(File assetsDir, String hash) {
        return new File(new File(assetsDir, "objects"), hash.substring(0, 2) + File.separator + hash);
    }

    private static String sha1(File file) throws IOException {
        MessageDigest digest = newSha1Digest();

        try (InputStream inputStream = new DigestInputStream(new FileInputStream(file), digest)) {
            byte[] buffer = new byte[8192];

            while (inputStream.read(buffer) != -1) {
            }
        }

        return toHex(digest.digest());
    }

    private static MessageDigest newSha1Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-1 digest is not available.", exception);
        }
    }

    private static JsonArray getArray(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);

        if (element == null || !element.isJsonArray()) {
            throw new IOException("Missing JSON array: " + name);
        }

        return element.getAsJsonArray();
    }

    private static JsonObject getObject(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);

        if (element == null || !element.isJsonPrimitive()) {
            throw new IOException("Missing JSON string: " + name);
        }

        return element.getAsString();
    }

    private static long getLong(JsonObject object, String name, long fallback) {
        JsonElement element = object.get(name);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsLong();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);

        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 255));
        }

        return builder.toString();
    }

    private static final class AssetIndexInfo {
        private final String id;
        private final String url;
        private final String sha1;
        private final long size;

        private AssetIndexInfo(String id, String url, String sha1, long size) {
            this.id = id;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    private static final class AssetObject {
        private final String name;
        private final String hash;
        private final long size;

        private AssetObject(String name, String hash, long size) {
            this.name = name;
            this.hash = hash;
            this.size = size;
        }
    }
}
