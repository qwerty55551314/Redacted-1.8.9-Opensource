package redacted.client.module.impl.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import org.lwjgl.opengl.GL11;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.Render3DEvent;
import redacted.client.font.Fonts;
import redacted.client.font.Weight;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.font.Font;
import redacted.client.util.render.ColorUtil;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spawns comic-book words ("Pow!", "Zap!") over entities the player attacks and draws
 * them as camera-facing billboards in the 3D world.
 *
 * GL state discipline: this runs from Render3DEvent, and vanilla renders the first-person
 * hand immediately after the event. All state changes therefore go through GlStateManager
 * (so its cache stays in sync) and are explicitly reverted -- no glPushAttrib/glPopAttrib.
 * An attrib pop restores real GL state behind GlStateManager's back, which left a stale
 * current color that the hand renderer's color resets could no longer clear, tinting the arm.
 */
public class SuperheroFX extends Module {

    private static final String[] COMIC_WORDS = {"Boom", "Kaboom", "Pow", "Wam", "Zap", "Bam", "Slap", "Kapow", "Wow"};
    private static final int[] PALETTE_COLORS = {0xff0000, 0x00ff00, 0x0000ff, 0xffff00, 0xff00ff, 0x00ffff, 0xffffff};

    // words farther from the player than this are dropped instead of drawn
    private static final double MAX_RENDER_DISTANCE = 6.0;
    // world-units-to-billboard scale factor for the comic font
    private static final float BILLBOARD_SCALE = 0.06f;

    private final NumberValue wordSize = new NumberValue("Word Size", this, 0.5, 0.1, 2.0, 0.1).describe("Comic word text size");
    private final NumberValue wordsPerHit = new NumberValue("Words Per Hit", this, 1.0, 1.0, 10.0, 1.0).describe("Words spawned per attack");
    private final NumberValue lifetime = new NumberValue("Lifetime", this, 2000.0, 500.0, 10000.0, 200.0).describe("Total time a word stays visible (ms)");
    private final ModeValue wordColorMode = new ModeValue("Word Color", this, "Palette", "Palette", "Random", "Custom").describe("Palette cycles the preset colors, Random picks a random hue, Custom uses your color");
    private final ColorValue customWordColor = new ColorValue("Custom Word Color", this, 0xFFFF0000, wordColorMode, "Custom").describe("Color used when Word Color is Custom");

    private final CopyOnWriteArrayList<ComicWord> activeWords = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    public SuperheroFX() {
        super("SuperheroFX", "Comic style attack particles", Keyboard.KEY_NONE, Category.RENDER);
    }

