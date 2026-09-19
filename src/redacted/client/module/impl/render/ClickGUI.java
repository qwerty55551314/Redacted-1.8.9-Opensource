package redacted.client.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.font.Fonts;
import redacted.client.font.ZoomAwareFont;
import redacted.client.font.Weight;
import redacted.client.gui.clickgui.dropdown.DropdownClickGUI;
import redacted.client.util.font.Font;
import redacted.client.gui.clickgui.exhibition.ExhibitionClickGUI;
import redacted.client.gui.clickgui.redacted.RedactedClickGUI;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ColorValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
public class ClickGUI extends Module {

    // every theme lays out against a 540px-tall reference, then scales to the real screen
    private static final double REFERENCE_HEIGHT = 540.0;

    private final ModeValue mode = new ModeValue("Mode", this, "Redactedclient", "Redactedclient", "Dropdown", "Exhibition").describe("Which click GUI theme opens");
    private final NumberValue scale = new NumberValue("Scale", this, 1.0, 0.5, 2.0, 0.1).describe("Overall GUI size multiplier");
    private final ModeValue guiFont = new ModeValue("GUI Font", this, "SF-Pro-Rounded", "SF-Pro-Rounded", "Titan One", "Minecraft").describe("Font used across the GUI panels");
    private final ModeValue sidebarFont = new ModeValue("Sidebar Font", this, "SF-Pro-Rounded", "SF-Pro-Rounded", "Titan One", "Minecraft").describe("Font used for the category sidebar");
    private final BooleanValue rounded = new BooleanValue("Rounded", this, true).describe("Rounded panel corners");
    private final ColorValue color = new ColorValue("Color", this, 0xFF8888FF).describe("GUI accent color");
    private final BooleanValue rememberSearch = new BooleanValue("Remember Search", this, false).describe("Save and restore last search when the Redacted GUI reopens");

    private boolean swallowOpenFrame;

    public ClickGUI() {
        super("ClickGUI", "Opens the click GUI", Keyboard.KEY_RSHIFT, Category.RENDER, true);
    }

    @Override
    public void onEnable() {
        openClickGui();
        setEnabled(false);
    }

    private void openClickGui() {
        if (mc.currentScreen != null) return;
        swallowOpenFrame = true;

        switch (mode.getValue()) {
        case "Dropdown":
            mc.displayGuiScreen(new DropdownClickGUI());
            break;
        case "Exhibition":
            mc.displayGuiScreen(ExhibitionClickGUI.instance);
            break;
        default:
            mc.displayGuiScreen(new RedactedClickGUI());
            break;
        }
    }

    public ColorValue getColorValue() {
        return color;
    }

    public boolean isRounded() {
        return rounded.getValue();
    }

    public double getScale() {
        return scale.getValue();
    }

    // shared by all three themes: screen-fit factor times the user's scale setting
    public static double guiScale() {
        ClickGUI module = Client.getInstance().getModuleManager().getModule(ClickGUI.class);
        double userScale = module != null ? module.getScale() : 1.0;
        double screenHeight = new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight();
        return (screenHeight / REFERENCE_HEIGHT) * userScale;
    }

    public Font getSelectedFont(int size, Weight weight) {
        // zoom-aware so the redacted clickgui's Scale slider re-rasterizes text instead of
        // letting the matrix resample it; in every other GUI ZoomContext is inactive and
        // this delegates 1:1
        return new ZoomAwareFont(size, weight, (s, w) -> Fonts.getForMode(guiFont.getValue(), s, w));
    }

    public Font getSidebarFont(int size, Weight weight) {
        return new ZoomAwareFont(size, weight, (s, w) -> Fonts.getForMode(sidebarFont.getValue(), s, w));
    }

    public boolean shouldCloseOn(final int keyCode) {
        if (keyCode != getKey()) return false;
        if (swallowOpenFrame) {
            swallowOpenFrame = false;
            return false;
        }
        return true;
    }

    public boolean isRememberSearch() { return rememberSearch.getValue(); }
}
