package redacted.client.gui.menu;

import net.minecraft.client.gui.GuiScreen;
import redacted.client.font.Fonts;
import redacted.client.util.gui.GuiText;
import redacted.client.util.render.MenuBackground;

// brief branded intro shown right before the main menu. real asset load already
// finished synchronously by now, so this is a fixed ~2.5s animated hand-off, not a progress bar.
public class GuiLoadingScreen extends GuiScreen {

    private static final long DURATION = 2500L;

    private long start;
    private int dotFrame;
    private long lastDot;

    @Override
    public void initGui() {
        start = System.currentTimeMillis();
        dotFrame = 1;
        lastDot = start;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        MenuBackground.render(width, height);

        final long now = System.currentTimeMillis();
        if (now - lastDot > 500) {
            dotFrame = (dotFrame % 3) + 1;
            lastDot = now;
        }
        final String text = "Loading Redacted" + "...".substring(0, dotFrame);

        GuiText.prepareTextRender();
        try {
            Fonts.TITAN_ONE.get(40).drawCentered(text, width / 2.0, height * 0.5, 0xFFFFFFFF);
        } catch (final Exception ignored) {}
    }

    @Override
    public void updateScreen() {
        if (System.currentTimeMillis() - start >= DURATION) {
            mc.displayGuiScreen(new GuiCustomMainMenu());
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
