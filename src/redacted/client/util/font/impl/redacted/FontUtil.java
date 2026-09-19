package redacted.client.util.font.impl.redacted;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FontUtil {

    private static IResourceManager RESOURCE_MANAGER;

    // parsed base font (size 1) per resource path: Font.createFont re-reads and
    // re-parses the whole TTF on every call, and every Fonts.size() miss used to
    // pay that cost again; deriveFont(size) on the cached base is cheap
    private static final Map<String, Font> PARSED_FONTS = new ConcurrentHashMap<String, Font>();

    private static IResourceManager getResourceManager() {
        if (RESOURCE_MANAGER == null) {
            RESOURCE_MANAGER = Minecraft.getMinecraft().getResourceManager();
        }
        return RESOURCE_MANAGER;
    }

    public static Font getResource(final String resource, final int size) {
        try {
            IResourceManager mgr = getResourceManager();
            if (mgr == null) return null;
            Font base = PARSED_FONTS.get(resource);
            if (base == null) {
                base = Font.createFont(Font.TRUETYPE_FONT, mgr.getResource(new ResourceLocation(resource)).getInputStream());
                PARSED_FONTS.put(resource, base);
            }
            return base.deriveFont((float) size);
        } catch (final FontFormatException | IOException ignored) {
            return null;
        }
    }
}
