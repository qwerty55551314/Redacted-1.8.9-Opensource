package redacted.client.util.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class HeadCache {
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");

    private static final ConcurrentHashMap<String, ResourceLocation> CACHE = new ConcurrentHashMap<String, ResourceLocation>();
    private static final Set<String> IN_FLIGHT = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<String>());

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
            final Thread t = new Thread(r, "HeadCache");
            t.setDaemon(true);
            return t;
        }
    });

    public static void drawHead(final String uuid, final String username, final boolean cracked,
                                final double cx, final double cy, final double radius) {
        final ResourceLocation loc;
        if (cracked || uuid == null || uuid.isEmpty()) {
            loc = STEVE;
        } else {
            final ResourceLocation cached = CACHE.get(uuid);
            if (cached == null) {
                kickOff(uuid);
                loc = STEVE;
            } else {
                loc = cached;
            }
        }
        drawFaceSquare(loc, cx, cy, radius);
    }

    public static void invalidate(final String uuid) {
        if (uuid == null) return;
        CACHE.remove(uuid);
        IN_FLIGHT.remove(uuid);
        FAILED.remove(uuid);
    }

    private static void drawFaceSquare(final ResourceLocation loc, final double cx, final double cy, final double radius) {
        final double x = cx - radius;
        final double y = cy - radius;
        final double s = radius * 2;

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        Minecraft.getMinecraft().getTextureManager().bindTexture(loc);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(8.0 / 64.0, 8.0 / 64.0);   GL11.glVertex2d(x,     y);
        GL11.glTexCoord2d(8.0 / 64.0, 16.0 / 64.0);  GL11.glVertex2d(x,     y + s);
        GL11.glTexCoord2d(16.0 / 64.0, 16.0 / 64.0); GL11.glVertex2d(x + s, y + s);
        GL11.glTexCoord2d(16.0 / 64.0, 8.0 / 64.0);  GL11.glVertex2d(x + s, y);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(40.0 / 64.0, 8.0 / 64.0);  GL11.glVertex2d(x,     y);
        GL11.glTexCoord2d(40.0 / 64.0, 16.0 / 64.0); GL11.glVertex2d(x,     y + s);
        GL11.glTexCoord2d(48.0 / 64.0, 16.0 / 64.0); GL11.glVertex2d(x + s, y + s);
        GL11.glTexCoord2d(48.0 / 64.0, 8.0 / 64.0);  GL11.glVertex2d(x + s, y);
        GL11.glEnd();

        GlStateManager.disableBlend();
    }

    private static void kickOff(final String uuid) {
        if (FAILED.contains(uuid) || IN_FLIGHT.contains(uuid)) return;
        IN_FLIGHT.add(uuid);

        POOL.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Minecraft mc = Minecraft.getMinecraft();
                    final File cacheDir = new File(mc.mcDataDir, "redacted/heads");
                    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                        FAILED.add(uuid);
                        return;
                    }
                    final File pngFile = new File(cacheDir, uuid + ".png");

                    final BufferedImage skin;
                    if (pngFile.isFile()) {
                        skin = ImageIO.read(pngFile);
                    } else {
                        final byte[] bytes = fetchSkin(uuid);
                        if (bytes == null) {
                            FAILED.add(uuid);
                            return;
                        }
                        try (FileOutputStream fos = new FileOutputStream(pngFile)) {
                            fos.write(bytes);
                        }
                        skin = ImageIO.read(new ByteArrayInputStream(bytes));
                    }

                    if (skin == null) {
                        FAILED.add(uuid);
                        return;
                    }

                    mc.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final DynamicTexture tex = new DynamicTexture(skin);
                                final ResourceLocation loc = mc.getTextureManager().getDynamicTextureLocation(
                                        "label_head_" + uuid.replace("-", ""), tex);
                                CACHE.put(uuid, loc);
                            } catch (final Throwable t) {
                                FAILED.add(uuid);
                            }
                        }
                    });
                } catch (final Throwable t) {
                    FAILED.add(uuid);
                } finally {
                    IN_FLIGHT.remove(uuid);
                }
            }
        });
    }

    private static byte[] fetchSkin(final String uuid) {
        try {
            final String undashed = uuid.replace("-", "");
            final HttpURLConnection profile = open(SESSION_URL + undashed);
            if (profile == null) return null;

            final String body;
            try (InputStream in = profile.getInputStream()) {
                body = readAll(in);
            }

            final JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("properties")) return null;
            final JsonElement first = json.getAsJsonArray("properties").get(0);
            final String b64 = first.getAsJsonObject().get("value").getAsString();

            final String texturesJson = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            final JsonObject texturesRoot = new JsonParser().parse(texturesJson).getAsJsonObject();
            final JsonObject textures = texturesRoot.getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) return null;
            final String skinUrl = textures.getAsJsonObject("SKIN").get("url").getAsString();

            final HttpURLConnection skin = open(skinUrl);
            if (skin == null) return null;
            try (InputStream in = skin.getInputStream()) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
                return baos.toByteArray();
            }
        } catch (final Exception e) {
            return null;
        }
    }

    private static HttpURLConnection open(final String url) {
        try {
            final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Redacted/1.0");
            final int code = conn.getResponseCode();
            return code >= 200 && code < 300 ? conn : null;
        } catch (final IOException e) {
            return null;
        }
    }

    private static String readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
        return baos.toString("UTF-8");
    }
}
