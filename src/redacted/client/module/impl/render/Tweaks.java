package redacted.client.module.impl.render;

import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

/**
 * PolyPatcher/veloxcaelo port -- QoL + performance toggles wired into vanilla/OptiFine
 * render paths. The module is the master switch; each feature also has its own boolean.
 * All features read via the static getters so vanilla classes stay decoupled.
 */
public class Tweaks extends Module {

    private static Tweaks instance;

    public static Tweaks get() {
        if (instance == null) {
            instance = Client.getInstance().getModuleManager().getModule(Tweaks.class);
        }
        return instance;
    }

    private final BooleanValue numericalEnchants = new BooleanValue("Numerical Enchants", this, false)
        .describe("Show enchantment levels as numbers instead of Roman numerals");
    private final BooleanValue cleanProjectiles = new BooleanValue("Clean Projectiles", this, false)
        .describe("Hide your own projectiles for 2 ticks after firing so they don't block your view");
    private final BooleanValue compactChat = new BooleanValue("Compact Chat", this, false)
        .describe("Stack duplicate chat messages with a count");
    private final BooleanValue chatTimestamps = new BooleanValue("Chat Timestamps", this, false)
        .describe("Prepend [HH:MM:SS] to chat messages");
    private final BooleanValue disableItemFrames = new BooleanValue("Disable Item Frames", this, false)
        .describe("Stop item frames from rendering");
    private final BooleanValue disableMappedItemFrames = new BooleanValue("Disable Map Item Frames", this, false)
        .describe("Stop item frames holding maps from rendering");
    private final BooleanValue disableSkulls = new BooleanValue("Disable Skulls", this, false)
        .describe("Stop skull tile entities from rendering");
    private final BooleanValue disableEndPortals = new BooleanValue("Disable End Portals", this, false)
        .describe("Stop end portal tile entities from rendering");
    private final BooleanValue disableEnchantmentBooks = new BooleanValue("Disable Enchantment Books", this, false)
        .describe("Stop enchantment table books from rendering");
    private final BooleanValue limitTileEntityDistance = new BooleanValue("Limit Tile Entity Distance", this, false)
        .describe("Cap tile entity render distance at 128 blocks");
    private final BooleanValue betterHideGui = new BooleanValue("Better F1 (hide nametags)", this, false)
        .describe("Hide entity nametags when F1 is active");
    private final BooleanValue unfocusedFps = new BooleanValue("Unfocused FPS Cap", this, true)
        .describe("Cap FPS when the window is not focused (amount below)");
    private final NumberValue unfocusedFpsAmount = new NumberValue("Unfocused FPS Amount", this, 60.0, 10.0, 120.0, 1.0)
        .describe("Max FPS when the window is not focused");
    private final BooleanValue keepShadersOnPerspective = new BooleanValue("Keep Shaders on Perspective", this, true)
        .describe("Don't reload the vanilla shader when switching perspective");
    private final BooleanValue smoothScrolling = new BooleanValue("Smooth Scrolling", this, false)
        .describe("Smoothly ease GUI list scrolling instead of jumping");
    private final BooleanValue disableEnchantGlint = new BooleanValue("Disable Enchant Glint", this, false)
        .describe("Stop the armor enchant glint from rendering");
    private final BooleanValue cleanView = new BooleanValue("Clean View", this, false)
        .describe("Hide your own potion effect particles");

    public Tweaks() {
        super("Tweaks", "PolyPatcher/veloxcaelo QoL + performance fixes", Keyboard.KEY_NONE, Category.RENDER);
    }

    public static boolean numericalEnchants() { Tweaks t = get(); return t != null && t.isEnabled() && t.numericalEnchants.getValue(); }
    public static boolean cleanProjectiles() { Tweaks t = get(); return t != null && t.isEnabled() && t.cleanProjectiles.getValue(); }
    public static boolean compactChat() { Tweaks t = get(); return t != null && t.isEnabled() && t.compactChat.getValue(); }
    public static boolean chatTimestamps() { Tweaks t = get(); return t != null && t.isEnabled() && t.chatTimestamps.getValue(); }
    public static boolean disableItemFrames() { Tweaks t = get(); return t != null && t.isEnabled() && t.disableItemFrames.getValue(); }
    public static boolean disableMappedItemFrames() { Tweaks t = get(); return t != null && t.isEnabled() && t.disableMappedItemFrames.getValue(); }
    public static boolean disableSkulls() { Tweaks t = get(); return t != null && t.isEnabled() && t.disableSkulls.getValue(); }
    public static boolean disableEndPortals() { Tweaks t = get(); return t != null && t.isEnabled() && t.disableEndPortals.getValue(); }
    public static boolean disableEnchantmentBooks() { Tweaks t = get(); return t != null && t.isEnabled() && t.disableEnchantmentBooks.getValue(); }
    public static boolean limitTileEntityDistance() { Tweaks t = get(); return t != null && t.isEnabled() && t.limitTileEntityDistance.getValue(); }
    public static boolean betterHideGui() { Tweaks t = get(); return t != null && t.isEnabled() && t.betterHideGui.getValue(); }
    public static boolean unfocusedFps() { Tweaks t = get(); return t != null && t.isEnabled() && t.unfocusedFps.getValue(); }
    public static int unfocusedFpsAmount() {
        Tweaks t = get();
        if (t == null || !t.isEnabled() || !t.unfocusedFps.getValue()) return -1;
        return (int) Math.round(t.unfocusedFpsAmount.getValue());
    }
    public static boolean keepShadersOnPerspective() { Tweaks t = get(); return t != null && t.isEnabled() && t.keepShadersOnPerspective.getValue(); }
    public static boolean smoothScrolling() { Tweaks t = get(); return t != null && t.isEnabled() && t.smoothScrolling.getValue(); }
    public static boolean disableEnchantGlint() { Tweaks t = get(); return t != null && t.isEnabled() && t.disableEnchantGlint.getValue(); }
    public static boolean cleanView() { Tweaks t = get(); return t != null && t.isEnabled() && t.cleanView.getValue(); }
}
