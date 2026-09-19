package redacted.client.font;

import net.minecraft.client.Minecraft;
import redacted.client.util.font.Font;
import redacted.client.util.font.impl.redacted.FontRenderer;
import redacted.client.util.font.impl.redacted.FontUtil;

import java.util.HashMap;
import java.util.function.Supplier;

public enum Fonts {
    MAIN("SF-Pro-Rounded-%s", "otf"),
    MINECRAFT("Minecraft", () -> new MinecraftFontWrapper(Minecraft.getMinecraft().fontRendererObj)),
    EXHIBITION("exhibition", "ttf"),
    TITAN_ONE("TitanOne-Regular", "ttf"),
    SUPERHERO("Superherofx", "ttf"),
    DISPLAY("Display", "ttf"),
    SNIPA_ICONS("Snipeware-Regular", "ttf"),
    EXHIBITION_ICONS("exhibition_icons", "ttf"),
    ICONS_1("Icon-1", "ttf"),
    ICONS_3("Icon-3", "ttf");

    Fonts(String name, String extension) {
        this.name = name;
        this.extention = extension;
    }

    Fonts(String name, Supplier<Font> get) {
        this.name = name;
        this.extention = "";
        this.get = get;
    }

    Supplier<Font> get;
    Font font;
    String name;
    final String extention;
    private final HashMap<Integer, FontRenderer> sizes = new HashMap<>();

    public HashMap<Integer, FontRenderer> getSizes() {
        return sizes;
    }

    public Font get(int size) {
        return get(size, Weight.NONE);
    }

    public Font get() {
        if (get == null) return null;
        return get(0, Weight.NONE);
    }

    public Font get(int size, Weight weight) {
        if (get != null) {
            if (font == null) font = get.get();
            return font;
        }

        int key = size * 1000 + weight.getNum();

        if (!sizes.containsKey(key)) {
            java.awt.Font font = null;
            String location = "unknown";

            for (String alias : weight.getAliases()) {
                location = "redacted/font/" + String.format(name, alias) + "." + extention;
                font = FontUtil.getResource(location, size);
                if (font != null) break;
            }

            if (font != null) {
                sizes.put(key, new FontRenderer(font, true, true, false));
            } else {
                throw new RuntimeException("Unknown Font " + location);
            }
        }

        return sizes.get(key);
    }

    // scaled px -> font size, floored at 8 so it stays legible
    public static int size(double scaled) {
        return Math.max(8, (int) Math.round(scaled));
    }

    // shared font-mode picker for the HUD / GUI modules
    public static Font getForMode(String mode, int size, Weight weight) {
        switch (mode) {
            case "Titan One": return TITAN_ONE.get(size, weight);
            case "Minecraft": return MINECRAFT.get();
            default: return MAIN.get(size, weight);
        }
    }
}
