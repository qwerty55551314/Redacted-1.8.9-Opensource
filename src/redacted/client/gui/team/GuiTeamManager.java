package redacted.client.gui.team;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import redacted.client.module.impl.combat.Team;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.GuiText;
import redacted.client.util.gui.InputField;
import redacted.client.util.render.RenderUtil;
import redacted.client.value.impl.ColorValue;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class GuiTeamManager extends GuiScreen {

    private static final int PANEL_W = 520;
    private static final int PANEL_H = 380;
    private static final int PAD = 14;
    private static final int PANE_GAP = 10;

    private static final int BG_BASE      = 0xFF0A0A0A;
    private static final int BG_PANE      = 0xFF0F0F0F;
    private static final int BG_ROW       = 0xFF141414;
    private static final int BG_HOVER     = 0xFF1A1A1A;
    private static final int BG_INPUT     = 0xFF111111;
    private static final int BG_BUTTON    = 0xFF222222;
    private static final int BG_BUTTON_HV = 0xFF333333;
    private static final int BORDER_DEF   = 0xFF333333;
    private static final int BORDER_FOCUS = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SEC     = 0xFF888888;
    private static final int TEXT_FAINT   = 0xFF555555;
    private static final int SHADOW_CLR   = 0x30000000;

    private static final double ROW_H = 22;
    private static final double BTN_H = 28;
    private static final double FIELD_H = 30;

    private static final int CP_W = 160;
    private static final int CP_H = 180;

    private static final double SQ = 90;

    private static final int[][] PRESETS = {
        {255, 85, 85}, {85, 255, 85}, {85, 85, 255}, {255, 255, 85},
        {255, 85, 255}, {255, 170, 0}, {200, 200, 200}
    };

    private final Animation openAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    // window resizes re-run initGui -- only play the open zoom on the first init
    private boolean animatedIn;
    private final InputField nameField = new InputField(false, "Add player name...");
    private Team team;

    private double panelLeft, panelTop;
    private double bodyTop, bodyH;
    private double leftPaneX, leftPaneW, rightPaneX, rightPaneW;
    private double inputY, addX, addW;
    private double swatchX, swatchY;
    private double doneX, doneY;

    private double teamScroll, teamScrollTarget;
    private double onlineScroll, onlineScrollTarget;

    private boolean colorPickerOpen;
    private boolean draggingSquare;
    private boolean draggingHue;

    private GuiScreen parentScreen;

    // ColorValue is the single source of truth now, no more local hue/sat/val copy to keep in sync
    private ColorValue colorValue() {
        return this.team != null ? this.team.getColorValue() : null;
    }

    public void setParentScreen(final GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.team = Team.get();
        this.nameField.setMaxStringLength(48);
        if (this.animatedIn) {
            this.openAnimation.setValue(1);
        } else {
            this.openAnimation.setValue(0);
            this.openAnimation.reset();
            this.animatedIn = true;
        }
        this.teamScroll = this.teamScrollTarget = 0;
        this.onlineScroll = this.onlineScrollTarget = 0;
        this.colorPickerOpen = false;
        this.draggingSquare = false;
        this.draggingHue = false;
    }

    private void updateLayout() {
        this.panelLeft = (this.width - PANEL_W) / 2.0;
        this.panelTop = (this.height - PANEL_H) / 2.0;

        this.inputY = this.panelTop + 46;
        this.addW = 52;
        this.addX = this.panelLeft + PANEL_W - PAD - this.addW;
        final double fieldX = this.panelLeft + PAD;
        final double fieldW = this.addX - 8 - fieldX;
        this.nameField.setBounds(fieldX, this.inputY, fieldW, FIELD_H);

        final double paneGap = PANE_GAP;
        final double paneTop = this.inputY + FIELD_H + 14;
        this.leftPaneW = (PANEL_W - PAD * 2 - paneGap) / 2.0;
        this.leftPaneX = this.panelLeft + PAD;
        this.rightPaneW = this.leftPaneW;
        this.rightPaneX = this.panelLeft + PAD + this.leftPaneW + paneGap;

        this.bodyTop = paneTop + 24;
        this.bodyH = (this.panelTop + PANEL_H - 44) - this.bodyTop;

        this.doneX = this.panelLeft + PANEL_W / 2.0 - 50;
        this.doneY = this.panelTop + PANEL_H - 34;

        this.swatchX = this.panelLeft + PANEL_W - PAD - 14;
        this.swatchY = this.panelTop + 15;
    }

    @Override
    public void updateScreen() {
        this.nameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.updateLayout();

        if (this.draggingSquare) handleSquareDrag(mouseX, mouseY);
        if (this.draggingHue) handleHueDrag(mouseX, mouseY);

        // The game keeps rendering behind this screen, so normalize leaked world GL state
        // before drawing the panel. Text draws repeat this reset via GuiText.
        GuiText.prepareTextRender();

        RenderUtil.drawRect(0, 0, this.width, this.height, 0xCC000000);

        this.openAnimation.run(1.0);
        final double scale = this.openAnimation.getValue();

        GlStateManager.pushMatrix();
        final double cx = this.panelLeft + PANEL_W / 2.0;
        final double cy = this.panelTop + PANEL_H / 2.0;
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(scale, scale, 1.0);
        GlStateManager.translate(-cx, -cy, 0);

        if (scale > 0.95) {
            RenderUtil.drawRoundedRect(this.panelLeft - 2, this.panelTop - 2, PANEL_W + 4, PANEL_H + 4, 12, SHADOW_CLR);
        }
        RenderUtil.drawRoundedRect(this.panelLeft, this.panelTop, PANEL_W, PANEL_H, 10, BG_PANE);

        drawText(22, "Team Management", this.panelLeft + PAD, this.panelTop + 14, TEXT_PRIMARY);

        final int teamColor = this.team != null ? Team.getTeamColorARGB() : 0xFF00AAFF;
        RenderUtil.drawRoundedRect(this.swatchX, this.swatchY, 14, 14, 3, teamColor);
        final int swatchBorder = this.colorPickerOpen ? BORDER_FOCUS : BORDER_DEF;
        RenderUtil.drawRoundedOutline(this.swatchX, this.swatchY, 14, 14, 3, 1.0f, swatchBorder);
        drawText(11, "Team Color", this.swatchX - 6 - stringWidth(11, "Team Color"), this.swatchY + 2, TEXT_SEC);

        this.nameField.draw();
        final boolean addHover = GUIUtil.mouseOver(this.addX, this.inputY, this.addW, FIELD_H, mouseX, mouseY);
        RenderUtil.drawRoundedRect(this.addX, this.inputY, this.addW, FIELD_H, 5, addHover ? BG_BUTTON_HV : BG_BUTTON);
        RenderUtil.drawRoundedOutline(this.addX, this.inputY, this.addW, FIELD_H, 5, 1.0f, addHover ? BORDER_FOCUS : BORDER_DEF);
        drawTextCentered(13, "Add", this.addX + this.addW / 2.0, this.inputY + (FIELD_H - fontH(13)) / 2.0, TEXT_PRIMARY);

        if (this.team == null) {
            drawTextCentered(14, "Team module not found", this.panelLeft + PANEL_W / 2.0, this.panelTop + PANEL_H / 2.0, 0xFFFF5555);
            GlStateManager.popMatrix();
            return;
        }

        final List<String> members = this.team.getMembers();
        final boolean hideOnlinePlayers = shouldHideOnlinePlayers();
        final List<String> online = hideOnlinePlayers ? new ArrayList<String>() : onlinePlayers();

        drawText(14, "Team Members", this.leftPaneX, this.bodyTop - 16, TEXT_PRIMARY);
        drawText(11, "click to remove", this.leftPaneX + stringWidth(14, "Team Members") + 6, this.bodyTop - 16, TEXT_FAINT);
        drawText(14, "Online Players", this.rightPaneX, this.bodyTop - 16, TEXT_PRIMARY);
        drawText(11, hideOnlinePlayers ? "hidden in pregame" : "click to add/remove",
                this.rightPaneX + stringWidth(14, "Online Players") + 6, this.bodyTop - 16, TEXT_FAINT);

        final int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            final double delta = wheel > 0 ? 28 : -28;
            if (GUIUtil.mouseOver(this.leftPaneX, this.bodyTop, this.leftPaneW, this.bodyH, mouseX, mouseY)) {
                this.teamScrollTarget += delta;
            } else if (!hideOnlinePlayers && GUIUtil.mouseOver(this.rightPaneX, this.bodyTop, this.rightPaneW, this.bodyH, mouseX, mouseY)) {
                this.onlineScrollTarget += delta;
            }
        }
        this.teamScrollTarget = clampScroll(this.teamScrollTarget, members.size());
        this.onlineScrollTarget = clampScroll(this.onlineScrollTarget, online.size());
        this.teamScroll += (this.teamScrollTarget - this.teamScroll) * 0.25;
        this.onlineScroll += (this.onlineScrollTarget - this.onlineScroll) * 0.25;

        drawList(this.leftPaneX, members, this.teamScroll, mouseX, mouseY);
        drawList(this.rightPaneX, online, this.onlineScroll, mouseX, mouseY,
                hideOnlinePlayers ? "Hidden until game starts" : "(none)");

        final boolean doneHover = GUIUtil.mouseOver(this.doneX, this.doneY, 100, BTN_H, mouseX, mouseY);
        RenderUtil.drawRoundedRect(this.doneX, this.doneY, 100, BTN_H, 5, doneHover ? BG_BUTTON_HV : BG_BUTTON);
        RenderUtil.drawRoundedOutline(this.doneX, this.doneY, 100, BTN_H, 5, 1.0f, doneHover ? BORDER_FOCUS : BORDER_DEF);
        drawTextCentered(14, "Done", this.doneX + 50, this.doneY + (BTN_H - fontH(14)) / 2.0, TEXT_PRIMARY);

        GlStateManager.popMatrix();

        if (this.colorPickerOpen) {
            drawColorPicker(mouseX, mouseY);
        }
    }

    private void drawColorPicker(final int mouseX, final int mouseY) {
        final ColorValue cv = colorValue();
        if (cv == null) return;
        final double hue = cv.getHue();
        final double sat = cv.getSat();
        final double val = cv.getVal();

        final double px = Math.min(this.swatchX - CP_W + 14, this.panelLeft + PANEL_W - CP_W - 8);
        final double py = this.swatchY + 20;

        RenderUtil.drawRoundedRect(px, py, CP_W, CP_H, 6, BG_ROW);
        RenderUtil.drawRoundedOutline(px, py, CP_W, CP_H, 6, 1.0f, BORDER_DEF);

        drawText(12, "Team Color", px + 10, py + 6, TEXT_PRIMARY);

        // centre the square + hue bar unit
        final double barW = 14;
        final double unitW = SQ + 5 + barW;
        final double unitX = px + (CP_W - unitW) / 2.0;
        final double sqX = unitX;
        final double barX = unitX + SQ + 5;
        final double sqY = py + 24;

        // 2D square
        for (int sy = 0; sy < SQ; sy++) {
            for (int sx = 0; sx < SQ; sx++) {
                final float satC = sx / (float) SQ;
                final float valC = 1.0f - sy / (float) SQ;
                final int col = Color.HSBtoRGB((float) hue, satC, valC);
                RenderUtil.drawRect(sqX + sx, sqY + sy, 1, 1, 0xFF000000 | col);
            }
        }
        RenderUtil.drawRoundedOutline(sqX, sqY, SQ, SQ, 2, 1.0f, BORDER_DEF);

        // indicator circle
        final double indX = sqX + sat * SQ;
        final double indY = sqY + (1.0 - val) * SQ;
        final int indCol = val > 0.5 && sat < 0.3 ? 0xFF000000 : 0xFFFFFFFF;
        RenderUtil.drawCircleOutline(indX, indY, 3, 1.5f, indCol);

        // hue bar
        for (int i = 0; i < (int) SQ; i++) {
            final float t = i / (float) SQ;
            final int col = Color.HSBtoRGB(t, 1.0f, 1.0f);
            RenderUtil.drawRect(barX, sqY + i, barW, 1, 0xFF000000 | col);
        }
        RenderUtil.drawRoundedOutline(barX, sqY, barW, SQ, 2, 1.0f, BORDER_DEF);

        // hue thumb
        final double thumbY = sqY + hue * SQ - 2;
        RenderUtil.drawRoundedRect(barX - 1, thumbY, barW + 2, 4, 2, TEXT_PRIMARY);

        // RGB text below square
        final int curColor = Color.HSBtoRGB((float) hue, (float) sat, (float) val);
        final double rgbY = sqY + SQ + 6;
        drawTextCentered(10, String.format("R:%-3d", (curColor >> 16) & 0xFF),
                px + CP_W / 2.0 - 34, rgbY, 0xFFFF5555);
        drawTextCentered(10, String.format("G:%-3d", (curColor >> 8) & 0xFF),
                px + CP_W / 2.0, rgbY, 0xFF55FF55);
        drawTextCentered(10, String.format("B:%-3d", curColor & 0xFF),
                px + CP_W / 2.0 + 34, rgbY, 0xFF5555FF);

        // presets
        final double presetY = rgbY + 12;
        drawText(10, "Presets", px + 10, presetY, TEXT_FAINT);
        final double ps = 16;
        final double pg = 4;
        final double totalPs = PRESETS.length * ps + (PRESETS.length - 1) * pg;
        final double psX = px + (CP_W - totalPs) / 2.0;
        for (int i = 0; i < PRESETS.length; i++) {
            final double ppx = psX + i * (ps + pg);
            final int pCol = 0xFF000000 | (PRESETS[i][0] << 16) | (PRESETS[i][1] << 8) | PRESETS[i][2];
            RenderUtil.drawRoundedRect(ppx, presetY + 12, ps, ps, 3, pCol);
            if (GUIUtil.mouseOver(ppx, presetY + 12, ps, ps, mouseX, mouseY)) {
                RenderUtil.drawRoundedOutline(ppx, presetY + 12, ps, ps, 3, 1.5f, BORDER_FOCUS);
            }
        }
        drawTextCentered(9, "click outside to close", px + CP_W / 2.0, py + CP_H - 12, TEXT_FAINT);
    }

    private void handleSquareDrag(final int mx, final int my) {
        final ColorValue cv = colorValue();
        if (cv == null) return;
        final double px = Math.min(this.swatchX - CP_W + 14, this.panelLeft + PANEL_W - CP_W - 8);
        final double py = this.swatchY + 20;
        final double unitW = SQ + 5 + 14;
        final double unitX = px + (CP_W - unitW) / 2.0;
        final double sqY = py + 24;
        final double sat = Math.max(0, Math.min(1, (mx - unitX) / SQ));
        final double val = Math.max(0, Math.min(1, 1.0 - (my - sqY) / SQ));
        cv.setHSB(cv.getHue(), (float) sat, (float) val);
    }

    private void handleHueDrag(final int mx, final int my) {
        final ColorValue cv = colorValue();
        if (cv == null) return;
        final double px = Math.min(this.swatchX - CP_W + 14, this.panelLeft + PANEL_W - CP_W - 8);
        final double py = this.swatchY + 20;
        final double sqY = py + 24;
        final double hue = Math.max(0, Math.min(1, (my - sqY) / SQ));
        cv.setHSB((float) hue, cv.getSat(), cv.getVal());
    }

    private void drawList(final double px, final List<String> rows, final double scroll,
                          final int mouseX, final int mouseY) {
        drawList(px, rows, scroll, mouseX, mouseY, "(none)");
    }

    private void drawList(final double px, final List<String> rows, final double scroll,
                          final int mouseX, final int mouseY, final String emptyText) {
        RenderUtil.drawRoundedRect(px, this.bodyTop, this.leftPaneW, this.bodyH, 6, BG_ROW);

        if (rows.isEmpty()) {
            drawText(13, emptyText, px + 8, this.bodyTop + 8, TEXT_FAINT);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            final double rowY = this.bodyTop + 4 + i * ROW_H + scroll;
            if (rowY + ROW_H < this.bodyTop || rowY > this.bodyTop + this.bodyH) continue;

            final String name = rows.get(i);
            final String displayName = cleanName(name);
            final boolean hover = GUIUtil.mouseOver(px, rowY, this.leftPaneW, ROW_H, mouseX, mouseY)
                    && mouseY >= this.bodyTop && mouseY < this.bodyTop + this.bodyH;
            if (hover) {
                RenderUtil.drawRoundedRect(px + 3, rowY, this.leftPaneW - 6, ROW_H - 1, 3, BG_HOVER);
            }
            final int color = this.team != null && (this.team.contains(name) || this.team.contains(displayName)) ? Team.getTeamColorARGB() : TEXT_PRIMARY;
            drawText(14, displayName, px + 8, rowY + (ROW_H - fontH(14)) / 2.0, color);
        }
    }

    private double clampScroll(final double target, final int rowCount) {
        final double max = Math.min(0, this.bodyH - 8 - rowCount * ROW_H);
        return Math.max(max, Math.min(0, target));
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        this.updateLayout();
        if (mouseButton != 0) return;

        if (this.colorPickerOpen) {
            final double px = Math.min(this.swatchX - CP_W + 14, this.panelLeft + PANEL_W - CP_W - 8);
            final double py = this.swatchY + 20;
            if (GUIUtil.mouseOver(px, py, CP_W, CP_H, mouseX, mouseY)) {
                handleColorPickerClick(mouseX, mouseY, px, py);
                return;
            }
            this.colorPickerOpen = false;
            this.draggingSquare = false;
            this.draggingHue = false;
        }

        this.nameField.mouseClicked(mouseX, mouseY, mouseButton);

        if (GUIUtil.mouseOver(this.swatchX, this.swatchY, 14, 14, mouseX, mouseY)) {
            this.colorPickerOpen = !this.colorPickerOpen;
            return;
        }

        if (GUIUtil.mouseOver(this.addX, this.inputY, this.addW, FIELD_H, mouseX, mouseY)) {
            addTypedName();
            return;
        }
        if (GUIUtil.mouseOver(this.doneX, this.doneY, 100, BTN_H, mouseX, mouseY)) {
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }
        if (this.team == null) return;

        final List<String> members = this.team.getMembers();
        final int memberRow = rowAt(mouseX, mouseY, this.leftPaneX, this.teamScroll, members.size());
        if (memberRow >= 0) {
            this.team.remove(members.get(memberRow));
            return;
        }

        if (!shouldHideOnlinePlayers()) {
            final List<String> online = onlinePlayers();
            final int onlineRow = rowAt(mouseX, mouseY, this.rightPaneX, this.onlineScroll, online.size());
            if (onlineRow >= 0) {
                final String name = online.get(onlineRow);
                if (this.team.contains(name)) this.team.remove(name);
                else this.team.add(name);
            }
        }
    }

    private void handleColorPickerClick(final int mouseX, final int mouseY, final double px, final double py) {
        final ColorValue cv = colorValue();
        if (cv == null) return;
        final double barW = 14;
        final double unitW = SQ + 5 + barW;
        final double unitX = px + (CP_W - unitW) / 2.0;
        final double sqX = unitX;
        final double barX = unitX + SQ + 5;
        final double sqY = py + 24;

        if (GUIUtil.mouseOver(sqX, sqY, SQ, SQ, mouseX, mouseY)) {
            this.draggingSquare = true;
            handleSquareDrag(mouseX, mouseY);
            return;
        }
        if (GUIUtil.mouseOver(barX, sqY, barW, SQ, mouseX, mouseY)) {
            this.draggingHue = true;
            handleHueDrag(mouseX, mouseY);
            return;
        }
        final double rgbY = sqY + SQ + 6;
        final double presetY = rgbY + 12;
        final double ps = 16;
        final double pg = 4;
        final double totalPs = PRESETS.length * ps + (PRESETS.length - 1) * pg;
        final double psX = px + (CP_W - totalPs) / 2.0;
        for (int i = 0; i < PRESETS.length; i++) {
            final double ppx = psX + i * (ps + pg);
            if (GUIUtil.mouseOver(ppx, presetY + 12, ps, ps, mouseX, mouseY)) {
                final float[] hsv = new float[3];
                Color.RGBtoHSB(PRESETS[i][0], PRESETS[i][1], PRESETS[i][2], hsv);
                cv.setHSB(hsv[0], hsv[1], hsv[2]);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(final int mouseX, final int mouseY, final int state) {
        if (state == 0) {
            this.draggingSquare = false;
            this.draggingHue = false;
        }
    }

    @Override
    protected void mouseClickMove(final int mouseX, final int mouseY, final int clickedMouseButton, final long timeSinceLastClick) {
        if (clickedMouseButton == 0) {
            if (this.draggingSquare) handleSquareDrag(mouseX, mouseY);
            if (this.draggingHue) handleHueDrag(mouseX, mouseY);
        }
    }

    private int rowAt(final int mouseX, final int mouseY, final double px, final double scroll, final int rowCount) {
        if (mouseX < px || mouseX > px + this.leftPaneW || mouseY < this.bodyTop || mouseY > this.bodyTop + this.bodyH) return -1;
        for (int i = 0; i < rowCount; i++) {
            final double rowY = this.bodyTop + 4 + i * ROW_H + scroll;
            if (mouseY >= rowY && mouseY < rowY + ROW_H && rowY >= this.bodyTop && rowY < this.bodyTop + this.bodyH) return i;
        }
        return -1;
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.colorPickerOpen) {
                this.colorPickerOpen = false;
                return;
            }
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }
        if (this.nameField.textboxKeyTyped(typedChar, keyCode)) return;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            addTypedName();
        }
    }

    private void addTypedName() {
        if (this.team == null) return;
        final String name = cleanName(this.nameField.getText());
        if (!name.isEmpty()) {
            this.team.add(name);
            this.nameField.setText("");
        }
    }

    private List<String> onlinePlayers() {
        final List<String> names = new ArrayList<String>();
        if (this.mc.thePlayer == null || this.mc.thePlayer.sendQueue == null) return names;
        final Collection<NetworkPlayerInfo> infos = this.mc.thePlayer.sendQueue.getPlayerInfoMap();
        for (final NetworkPlayerInfo info : infos) {
            if (info != null && info.getGameProfile() != null) {
                final String name = cleanName(info.getGameProfile().getName());
                if (name != null && !name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    private boolean shouldHideOnlinePlayers() {
        if (!isHypixel()) return false;
        final List<String> sidebarLines = sidebarLines();
        return isBedWarsSidebar(sidebarLines) && isPregameSidebar(sidebarLines);
    }

    private boolean isHypixel() {
        final ServerData serverData = this.mc.getCurrentServerData();
        if (serverData == null || serverData.serverIP == null) return false;
        return serverData.serverIP.toLowerCase(Locale.ROOT).contains("hypixel");
    }

    private boolean isBedWarsSidebar(final List<String> sidebarLines) {
        for (final String line : sidebarLines) {
            final String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("bed wars") || lower.contains("bedwars")) {
                return true;
            }
        }
        return false;
    }

    private boolean isPregameSidebar(final List<String> sidebarLines) {
        for (final String line : sidebarLines) {
            final String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("waiting") || lower.contains("starting")
                    || lower.contains("starts in") || lower.contains("players:")) {
                return true;
            }
        }
        return false;
    }

    private List<String> sidebarLines() {
        final List<String> lines = new ArrayList<String>();
        if (this.mc.theWorld == null) return lines;

        final Scoreboard scoreboard = this.mc.theWorld.getScoreboard();
        if (scoreboard == null) return lines;

        final ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) return lines;

        lines.add(cleanName(objective.getDisplayName()));
        final Collection<Score> scores = scoreboard.getSortedScores(objective);
        for (final Score score : scores) {
            if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) continue;
            final ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            lines.add(cleanName(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName())));
        }
        return lines;
    }

    private String cleanName(final String name) {
        if (name == null) return "";
        return StringUtils.stripControlCodes(name).trim();
    }

    private float fontH(final int size) {
        return GuiText.height(size);
    }

    private void drawText(final int size, final String s, final double x, final double y, final int color) {
        GuiText.draw(size, s, x, y, color);
    }

    private void drawTextCentered(final int size, final String s, final double cx, final double y, final int color) {
        GuiText.drawCentered(size, s, cx, y, color);
    }

    private int stringWidth(final int size, final String s) {
        return GuiText.width(size, s);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