    @Override
    public void onDisable() {
        activeWords.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isOutboundAttackPacket(event)) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        Entity target = ((C02PacketUseEntity) event.getPacket()).getEntityFromWorld(mc.theWorld);
        if (target == null) return;
        spawnImpactWords(target);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.thePlayer == null || activeWords.isEmpty()) return;

        // the Mirror module fires a second Render3D pass per frame: draw in it, but step physics once
        if (!Mirror.isRendering()) updateActiveWords();
        if (!activeWords.isEmpty()) {
            renderActiveWords();
        }
    }

    // true when the packet is the player's outbound attack swing (safe to cast afterwards)
    private boolean isOutboundAttackPacket(PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return false;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return false;
        return ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK;
    }

    private void spawnImpactWords(Entity target) {
        int wordCount = wordsPerHit.intValue();
        for (int i = 0; i < wordCount; i++) {
            activeWords.add(newImpactWord(target));
        }
    }

    private ComicWord newImpactWord(Entity target) {
        double x = target.posX + (random.nextDouble() - 0.5) * 1.5;
        double y = target.posY + target.height / 2 + (random.nextDouble() - 0.5) * 1.0;
        double z = target.posZ + (random.nextDouble() - 0.5) * 1.5;
        String text = COMIC_WORDS[random.nextInt(COMIC_WORDS.length)];
        return new ComicWord(random, text, pickWordColor(), x, y, z, lifetime.getValue().longValue());
    }

    private int pickWordColor() {
        switch (wordColorMode.getValue()) {
            case "Random": return Color.HSBtoRGB(random.nextFloat(), 0.7f, 1.0f);
            case "Custom": return customWordColor.getRGB();
            default:       return PALETTE_COLORS[random.nextInt(PALETTE_COLORS.length)];
        }
    }

    // advances drift and drops words that expired or left the render range
    private void updateActiveWords() {
        long now = System.currentTimeMillis();
        for (ComicWord word : activeWords) {
            word.advanceMotion();
            if (word.isExpired(now) || isTooFarFromPlayer(word)) {
                activeWords.remove(word);
            }
        }
    }

    private boolean isTooFarFromPlayer(ComicWord word) {
        return mc.thePlayer.getDistance(word.x, word.y, word.z) > MAX_RENDER_DISTANCE;
    }

    private void renderActiveWords() {
        beginOverlayRenderState();
        for (ComicWord word : activeWords) {
            renderWord(word);
        }
        endOverlayRenderState();
    }

    private void beginOverlayRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
    }

    private void endOverlayRenderState() {
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        // white current color LAST: the hand renderer right after us relies on it
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void renderWord(ComicWord word) {
        applyBillboardTransform(word);
        drawComicText(word);
        GlStateManager.popMatrix();
    }

    // positions the text at the word's world spot, rotated to always face the camera
    private void applyBillboardTransform(ComicWord word) {
        float viewerRelativeX = (float) (word.x - mc.getRenderManager().viewerPosX);
        float viewerRelativeY = (float) (word.y - mc.getRenderManager().viewerPosY);
        float viewerRelativeZ = (float) (word.z - mc.getRenderManager().viewerPosZ);

        GlStateManager.pushMatrix();
        GlStateManager.translate(viewerRelativeX, viewerRelativeY, viewerRelativeZ);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1f, 0f, 0f);

        float size = BILLBOARD_SCALE * wordSize.floatValue();
        GlStateManager.scale(-size, -size, size);
    }

    private void drawComicText(ComicWord word) {
        int argb = ColorUtil.withAlpha(word.colorArgb, (int) (word.alphaFraction(System.currentTimeMillis()) * 255));
        try {
            Font superheroFont = Fonts.SUPERHERO.get(40, Weight.REGULAR);
            int textWidth = superheroFont.width(word.text);
            superheroFont.drawWithShadow(word.text, -textWidth / 2.0, -superheroFont.height() / 2.0, argb);
        } catch (Exception missingFont) {
            FontRenderer vanillaFont = mc.fontRendererObj;
            float textWidth = vanillaFont.getStringWidth(word.text);
            vanillaFont.drawStringWithShadow(word.text, -textWidth / 2.0f, -4f, argb);
        }
    }

    // one floating comic word: world position, drift velocity, and lifetime-driven fade
    private static final class ComicWord {
        private final String text;
        private final int colorArgb;
        private final long spawnedAtMillis;
        private final long lifetimeMillis;

        private double x, y, z;
        private double velocityX, velocityY, velocityZ;

        ComicWord(Random random, String text, int colorArgb,
                  double x, double y, double z, long lifetimeMillis) {
            this.text = text;
            // force full base opacity -- fade is applied on top at draw time
            this.colorArgb = 0xFF000000 | (colorArgb & 0x00FFFFFF);
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocityX = (random.nextInt(3) - 1.0) * 0.5;
            this.velocityY = random.nextDouble() * 0.5;
            this.velocityZ = (random.nextInt(3) - 1.0) * 0.5;
            this.spawnedAtMillis = System.currentTimeMillis();
            this.lifetimeMillis = lifetimeMillis;
        }

        boolean isExpired(long nowMillis) {
            return nowMillis >= spawnedAtMillis + lifetimeMillis;
        }

        // fully opaque for the first half of the lifetime, then a linear fade to invisible
        // by the end -- frame-rate independent, unlike the old per-frame decay
        float alphaFraction(long nowMillis) {
            long remainingMillis = spawnedAtMillis + lifetimeMillis - nowMillis;
            long halfLifeMillis = lifetimeMillis / 2;
            if (remainingMillis >= halfLifeMillis) return 1.0f;
            return Math.max(0.0f, remainingMillis / (float) halfLifeMillis);
        }

        void advanceMotion() {
            x += velocityX / 10;
            y += velocityY / 10;
            z += velocityZ / 10;
            velocityX -= velocityX / 20;
            velocityY -= velocityY / 20;
            velocityZ -= velocityZ / 20;
        }
    }
}
