package redacted.client.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.event.impl.Render2DEvent;
import redacted.client.font.Fonts;
import redacted.client.font.MinecraftFontWrapper;
import redacted.client.font.Weight;
import redacted.client.gui.edit.GuiEditHud;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.font.Font;
import redacted.client.util.gui.Draggable;
import redacted.client.util.render.RenderUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class HUD extends Module {
    // Global
    private final ModeValue font = new ModeValue("Font", this, "SF-Pro-Rounded", "SF-Pro-Rounded", "Titan One", "Minecraft").describe("Font for every HUD element");
    private final BooleanValue background = new BooleanValue("Background", this, true).describe("Dark backdrop behind each element");

    // ArrayList
    private final BooleanValue arrayList = new BooleanValue("ArrayList", this, true).describe("Show the enabled-module list");
    private final ModeValue arrayFont = new ModeValue("L Font", this, arrayList, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for ArrayList (Default = global HUD font)");
    private final ModeValue mode = new ModeValue("Mode", this, arrayList, true, "Classic", "Classic", "Rainbow", "ChromaRainbow").describe("ArrayList color: white, rainbow, or chroma");
    private final ColorValue classicColor = new ColorValue("Custom Color", this, 0xFFFFFFFF, mode, "Classic").describe("Text color in Classic mode");
    private Draggable listDraggable;

    // Coords
    private final BooleanValue coordsDisplay = new BooleanValue("Coords Display", this, false).describe("Show your X/Y/Z position");
    private final ModeValue coordsFont = new ModeValue("C Font", this, coordsDisplay, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for Coords display (Default = global HUD font)");
    private final BooleanValue showBiome = new BooleanValue("Biome", this, true, coordsDisplay, true).describe("Show the current biome name");
    private final BooleanValue showDirection = new BooleanValue("Direction", this, true, coordsDisplay, true).describe("Show the facing direction and yaw");
    private Draggable coordsDraggable;
    private String biomeCache = "";
    private int lastBiomeX = Integer.MIN_VALUE, lastBiomeZ = Integer.MIN_VALUE;

    // Reach
    private final BooleanValue reachDisplay = new BooleanValue("Reach Display", this, false).describe("Show your last hit's reach distance and session max");
    private final ModeValue reachFont = new ModeValue("R Font", this, reachDisplay, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for Reach display (Default = global HUD font)");
    private Draggable reachDraggable;
    private double lastReach;
    private double maxReach;
    private long hitTime;
    private static final DecimalFormat REACH_FMT = new DecimalFormat("0.##");
    private static final long REACH_TIMEOUT = 2000;

    // FPS
    private final BooleanValue fpsDisplay = new BooleanValue("FPS Display", this, false).describe("Show your frames per second");
    private final ModeValue fpsFont = new ModeValue("F Font", this, fpsDisplay, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for FPS display (Default = global HUD font)");
    private Draggable fpsDraggable;

    // Watermark
    private final BooleanValue watermark = new BooleanValue("Watermark", this, true).describe("Show the client name and version");
    private final ModeValue watermarkFont = new ModeValue("W Font", this, watermark, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for watermark (Default = global HUD font)");
    private Draggable watermarkDraggable;

    // Boss bar
    private final BooleanValue bossBar = new BooleanValue("Boss Bar", this, false).describe("Custom boss health bar");
    private final ModeValue bossStyle = new ModeValue("Boss Style", this, bossBar, true, "Modern", "Modern", "Vanilla").describe("Rounded modern bar or vanilla texture");
    private final ModeValue bossFont = new ModeValue("B Font", this, bossBar, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for Boss Bar (Default = global HUD font)");
    private final List<String> arrayListEntries = new ArrayList<>();
    // scratch for the cached-width arraylist sort (render-thread only)
    private int[] arrayListWidths = new int[16];
    private Integer[] arrayListOrder = new Integer[16];
    // string caches: rebuilt only when the value they render changes
    private String coordsCache;
    private int lastCoordsX = Integer.MIN_VALUE, lastCoordsY, lastCoordsZ;
    private String biomeLineCache;
    private String lastBiomeCache;
    private String facingCache;
    private int facingCacheYaw = Integer.MIN_VALUE;
    private String reachCache;
    private double lastReachCache = Double.NaN, lastMaxReachCache = Double.NaN;
    private String fpsCache;
    private int lastFpsCache = Integer.MIN_VALUE;
    private final List<ItemStack> armorItemList = new ArrayList<>();
    private Draggable bossDraggable;
    private static final int BAR_TEXTURE_W = 256;
    private static final int BAR_TEXTURE_H = 256;
    private static final int BG_V = 74;
    private static final int FILL_V = 79;

    // Armor
    private final BooleanValue armorStatus = new BooleanValue("Armor Status", this, false).describe("Show your armor pieces, held item, and durability");
    private final ModeValue armorFont = new ModeValue("A Font", this, armorStatus, true, "Default", "SF-Pro-Rounded", "Titan One", "Minecraft")
            .describe("Font for Armor Status (Default = global HUD font)");
    private final ModeValue armorOrientation = new ModeValue("Armor Orientation", this, armorStatus, true, "Horizontal", "Horizontal", "Vertical").describe("Lay the armor icons in a row or column");
    private final ModeValue armorInfo = new ModeValue("Armor Info", this, armorStatus, true, "Percent", "Off", "Percent", "Value").describe("Durability as a percent, remaining/max value, or hidden");
    private final BooleanValue armorEnchants = new BooleanValue("Armor Enchantments", this, false, armorStatus, true).describe("Show key enchant levels (prot/sharp/power)");
    private Draggable armorDraggable;

    public boolean isBossBarVisible() {
        return bossBar.getValue();
    }

    // the ArrayList draggable is named "HUD" but its gating toggle is the ArrayList setting
    @Override
    public BooleanValue getComponentToggle(final String draggableName) {
        if (draggableName != null && draggableName.equalsIgnoreCase("HUD")) return arrayList;
        return super.getComponentToggle(draggableName);
    }

    public HUD() {
        super("HUD", "Displays client overlay information", Keyboard.KEY_NONE, Category.RENDER);
        setCheckboxBooleans(true);
    }

    private Draggable lazyDraggable(String name, float x, float y, float w, float h) {
        Draggable d = null;
        switch (name) {
            case "HUD": if (listDraggable == null) listDraggable = registerDraggable(name, x, y, w, h); d = listDraggable; break;
            case "Coords Display": if (coordsDraggable == null) coordsDraggable = registerDraggable(name, x, y, w, h); d = coordsDraggable; break;
            case "Reach Display": if (reachDraggable == null) reachDraggable = registerDraggable(name, x, y, w, h); d = reachDraggable; break;
            case "FPS Display": if (fpsDraggable == null) fpsDraggable = registerDraggable(name, x, y, w, h); d = fpsDraggable; break;
            case "Watermark": if (watermarkDraggable == null) watermarkDraggable = registerDraggable(name, x, y, w, h); d = watermarkDraggable; break;
            case "Boss Bar": if (bossDraggable == null) bossDraggable = registerDraggable(name, x, y, w, h); d = bossDraggable; break;
            case "Armor Status": if (armorDraggable == null) armorDraggable = registerDraggable(name, x, y, w, h); d = armorDraggable; break;
        }
        if (d == null) d = new Draggable(this, name, x, y, w, h);
        return d;
    }

    // eager registration so every edit-HUD dummy exists from startup (see Client.start); the
    // defaults mirror the call sites in the draw methods so the dummy matches the real element.
    public void registerAllDraggables() {
        lazyDraggable("HUD", -1, -1, 100, 100);
        lazyDraggable("Coords Display", 5, 5, 90, 40);
        lazyDraggable("Reach Display", 5, 120, 70, 12);
        lazyDraggable("FPS Display", 5, 140, 50, 12);
        lazyDraggable("Watermark", 5, 160, 80, 18);
        lazyDraggable("Boss Bar", -1, -1, 182, 12);
        lazyDraggable("Armor Status", 5, 60, 90, 20);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        try {
            if (mc.thePlayer == null) return;

            boolean editMode = mc.currentScreen instanceof GuiEditHud;

            // the edit GUI previews every component regardless of its toggle
            if (arrayList.getValue() || editMode) drawArrayList(event, editMode);
            if (coordsDisplay.getValue() || editMode) drawCoords(event, editMode);
            if (reachDisplay.getValue() || editMode) drawReach(event, editMode);
            if (fpsDisplay.getValue() || editMode) drawFps(event, editMode);
            if (watermark.getValue() || editMode) drawWatermark(event, editMode);
            if (bossBar.getValue() || editMode) drawBossBar(event, editMode);
            if (armorStatus.getValue() || editMode) drawArmor(event, editMode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Font safeFont(ModeValue mode, int size, Weight weight) {
        try {
            return Fonts.getForMode(mode.getValue(), size, weight);
        } catch (Exception e) {
            if (mc.fontRendererObj != null) {
                return new MinecraftFontWrapper(mc.fontRendererObj);
            }
            return null;
        }
    }

    /** Returns the sub-mode font if set, otherwise falls back to the global HUD font. */
    private Font subFont(ModeValue subFontValue, int size, Weight weight) {
        if (subFontValue != null && !"Default".equalsIgnoreCase(subFontValue.getValue())) {
            return safeFont(subFontValue, size, weight);
        }
        return safeFont(font, size, weight);
    }

    // ArrayList

    private void drawArrayList(Render2DEvent event, boolean editMode) {
        float dScale = lazyDraggable("HUD", -1, -1, 100, 100).getScale();
        int fontSize = Fonts.size(18 * dScale);
        Font f = subFont(arrayFont, fontSize, Weight.REGULAR);
        if (f == null) return;
        arrayListEntries.clear();

        for (Module m : Client.getInstance().getModuleManager().getModules()) {
            if (m == this || !m.isEnabled()) continue;
            arrayListEntries.add(m.getName() + m.getArraylistSuffix());
        }

        List<String> entries = arrayListEntries;

        // measure each entry once, then sort an index array by the cached widths.
        // sorting the strings directly re-ran f.width() (a per-char scan) on every
        // comparison; the resulting order is identical (stable sort on the same keys)
        final int n = entries.size();
        if (arrayListWidths.length < n) {
            arrayListWidths = new int[Math.max(16, n * 2)];
            arrayListOrder = new Integer[Math.max(16, n * 2)];
        }
        for (int i = 0; i < n; i++) {
            arrayListWidths[i] = f.width(entries.get(i));
            arrayListOrder[i] = i;
        }
        java.util.Arrays.sort(arrayListOrder, 0, n, (a, b) -> Integer.compare(arrayListWidths[b], arrayListWidths[a]));

        int margin = 4;
        int lineH = (int) f.height() + 1;
        int totalH = n == 0 ? 0 : (n * lineH) - 1;
        int mw = 0;
        for (int i = 0; i < n; i++) mw = Math.max(mw, arrayListWidths[i]);

        Draggable d = lazyDraggable("HUD", -1, -1, 100, 100);
        // mw/totalH come from the dScale-sized font, so divide back to unscaled base px
        d.setBaseWidth((mw + margin * 2) / dScale);
        d.setBaseHeight((totalH + margin * 2) / dScale);

        float bx = d.getX(), by = d.getY();
        if (bx < 0) { bx = event.getScaledResolution().getScaledWidth() - d.getWidth() - margin; d.setX(bx); }
        if (by < 0) { by = margin; d.setY(by); }
        

        float y = by + margin;
        for (int k = 0; k < n; k++) {
            String entry = entries.get(arrayListOrder[k]);
            float x = bx + margin;
            int color = classicColor.getValue();
            int bar = 0;

            if (mode.getValue().equals("Rainbow")) {
                color = xatzRainbow((int) y);
                bar = color;
            } else if (mode.getValue().equals("ChromaRainbow")) {
                color = chromaRainbow((int) x, (int) y);
                bar = color;
            }

            if (bar != 0) {
                int l = (int) x - 2, t = (int) y - 1, r = (int) x, b2 = (int) y + lineH - 1;
                if (l < r && t < b2) Gui.drawRect(l, t, r, b2, bar);
            }

            f.drawWithShadow(entry, x + 2, y, color);
            y += lineH;
        }
    }

    // Coords

    private void drawCoords(Render2DEvent event, boolean editMode) {
        if (mc.theWorld == null) return;

        ScaledResolution sr = event.getScaledResolution();
        Draggable d = lazyDraggable("Coords Display", 5, 5, 90, 40);
        float sc = d.getScale();
        Font f = subFont(coordsFont, 14, Weight.REGULAR);
        if (f == null) return;

        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);

        if (showBiome.getValue() && (x != lastBiomeX || z != lastBiomeZ)) {
            BlockPos pos = new BlockPos(mc.thePlayer);
            biomeCache = mc.theWorld.getChunkFromBlockCoords(pos)
                    .getBiome(pos, mc.theWorld.getWorldChunkManager()).biomeName;
            lastBiomeX = x;
            lastBiomeZ = z;
        }

        String dir = getFacing();
        String coords = coordsCache != null && lastCoordsX == x && lastCoordsY == y && lastCoordsZ == z
                ? coordsCache : (coordsCache = "X: " + x + " Y: " + y + " Z: " + z);
        lastCoordsX = x; lastCoordsY = y; lastCoordsZ = z;
        String biomeLine = showBiome.getValue() && biomeCache == lastBiomeCache
                ? biomeLineCache : (biomeLineCache = "Biome: " + biomeCache);
        lastBiomeCache = biomeCache;
        int mw = f.width(coords);
        int lh = (int) (f.height() + 2);
        int lines = 1;

        if (showDirection.getValue()) { mw = Math.max(mw, f.width(dir)); lines++; }
        if (showBiome.getValue()) { mw = Math.max(mw, f.width(biomeLine)); lines++; }

        int bw = mw + 8;
        int bh = lines * lh + 4;
        d.setBaseWidth(bw);
        d.setBaseHeight(bh);

        float bx = d.getX(), by = d.getY();


        GlStateManager.pushMatrix();
        GlStateManager.translate(bx, by, 0);
        GlStateManager.scale(sc, sc, 1);

        if (background.getValue())
            RenderUtil.drawRoundedRect(0, 0, bw, bh, 3, 0x60000000);

        int cy = 3;
        f.drawWithShadow(coords, 4, cy, 0xFFFFFFFF);
        cy += lh;
        if (showDirection.getValue()) { f.drawWithShadow(dir, 4, cy, 0xFF55FF55); cy += lh; }
        if (showBiome.getValue()) f.drawWithShadow(biomeLine, 4, cy, 0xFFAAAAAA);

        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    private String getFacing() {
        float yaw = mc.thePlayer.rotationYaw % 360;
        if (yaw < 0) yaw += 360;
        int iy = (int) yaw;
        if (facingCache != null && facingCacheYaw == iy) return facingCache;
        facingCacheYaw = iy;
        String d;
        if (yaw < 45 || yaw >= 315) d = "South (-Z)";
        else if (yaw < 135) d = "West (-X)";
        else if (yaw < 225) d = "North (+Z)";
        else d = "East (+X)";
        return facingCache = "Facing: " + d + " [" + iy + "\u00b0]";
    }

    // Reach

    @EventTarget
    public void onLeftClick(LeftClickEvent event) {
        if (!reachDisplay.getValue()) return;
        if (mc.objectMouseOver != null && mc.objectMouseOver.hitVec != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            double dist = mc.objectMouseOver.hitVec.distanceTo(mc.thePlayer.getPositionEyes(1.0F));
            lastReach = dist;
            maxReach = Math.max(maxReach, dist);
            hitTime = System.currentTimeMillis();
        }
    }

    private void drawReach(Render2DEvent event, boolean editMode) {
        ScaledResolution sr = event.getScaledResolution();
        Draggable d = lazyDraggable("Reach Display", 5, 120, 70, 12);
        float sc = d.getScale();
        Font f = subFont(reachFont, 14, Weight.REGULAR);
        if (f == null) return;

        if (System.currentTimeMillis() - hitTime > REACH_TIMEOUT) lastReach = 0;

        String text = lastReachCache == lastReach && lastMaxReachCache == maxReach && reachCache != null
                ? reachCache
                : (reachCache = "Reach: " + REACH_FMT.format(lastReach) + " blocks [" + REACH_FMT.format(maxReach) + "]");
        lastReachCache = lastReach;
        lastMaxReachCache = maxReach;

        int bw = f.width(text) + 8;
        int bh = (int) f.height() + 4;
        d.setBaseWidth(bw);
        d.setBaseHeight(bh);

        float bx = d.getX(), by = d.getY();


        GlStateManager.pushMatrix();
        GlStateManager.translate(bx, by, 0);
        GlStateManager.scale(sc, sc, 1);

        if (background.getValue())
            RenderUtil.drawRoundedRect(0, 0, bw, bh, 3, 0x60000000);
        f.drawWithShadow(text, 4, 2, lastReach > 0 ? 0xFF55FF55 : 0xFFAAAAAA);

        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    // FPS

    private void drawFps(Render2DEvent event, boolean editMode) {
        Draggable d = lazyDraggable("FPS Display", 5, 140, 50, 12);
        float sc = d.getScale();
        Font f = subFont(fpsFont, 14, Weight.REGULAR);
        if (f == null) return;

        final int fps = Minecraft.getDebugFPS();
        String text = lastFpsCache == fps && fpsCache != null ? fpsCache : (fpsCache = "FPS: " + fps);
        lastFpsCache = fps;

        int bw = f.width(text) + 8;
        int bh = (int) f.height() + 4;
        d.setBaseWidth(bw);
        d.setBaseHeight(bh);

        GlStateManager.pushMatrix();
        GlStateManager.translate(d.getX(), d.getY(), 0);
        GlStateManager.scale(sc, sc, 1);

        if (background.getValue())
            RenderUtil.drawRoundedRect(0, 0, bw, bh, 3, 0x60000000);
        f.drawWithShadow(text, 4, 2, 0xFFFFFFFF);

        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    // Watermark

    private void drawWatermark(Render2DEvent event, boolean editMode) {
        Draggable d = lazyDraggable("Watermark", 5, 160, 80, 18);
        float sc = d.getScale();
        Font nameF = subFont(watermarkFont, 24, Weight.REGULAR);
        Font verF = subFont(watermarkFont, 14, Weight.REGULAR);
        if (nameF == null || verF == null) return;

        int nw = nameF.width("Redacted");
        int bw = nw + 2 + verF.width("b0.0.1") + 8;
        int bh = (int) nameF.height() + 4;
        d.setBaseWidth(bw);
        d.setBaseHeight(bh);

        GlStateManager.pushMatrix();
        GlStateManager.translate(d.getX(), d.getY(), 0);
        GlStateManager.scale(sc, sc, 1);

        if (background.getValue())
            RenderUtil.drawRoundedRect(0, 0, bw, bh, 3, 0x60000000);
        nameF.drawWithShadow("Redacted", 4, 2, 0xFFFFFFFF);
        verF.drawWithShadow("b0.0.1", 4 + nw + 2, 2 + nameF.height() - verF.height(), 0xFFAAAAAA);

        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    // Boss bar

    private void drawBossBar(Render2DEvent event, boolean editMode) {
        if (mc.theWorld == null) return;
        if (!editMode && (BossStatus.bossName == null || BossStatus.statusBarTime <= 0)) return;

        ScaledResolution sr = event.getScaledResolution();
        String name = editMode ? "Wither King" : BossStatus.bossName;
        float health = editMode ? 0.6f : BossStatus.healthScale;
        // 182x5 vanilla dims; the draggable scale knob resizes
        float w = 182f, h = 5f;
        Font f = subFont(bossFont, 14, Weight.REGULAR);
        if (f == null) return;
        float th = f.height() + 4;

        Draggable d = lazyDraggable("Boss Bar", -1, -1, 182, 12);
        d.setBaseWidth(w);
        d.setBaseHeight(h + th);

        float bx = d.getX(), by = d.getY();
        float sc = d.getScale();

        if (bx < 0 || by < 0) {
            bx = sr.getScaledWidth() / 2f - w / 2f;
            by = 12;
            d.setX(bx);
            d.setY(by);
        }



        GlStateManager.pushMatrix();
        GlStateManager.translate(bx, by, 0);
        GlStateManager.scale(sc, sc, 1);

        if (bossStyle.getValue().equals("Vanilla"))
            drawVanillaBar(w, h, health, name, f);
        else
            drawModernBar(w, h, health, name, f);

        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    private void drawVanillaBar(float w, float h, float health, String name, Font font) {
        if (background.getValue())
            RenderUtil.drawRoundedRect(-2, -6, w + 4, h + 4 + font.height() + 8, 4, 0x44000000);

        mc.getTextureManager().bindTexture(Gui.icons);
        GlStateManager.color(1, 1, 1, 1);

        int barW = (int) w, barH = (int) h;
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, BG_V, barW, barH, BAR_TEXTURE_W, BAR_TEXTURE_H);
        int hw = (int) (health * (barW + 1));
        if (hw > 0)
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, FILL_V, hw, barH, BAR_TEXTURE_W, BAR_TEXTURE_H);

        int tw = mc.fontRendererObj.getStringWidth(name);
        mc.fontRendererObj.drawStringWithShadow(name, barW / 2f - tw / 2f, barH + 2, 0xFFFFFF);
    }

    private void drawModernBar(float w, float h, float health, String name, Font font) {
        if (background.getValue()) {
            float bgH = h + font.height() + 6;
            RenderUtil.drawRoundedRect(-2, -2, w + 4, bgH + 4, 4, 0x44000000);
        }

        RenderUtil.drawRoundedRect(0, 0, w, h, h / 2f, 0x88000000);
        float fill = w * Math.max(0, Math.min(1, health));
        int c = 0xFF55FF55;
        if (health < 0.6) c = 0xFFFFFF55;
        if (health < 0.25) c = 0xFFFF5555;
        RenderUtil.drawRoundedRect(1, 1, fill - 2, h - 2, (h - 2) / 2f, c);

        font.drawWithShadow(name, (int) (w / 2f - font.width(name) / 2f), (int) (h + 4), 0xFFFFFFFF);
    }

    // Armor

    private void drawArmor(Render2DEvent event, boolean editMode) {
        ScaledResolution sr = event.getScaledResolution();
        Draggable d = lazyDraggable("Armor Status", 5, 60, 90, 20);
        float sc = d.getScale();

        Font f = subFont(armorFont, 11, Weight.REGULAR);
        if (f == null) return;

        armorItemList.clear();
        if (mc.thePlayer.getHeldItem() != null)
            armorItemList.add(mc.thePlayer.getHeldItem());
        for (int i = 3; i >= 0; i--) {
            ItemStack stack = mc.thePlayer.getCurrentArmor(i);
            if (stack != null) armorItemList.add(stack);
        }
        if (armorItemList.isEmpty()) {
            if (!editMode) return;
            // edit-GUI sample kit so the element has real content to place and size
            armorItemList.add(new ItemStack(Items.diamond_sword));
            armorItemList.add(new ItemStack(Items.diamond_helmet));
            armorItemList.add(new ItemStack(Items.diamond_chestplate));
            armorItemList.add(new ItemStack(Items.diamond_leggings));
            armorItemList.add(new ItemStack(Items.diamond_boots));
        }

        List<ItemStack> items = armorItemList;

        boolean horiz = armorOrientation.getValue().equals("Horizontal");
        int slot = 18, pad = 3;
        int exW = 0, exH = 0;

        if (!armorInfo.getValue().equals("Off")) {
            if (horiz) exH = 10; else exW = 24;
        }
        if (armorEnchants.getValue()) {
            if (horiz) exH += 10; else exW = Math.max(exW, 24);
        }

        int tw = horiz ? items.size() * (slot + pad) + pad + exW : slot + pad * 2 + exW;
        int th = horiz ? slot + pad * 2 + exH : items.size() * (slot + pad) + pad + exH;

        d.setBaseWidth(tw);
        d.setBaseHeight(th);

        float bx = d.getX(), by = d.getY();


        GlStateManager.pushMatrix();
        GlStateManager.translate(bx, by, 0);
        GlStateManager.scale(sc, sc, 1);

        if (background.getValue())
            RenderUtil.drawRoundedRect(0, 0, tw, th, 3, 0x60000000);

        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().zLevel = -150;

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int ix = horiz ? pad + i * (slot + pad) : pad;
            int iy = horiz ? pad : pad + i * (slot + pad);
            int nx = horiz ? ix : ix + slot + 2;
            int ny = horiz ? iy + slot + 1 : iy + 4;

            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, ix, iy);
            mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, ix, iy);

            if (!armorInfo.getValue().equals("Off")) {
                f.drawWithShadow(getDamageString(stack), nx, ny, getDamageColor(stack));
                ny += 9;
            }

            if (armorEnchants.getValue()) {
                String ench = getEnchantTag(stack);
                if (ench != null) f.drawWithShadow(ench, nx, ny, 0xFFAAFF55);
            }
        }

        mc.getRenderItem().zLevel = 0;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    private String getDamageString(ItemStack stack) {
        if (stack.getMaxDamage() <= 0) return "";
        int dmg = stack.getItemDamage(), max = stack.getMaxDamage();
        if (armorInfo.getValue().equals("Percent"))
            return (100 - (int) ((double) dmg / max * 100)) + "%";
        return (max - dmg) + "/" + max;
    }

    private int getDamageColor(ItemStack stack) {
        if (stack.getMaxDamage() <= 0) return 0xFFFFFFFF;
        double pct = 1.0 - (double) stack.getItemDamage() / stack.getMaxDamage();
        if (pct > 0.8) return 0xFF55FF55;
        if (pct > 0.6) return 0xFFFFFF55;
        if (pct > 0.4) return 0xFFFFAA55;
        if (pct > 0.2) return 0xFFFF5555;
        return 0xFFFF0000;
    }

    private String getEnchantTag(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        if (stack.getItem() instanceof ItemSword)
            return "S" + EnchantmentHelper.getEnchantmentLevel(16, stack);
        if (stack.getItem() instanceof ItemBow)
            return "P" + EnchantmentHelper.getEnchantmentLevel(48, stack) + " F" + EnchantmentHelper.getEnchantmentLevel(50, stack);
        if (stack.getItem() instanceof ItemArmor)
            return "P" + EnchantmentHelper.getEnchantmentLevel(0, stack);
        return null;
    }

    // Rainbow helpers

    private static int xatzRainbow(int y) {
        return Color.HSBtoRGB((System.currentTimeMillis() + y * -15L) % 3200L / 3200f, 0.6f, 1.0f);
    }

    private static int chromaRainbow(int x, int y) {
        return Color.HSBtoRGB(((System.currentTimeMillis() - (x + y) * 10L) % 2000L) / 2000f, 0.8f, 0.8f);
    }
}
