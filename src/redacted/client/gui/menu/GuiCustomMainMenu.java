package redacted.client.gui.menu;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import redacted.client.Client;
import redacted.client.alt.AltManager;
import redacted.client.font.Fonts;
import redacted.client.gui.alt.GuiAltManager;
import redacted.client.gui.clickgui.redacted.RedactedClickGUI;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.render.MenuBackground;
import redacted.client.util.render.RenderUtil;

import java.io.IOException;

public class GuiCustomMainMenu extends GuiScreen {

    private static final ResourceLocation LANG_ICON = new ResourceLocation("minecraft", "redacted/menu/LanguagesIcon.png");

    private final Animation openAnim = new Animation(Easing.EASE_OUT_EXPO, 250);
    // window resizes re-run initGui on the SAME instance -- only play the zoom once,
    // otherwise every resize replays the center-in animation
    private boolean animatedIn;

    @Override
    public void initGui() {
        if (this.animatedIn) {
            openAnim.setValue(1);
        } else {
            openAnim.setValue(0);
            openAnim.reset();
            this.animatedIn = true;
        }
        buttonList.clear();

        // last-alt auto-login: reload the account used in the previous session
        // (cracked instantly, Microsoft via refresh token, random fallback on failure)
        AltManager.tryAutoLoginFromLast(mc);

        final int j = height / 4 + 40;
        buttonList.add(new MainButton(1, width / 2 - 100, j, 200, 20, I18n.format("menu.singleplayer")));
        buttonList.add(new MainButton(2, width / 2 - 100, j + 24, 200, 20, I18n.format("menu.multiplayer")));
        buttonList.add(new MainButton(20, width / 2 - 100, j + 48, 200, 20, "Alt Manager"));
        buttonList.add(new MainButton(22, width / 2 - 100, j + 72, 200, 20, "Replays"));

        final int by = j + 96;
        buttonList.add(new MainButton(0, width / 2 - 100, by, 98, 20, I18n.format("menu.options")));
        buttonList.add(new MainButton(4, width / 2 + 2, by, 98, 20, I18n.format("menu.quit")));
        buttonList.add(new LangButton(width / 2 - 124, by));
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        MenuBackground.render(width, height);

        openAnim.run(1.0);
        final double scale = openAnim.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2.0, height / 2.0, 0);
        GlStateManager.scale(scale, scale, 1.0);
        GlStateManager.translate(-width / 2.0, -height / 2.0, 0);

        try {
            Fonts.TITAN_ONE.get(56).drawCentered("Redacted Client", width / 2.0, height * 0.08, 0xFFFFFFFF);
        } catch (final Exception ignored) {}

        for (final Object o : buttonList) {
            if (o instanceof MainButton) {
                ((MainButton) o).drawCustom(mc, mouseX, mouseY);
            } else if (o instanceof LangButton) {
                ((LangButton) o).draw(mc, mouseX, mouseY);
            }
        }

        try {
            Fonts.TITAN_ONE.get(14).draw("Minecraft 1.8.9", 4, height - 14, 0xFF666666);
            Fonts.TITAN_ONE.get(12).drawRight(Client.VERSION, width - 4, height - 12, 0x55666666);
        } catch (final Exception e) {
            drawString(fontRendererObj, "Minecraft 1.8.9", 4, height - 10, 0xFF666666);
        }

        GlStateManager.popMatrix();
    }

    @Override
    protected void actionPerformed(final net.minecraft.client.gui.GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case 1:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 2:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 4:
                mc.shutdown();
                break;
            case 5:
                mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
                break;
            case 20:
                mc.displayGuiScreen(new GuiAltManager(this));
                break;
            case 21:
                onGuiClosed();
                mc.displayGuiScreen(new RedactedClickGUI());
                break;
            case 22:
                if (com.replaymod.replay.ReplayModReplay.instance != null) {
                    new com.replaymod.replay.gui.screen.GuiReplayViewer(com.replaymod.replay.ReplayModReplay.instance).display();
                }
                break;
        }
    }

    @Override
    protected void keyTyped(final char c, final int key) throws IOException {
        if (key == 1) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) mc.setIngameFocus();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private class MainButton extends net.minecraft.client.gui.GuiButton {
        double hover;

        MainButton(final int id, final int x, final int y, final int w, final int h, final String text) {
            super(id, x, y, w, h, text);
        }

        void drawCustom(final net.minecraft.client.Minecraft mc, final int mx, final int my) {
            hovered = mx >= xPosition && my >= yPosition
                    && mx < xPosition + width && my < yPosition + height;
            hover += (hovered ? 1.0 : 0.0 - hover) * 0.3;
            if (Math.abs(hover) < 0.001) hover = 0.0;

            final int fill = hovered ? 0x90707070 : 0x60505050;
            RenderUtil.drawRect(xPosition, yPosition, width, height, fill);

            try {
                final redacted.client.util.font.Font f = Fonts.TITAN_ONE.get(14);
                final float fh = f.height();
                final double ty = yPosition + (height - fh) / 2.0 + 2;
                final int tc = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
                f.drawCentered(displayString, xPosition + width / 2.0, ty, tc);
            } catch (final Exception e) {
                drawCenteredString(fontRendererObj, displayString,
                        xPosition + width / 2, yPosition + (height - 8) / 2,
                        hovered ? 0xFFFFFF : 0xCCCCCC);
            }
        }
    }

    private class LangButton extends net.minecraft.client.gui.GuiButton {
        LangButton(final int x, final int y) {
            super(5, x, y, 20, 20, "");
        }

        void draw(final net.minecraft.client.Minecraft mc, final int mx, final int my) {
            hovered = mx >= xPosition && my >= yPosition
                    && mx < xPosition + width && my < yPosition + height;

            RenderUtil.drawRect(xPosition, yPosition, width, height, hovered ? 0x90707070 : 0x60505050);

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glColor4f(0.65F, 0.65F, 0.65F, 1.0F);
            mc.getTextureManager().bindTexture(LANG_ICON);
            drawModalRectWithCustomSizedTexture(xPosition + 2, yPosition + 2, 0, 0, 16, 16, 16, 16);
        }
    }
}
