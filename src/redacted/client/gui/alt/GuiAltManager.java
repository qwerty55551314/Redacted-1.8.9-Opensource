package redacted.client.gui.alt;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import org.lwjgl.opengl.GL11;

import redacted.client.alt.AltAccount;
import redacted.client.alt.AltAuthService;
import redacted.client.alt.AltHistoryEntry;
import redacted.client.alt.AltManager;
import redacted.client.alt.AlteningApiClient;
import redacted.client.alt.AlteningService;
import redacted.client.alt.MicrosoftAuthenticator;
import redacted.client.alt.ProxyConfig;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.GuiText;
import redacted.client.util.gui.InputField;
import redacted.client.util.gui.ScrollUtil;
import redacted.client.util.render.HeadCache;
import redacted.client.util.render.MenuBackground;
import redacted.client.util.render.RenderUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GuiAltManager extends GuiScreen {
    private static final Logger LOG = LogManager.getLogger();
    private static final boolean DEBUG = true;

    // palette
    private static final int BG_BASE      = 0xFF0A0A0A;
    private static final int BG_PANE      = 0xFF0F0F0F;
    private static final int BG_ROW       = 0xFF141414;
    private static final int BG_HOVER     = 0xFF1A1A1A;
    private static final int BG_SELECTED  = 0xFF1E1E1E;
    private static final int BG_INPUT     = 0xFF111111;
    private static final int BG_BUTTON    = 0xFF222222;
    private static final int BG_BUTTON_HV = 0xFF333333;
    private static final int BORDER_DEF   = 0xFF333333;
    private static final int BORDER_FOCUS = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SEC     = 0xFF888888;
    private static final int TEXT_FAINT   = 0xFF555555;
    private static final int ACTIVE_GREEN = 0xFF55AA55;
    private static final int INPUT_BG     = 0x22000000;
    private static final int HOVER_SOFT   = 0x33FFFFFF;
    private static final int SHADOW_CLR   = 0x30000000;
    private static final int DIVIDER      = 0xFF1A1A1A;
    private static final int ERROR_CLR    = 0xFF666666;

    // layout
    private static final double OUTER_PAD = 16;
    private static final double PAD = 12;
    private static final double LEFT_W = 270;
    private static final double PANE_GAP = 12;
    private static final double ROW_H = 44;
    private static final double ROW_GAP = 2;
    private static final double SEARCH_H = 28;
    private static final double TAB_H = 32;
    private static final double FIELD_H = 34;
    private static final double FIELD_GAP = 10;
    private static final double BTN_H = 36;
    private static final double SMALL_BTN_H = 28;
    private static final double STATUS_BAR_H = 28;

    // design minimums: below these the whole UI shrinks uniformly instead of
    // letting panes collapse and icons escape their boxes
    private static final double MIN_PANEL_W = 560;
    private static final double MIN_PANEL_H = 340;

    // runtime-scaled layout values (uiScale adapts everything to the ScaledResolution)
    private double uiScale = 1.0;
    private double rowH = ROW_H;
    private double rowGap = ROW_GAP;
    private double searchH = SEARCH_H;
    private double statusBarH = STATUS_BAR_H;
    private double iconSize = 14;

    // icons
    private static final ResourceLocation ICON_MICROSOFT = new ResourceLocation("minecraft:redacted/alt/microsoft.png");
    private static final ResourceLocation ICON_CRACKED   = new ResourceLocation("minecraft:redacted/alt/cracked.png");
    private static final ResourceLocation ICON_PERSON    = new ResourceLocation("minecraft:redacted/alt/person.png");
    private static final ResourceLocation ICON_KEY       = new ResourceLocation("minecraft:redacted/alt/key.png");
    private static final ResourceLocation ICON_LOCK      = new ResourceLocation("minecraft:redacted/alt/lock.png");
    private static final ResourceLocation ICON_DICE      = new ResourceLocation("minecraft:redacted/alt/dice.png");
    private static final ResourceLocation ICON_REFRESH   = new ResourceLocation("minecraft:redacted/alt/refresh.png");
    private static final ResourceLocation ICON_SERVER    = new ResourceLocation("minecraft:redacted/alt/server.png");
    private static final ResourceLocation ICON_EYE       = new ResourceLocation("minecraft:redacted/alt/eye.png");
    private static final ResourceLocation ICON_EYE_CROSS = new ResourceLocation("minecraft:redacted/alt/eye_crossed.png");

    private static final long DOUBLE_CLICK_MS = 400L;
    private static final long DELETE_ARM_MS = 3000L;

    private final GuiScreen parentScreen;
    private final AltAuthService authService = new AltAuthService();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    // window resizes re-run initGui -- only play the open zoom on the first init
    private boolean animatedIn;
    private final ScrollUtil scrollUtil = new ScrollUtil();

    private final InputField tokenField = new InputField(true, "Refresh Token or MS Token");
    private final InputField alteningApiKeyField = new InputField(false, "API Key or Alt Token");
    private final InputField crackedNameField = new InputField(false, "Username");
    private final InputField proxyField = new InputField(false, "Proxy Host: Port (optional)");
    private final InputField proxyUserField = new InputField(false, "Proxy Username (optional)");
    private final InputField proxyPassField = new InputField(true, "Proxy Password (optional)");
    private final InputField searchField = new InputField(false, "Search accounts...");

    private String status = "";
    private int statusColor = TEXT_SEC;
    private boolean busy;
    private boolean cracked;
    private boolean altening;
    private boolean loadedProxyPrefs;

    private String selectedUuid;
    private long lastRowClickAt;
    private String lastRowClickUuid;
    private String deleteArmedKey;
    private long deleteArmedAt;
    private final Map<String, Double> rowHoverAnim = new HashMap<String, Double>();

    // Device code flow
    private boolean deviceCodeFlowActive;
    private MicrosoftAuthenticator.DeviceCodeInfo deviceCodeInfo;
    private volatile String deviceCodePollStatus;
    private volatile boolean deviceCodePollDone;
    private volatile boolean deviceCodePollSuccess;
    private volatile String deviceCodeResultToken;
    private volatile String deviceCodeResultRefreshToken;
    private Thread deviceCodePollThread;

    // cached layout
    private double left, top, panelW, panelH;
    private double leftPaneX, leftPaneY, leftPaneW, leftPaneH;
    private double searchX, searchY, searchW;
    private double listX, listY, listW, listH;
    private double listBottomY, refreshBtnX, refreshBtnY, refreshBtnSize;
    private double rightPaneX, rightPaneY, rightPaneW, rightPaneH;
    private double tabY, tabMsX, tabCrX, tabAltX, tabW;
    private double fieldX, fieldW;
    private double fieldY1, fieldY2, fieldY3, fieldY4;
    private double primaryX, primaryY, primaryW;
    private double diceX, diceY, diceSize;
    private double eyeX, eyeY, eyeSize;
    private double statusTextX, statusTextY;
    private double bottomBtnY, bottomBtnW;
    private double btnRestoreX, btnRemoveX, btnClearX, btnBackX;
    private double deviceCodeBtnX, deviceCodeBtnY, deviceCodeBtnW;

    // dynamic sizes (shrink on small windows / high GUI scales)
    private double dynFieldH, dynFieldGap, dynTabH, dynBtnH, dynSmallBtnH;

    public GuiAltManager(final GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        AltManager.getInstance().load(this.mc.mcDataDir);

        this.tokenField.setMaxStringLength(4096);
        this.alteningApiKeyField.setMaxStringLength(4096);
        this.crackedNameField.setMaxStringLength(16);
        this.proxyField.setMaxStringLength(256);
        this.proxyUserField.setMaxStringLength(128);
        this.proxyPassField.setMaxStringLength(256);
        this.searchField.setMaxStringLength(32);

        if (!this.loadedProxyPrefs) {
            final AltManager manager = AltManager.getInstance();
            this.proxyField.setText(manager.getLastProxyAddress());
            this.proxyUserField.setText(manager.getLastProxyUsername());
            this.proxyPassField.setText(manager.getLastProxyPassword());
            this.alteningApiKeyField.setText(manager.getLastAlteningApiKey());
            this.loadedProxyPrefs = true;
        }

        if (this.animatedIn) {
            this.openAnimation.setValue(1);
        } else {
            this.openAnimation.setValue(0);
            this.openAnimation.reset();
            this.animatedIn = true;
        }
        this.scrollUtil.setTarget(0);
    }

    private void updateLayout() {
        // uniform shrink factor when the scaled resolution is below the design minimums.
        // fonts, rows, icons and paddings all scale together so the layout keeps its
        // proportions at small windows / high GUI scales instead of overflowing
        this.uiScale = Math.max(0.4, Math.min(1.0, Math.min(
                (this.width - OUTER_PAD * 2) / MIN_PANEL_W,
                (this.height - OUTER_PAD * 2) / MIN_PANEL_H)));
        this.rowH = ROW_H * this.uiScale;
        this.rowGap = Math.max(1, ROW_GAP * this.uiScale);
        this.searchH = SEARCH_H * this.uiScale;
        this.statusBarH = STATUS_BAR_H * this.uiScale;
        this.iconSize = 14 * this.uiScale;

        this.left = OUTER_PAD;
        this.top = OUTER_PAD;
        this.panelW = this.width - OUTER_PAD * 2;
        this.panelH = this.height - OUTER_PAD * 2;

        // left pane width adapts to available space:
        //   - proportional to the panel (40%), capped at LEFT_W
        //   - hard-capped so the right pane keeps a usable minimum width -- this cap is
        //     what used to be missing, letting rightPaneW go negative on small windows
        //     and pushing every field-anchored icon outside its box
        final double pad = PAD * this.uiScale;
        final double paneGap = PANE_GAP * this.uiScale;
        final double minRightW = 170 * this.uiScale;
        this.leftPaneW = Math.min(LEFT_W * this.uiScale, this.panelW * 0.40 - paneGap);
        this.leftPaneW = Math.max(110 * this.uiScale,
                Math.min(this.leftPaneW, this.panelW - pad * 2 - paneGap - minRightW));
        this.leftPaneX = this.left + pad;
        this.leftPaneY = this.top + pad;
        this.leftPaneH = this.panelH - pad * 2;

        // search bar
        this.searchX = this.leftPaneX + 8 * this.uiScale;
        this.searchY = this.leftPaneY + 8 * this.uiScale;
        this.searchW = this.leftPaneW - 16 * this.uiScale;
        this.searchField.setBounds(this.searchX, this.searchY, this.searchW, this.searchH);

        // account list
        this.listX = this.leftPaneX + 6 * this.uiScale;
        this.listY = this.searchY + this.searchH + 8 * this.uiScale;
        this.listW = this.leftPaneW - 12 * this.uiScale;
        this.listBottomY = this.leftPaneY + this.leftPaneH - this.statusBarH;
        this.listH = this.listBottomY - this.listY - 4 * this.uiScale;

        // refresh button
        this.refreshBtnSize = this.iconSize;
        this.refreshBtnX = this.leftPaneX + this.leftPaneW - 8 * this.uiScale - this.refreshBtnSize;
        this.refreshBtnY = this.listBottomY + (this.statusBarH - this.refreshBtnSize) / 2.0;

        // right pane
        this.rightPaneX = this.leftPaneX + this.leftPaneW + paneGap;
        this.rightPaneY = this.top + pad;
        this.rightPaneW = Math.max(minRightW, this.panelW - pad * 2 - this.leftPaneW - paneGap);
        this.rightPaneH = this.panelH - pad * 2;

        // tabs
        this.tabY = this.rightPaneY + 38 * this.uiScale;
        this.tabW = Math.max(42 * this.uiScale, (this.rightPaneW - 36 * this.uiScale) / 3.0);
        this.tabMsX = this.rightPaneX + 12 * this.uiScale;
        this.tabCrX = this.tabMsX + this.tabW + 12 * this.uiScale;
        this.tabAltX = this.tabCrX + this.tabW + 12 * this.uiScale;

        // form fields - compress when the right pane is narrow (4x/5x GUI scale)
        final double minComfortableRightW = 280 * this.uiScale;
        final double shrink = Math.max(0.62, Math.min(1.0, this.rightPaneW / minComfortableRightW));
        this.dynFieldH = Math.max(22 * this.uiScale, FIELD_H * shrink);
        this.dynFieldGap = Math.max(3 * this.uiScale, FIELD_GAP * shrink);
        this.dynTabH = Math.max(22 * this.uiScale, TAB_H * shrink);
        this.dynBtnH = Math.max(24 * this.uiScale, BTN_H * shrink);
        this.dynSmallBtnH = Math.max(18 * this.uiScale, SMALL_BTN_H * shrink);

        // vertical fit: when the pane is short, shrink the variable part of the stack
        // (tabs + 4 fields + buttons) so the bottom row never overlaps the status text
        final double fixedV = (38 + 14 + 16 + 8 + 14 + pad + 8) * this.uiScale; // headers/gaps/status/bottom pad
        final double varV = this.dynTabH + 4 * this.dynFieldH + 3 * this.dynFieldGap + this.dynBtnH + this.dynSmallBtnH;
        final double vshrink = Math.min(1.0, Math.max(0.45, (this.rightPaneH - fixedV) / Math.max(1, varV)));
        if (vshrink < 1.0) {
            this.dynFieldH = Math.max(16 * this.uiScale, this.dynFieldH * vshrink);
            this.dynFieldGap = Math.max(2 * this.uiScale, this.dynFieldGap * vshrink);
            this.dynTabH = Math.max(16 * this.uiScale, this.dynTabH * vshrink);
            this.dynBtnH = Math.max(18 * this.uiScale, this.dynBtnH * vshrink);
            this.dynSmallBtnH = Math.max(14 * this.uiScale, this.dynSmallBtnH * vshrink);
        }

        this.fieldX = this.rightPaneX + 12 * this.uiScale;
        this.fieldW = Math.max(96 * this.uiScale, this.rightPaneW - 24 * this.uiScale);
        this.fieldY1 = this.tabY + this.dynTabH + 14 * this.uiScale;
        this.fieldY2 = this.fieldY1 + this.dynFieldH + this.dynFieldGap;
        this.fieldY3 = this.fieldY2 + this.dynFieldH + this.dynFieldGap;
        this.fieldY4 = this.fieldY3 + this.dynFieldH + this.dynFieldGap;

        // dice button
        this.diceSize = this.iconSize;
        this.diceX = this.fieldX + this.fieldW - this.diceSize - 6 * this.uiScale;
        this.diceY = this.fieldY1 + (this.dynFieldH - this.diceSize) / 2.0;

        // eye toggle
        this.eyeSize = this.iconSize;
        this.eyeX = this.fieldX + this.fieldW - this.eyeSize - 6 * this.uiScale;
        this.eyeY = this.fieldY4 + (this.dynFieldH - this.eyeSize) / 2.0;

        // device code button (Microsoft tab only) - sits on same row as the token field,
        // vertically centered so it doesn't overlap the proxy-host field below
        this.deviceCodeBtnW = Math.min(100 * this.uiScale, this.fieldW * 0.5);
        this.deviceCodeBtnX = this.fieldX + this.fieldW - this.deviceCodeBtnW;
        this.deviceCodeBtnY = this.fieldY1 + (this.dynFieldH - this.dynSmallBtnH) / 2.0;

        // primary button
        this.primaryW = this.fieldW;
        this.primaryX = this.fieldX;
        this.primaryY = this.fieldY4 + this.dynFieldH + 16 * this.uiScale;

        // status text
        this.statusTextX = this.fieldX + this.fieldW / 2.0;
        this.statusTextY = this.primaryY + this.dynBtnH + 8 * this.uiScale;

        // bottom buttons
        this.bottomBtnY = this.rightPaneY + this.rightPaneH - pad - this.dynSmallBtnH;
        if (this.bottomBtnY < this.statusTextY + 12 * this.uiScale) {
            this.bottomBtnY = this.statusTextY + 12 * this.uiScale;
        }
        this.bottomBtnW = Math.max(34 * this.uiScale, (this.fieldW - 24 * this.uiScale) / 4.0);
        this.btnRestoreX = this.fieldX;
        this.btnRemoveX = this.btnRestoreX + this.bottomBtnW + 8 * this.uiScale;
        this.btnClearX = this.btnRemoveX + this.bottomBtnW + 8 * this.uiScale;
        this.btnBackX = this.btnClearX + this.bottomBtnW + 8 * this.uiScale;
    }

    @Override
    public void updateScreen() {
        this.tokenField.updateCursorCounter();
        this.alteningApiKeyField.updateCursorCounter();
        this.crackedNameField.updateCursorCounter();
        this.proxyField.updateCursorCounter();
        this.proxyUserField.updateCursorCounter();
        this.proxyPassField.updateCursorCounter();
        this.searchField.updateCursorCounter();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.updateLayout();
        this.ensureSelectionValid();

        // cover the dirt background with the animated purple menu shader
        MenuBackground.render(this.width, this.height);

        this.openAnimation.run(1.0);
        final double scale = this.openAnimation.getValue();

        GlStateManager.pushMatrix();
        final double cx = this.left + this.panelW / 2.0;
        final double cy = this.top + this.panelH / 2.0;
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(scale, scale, 1.0);
        GlStateManager.translate(-cx, -cy, 0);

        // shadow
        if (scale > 0.95) {
            RenderUtil.drawRoundedRect(this.left - 2, this.top - 2, this.panelW + 4, this.panelH + 4, 12, SHADOW_CLR);
        }
        // panel
        RenderUtil.drawRoundedRect(this.left, this.top, this.panelW, this.panelH, 10, BG_BASE);
        // inner glow
        RenderUtil.drawRoundedOutline(this.left + 1, this.top + 1, this.panelW - 2, this.panelH - 2, 9, 1.0f, 0x0DFFFFFF);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();

        this.drawLeftPane(mouseX, mouseY);
        this.drawRightPane(mouseX, mouseY);

        GlStateManager.popMatrix();
    }

    // left pane

    private void drawLeftPane(final int mouseX, final int mouseY) {
        RenderUtil.drawRoundedRect(this.leftPaneX, this.leftPaneY, this.leftPaneW, this.leftPaneH, 8, BG_PANE);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();

        // search bar
        this.searchField.draw();

        // account list backing
        RenderUtil.drawRoundedRect(this.listX, this.listY, this.listW, this.listH, 6, BG_INPUT);

        final List<AltHistoryEntry> filtered = this.getFilteredHistory();

        // scroll (wheel input is owned here, bounded to the list rect; ScrollUtil only animates)
        final int wheel = Mouse.getDWheel();
        if (wheel != 0 && GUIUtil.mouseOver(this.listX, this.listY, this.listW, this.listH, mouseX, mouseY)) {
            this.scrollUtil.setTarget(this.scrollUtil.getTarget() + (wheel > 0 ? 30 : -30));
        }
        // rows start 4px inside the list, so the scrollable view is the inset rect and the
        // last row's trailing gap doesn't count as content -- the old math stopped the last
        // row ~2px short of the bottom, reading as a clipped gap above the status bar
        final double contentH = Math.max(0, filtered.size() * (this.rowH + this.rowGap) - this.rowGap);
        final double viewH = this.listH - 8 * this.uiScale;
        this.scrollUtil.setMax(-(Math.max(0, contentH - viewH)));
        this.scrollUtil.onRender();

        if (filtered.isEmpty()) {
            drawTextCentered(13,
                    this.searchField.getText().isEmpty() ? "No saved alts" : "No matches",
                    this.listX + this.listW / 2.0, this.listY + this.listH / 2.0 - 6, TEXT_FAINT);
        } else {
            // clip rows to the list rect once the open animation settles (scissor coords are
            // in window space, which the zoom matrix would desync); during the zoom rows are
            // simply drawn, matching the old behavior
            final boolean clip = this.openAnimation.getValue() >= 0.99;
            if (clip) {
                this.enableScissor(this.listX, this.listY, this.listW, this.listH);
            }
            for (int i = 0; i < filtered.size(); i++) {
                final AltHistoryEntry entry = filtered.get(i);
                final double rowY = this.listY + 4 * this.uiScale + i * (this.rowH + this.rowGap) + this.scrollUtil.getScroll();
                // cheap whole-row cull for perf; the scissor handles partially visible rows,
                // which the old center-based cull both overflowed and dropped
                if (rowY + this.rowH < this.listY - 2 || rowY > this.listY + this.listH + 2) continue;
                this.drawListRow(entry, rowY, mouseX, mouseY);
            }
            if (clip) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            // fade strips (vertical fade over the list edges)
            RenderUtil.drawGradientRectVertical(this.listX, this.listY, this.listW, 6 * this.uiScale, BG_PANE, 0x000F0F0F);
            RenderUtil.drawGradientRectVertical(this.listX, this.listY + this.listH - 6 * this.uiScale, this.listW, 6 * this.uiScale, 0x000F0F0F, BG_PANE);
        }

        // status bar
        final List<AltHistoryEntry> all = AltManager.getInstance().getHistory();
        drawTextCentered(11, all.size() + " account" + (all.size() != 1 ? "s" : ""),
                this.leftPaneX + this.leftPaneW / 2.0, this.listBottomY + (this.statusBarH - fontH(11)) / 2.0, TEXT_SEC);

        // refresh button (PNG icon)
        final boolean refreshHover = GUIUtil.mouseOver(this.refreshBtnX, this.refreshBtnY, this.refreshBtnSize, this.refreshBtnSize, mouseX, mouseY);
        RenderUtil.drawIcon(ICON_REFRESH, this.refreshBtnX, this.refreshBtnY, this.refreshBtnSize);
        if (refreshHover) {
            RenderUtil.drawRoundedOutline(this.refreshBtnX - 1, this.refreshBtnY - 1, this.refreshBtnSize + 2, this.refreshBtnSize + 2, 4, 1.0f, HOVER_SOFT);
        }
    }

    /**
     * Scissor in the 1.8.8 convention: GUI-space rect converted to window pixels with the
     * mandatory Y flip ({@code scaledHeight - (y + height)}), rounded to whole device pixels.
     */
    private void enableScissor(final double x, final double y, final double width, final double height) {
        final ScaledResolution sr = new ScaledResolution(this.mc);
        final int sf = Math.max(1, sr.getScaleFactor());
        final int sx = (int) Math.round(x * sf);
        final int sy = (int) Math.round((sr.getScaledHeight() - (y + height)) * sf);
        final int sw = (int) Math.round(width * sf);
        final int sh = (int) Math.round(height * sf);
        if (sw <= 0 || sh <= 0) {
            return;
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    private void drawListRow(final AltHistoryEntry entry, final double rowY, final int mouseX, final int mouseY) {
        final double rowX = this.listX + 4 * this.uiScale;
        final double rowW = this.listW - 8 * this.uiScale;
        final boolean hover = GUIUtil.mouseOver(rowX, rowY, rowW, this.rowH, mouseX, mouseY)
                && mouseY >= this.listY && mouseY < this.listY + this.listH;
        final boolean selected = entry.getUuid().equals(this.selectedUuid);

        // hover lerp
        final String key = entry.getUuid();
        double anim = this.rowHoverAnim.containsKey(key) ? this.rowHoverAnim.get(key) : 0.0;
        anim += ((hover ? 1.0 : 0.0) - anim) * 0.18;
        this.rowHoverAnim.put(key, anim);

        final boolean isActive = !entry.isCracked()
                && this.mc.getSession().getPlayerID() != null
                && this.mc.getSession().getPlayerID().equals(entry.getUuid());

        // row background
        final int baseFill = anim > 0.02 ? lerpColor(BG_ROW, BG_HOVER, (float) anim) : BG_ROW;
        RenderUtil.drawRoundedRect(rowX, rowY, rowW, this.rowH, 6 * this.uiScale, selected ? BG_SELECTED : baseFill);

        // accent bar
        if (isActive) {
            RenderUtil.drawRect(rowX + 2 * this.uiScale, rowY + 6 * this.uiScale, 2 * this.uiScale, this.rowH - 12 * this.uiScale, ACTIVE_GREEN);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();

        // avatar
        final double avatarCx = rowX + 24 * this.uiScale;
        final double avatarCy = rowY + this.rowH / 2.0;
        HeadCache.drawHead(entry.getUuid(), entry.getUsername(), entry.isCracked(), avatarCx, avatarCy, 14 * this.uiScale);

        // text
        final double textX = rowX + 52 * this.uiScale;
        final double textMaxW = rowW - 52 * this.uiScale;
        final String shownName = endTruncate(entry.getUsername(), (int) textMaxW, 14);
        drawText(14, shownName, textX, rowY + 10 * this.uiScale, TEXT_PRIMARY);

        // type icon + label
        final String typeLabel = entry.isCracked() ? "Cracked" : (entry.isAltening() ? "TheAltening" : "Microsoft");
        final ResourceLocation typeIcon = entry.isCracked() ? ICON_CRACKED : (entry.isAltening() ? ICON_KEY : ICON_MICROSOFT);
        final float subH = fontH(11);
        final double subIcon = 10 * this.uiScale;
        final double subY = rowY + this.rowH - subH - 6 * this.uiScale;
        RenderUtil.drawIcon(typeIcon, textX, subY + (subH - subIcon) / 2.0, subIcon);
        drawText(11, typeLabel, textX + subIcon + 3 * this.uiScale, subY, TEXT_SEC);
    }

    // right pane

    private void drawRightPane(final int mouseX, final int mouseY) {
        RenderUtil.drawRoundedRect(this.rightPaneX, this.rightPaneY, this.rightPaneW, this.rightPaneH, 8, BG_PANE);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();

        // title
        drawText(16, this.deviceCodeFlowActive ? "Microsoft Authentication" : "Add Account",
                this.rightPaneX + 16, this.rightPaneY + 16, TEXT_PRIMARY);

        if (this.deviceCodeFlowActive) {
            this.drawDeviceCodePane(mouseX, mouseY);
        } else {
            this.drawNormalRightPane(mouseX, mouseY);
        }
    }

    private void drawNormalRightPane(final int mouseX, final int mouseY) {
        // tabs
        drawTab("Microsoft", this.tabMsX, this.tabY, this.tabW, this.dynTabH, mouseX, mouseY, !this.cracked && !this.altening, ICON_MICROSOFT);
        drawTab("Cracked", this.tabCrX, this.tabY, this.tabW, this.dynTabH, mouseX, mouseY, this.cracked, ICON_CRACKED);
        drawTab("Altening", this.tabAltX, this.tabY, this.tabW, this.dynTabH, mouseX, mouseY, this.altening, ICON_KEY);

        // form fields
        drawText(13, this.cracked ? "Username" : (this.altening ? "API Key or Alt Token" : "Refresh Token or MS Token"),
                this.fieldX, this.fieldY1 - 12, TEXT_SEC);
        this.drawFormField1(mouseX, mouseY);
        this.drawFormField(this.proxyField, this.fieldY2, ICON_SERVER, false);
        this.drawFormField(this.proxyUserField, this.fieldY3, ICON_PERSON, false);
        this.proxyPassField.setLeftPad(30);
        this.proxyPassField.setBounds(this.fieldX, this.fieldY4, this.fieldW - this.eyeSize - 10, this.dynFieldH);
        this.proxyPassField.draw();
        RenderUtil.drawIcon(ICON_LOCK, this.fieldX + 8, this.fieldY4 + (this.dynFieldH - this.iconSize) / 2.0, this.iconSize);
        drawEyeButton(this.eyeX, this.eyeY, this.eyeSize, this.proxyPassField.isMasked(), mouseX, mouseY);

        // primary button
        final String primaryLabel = this.altening
                ? (isAlteningApiKey(this.alteningApiKeyField.getText()) ? (this.selectedUuid != null ? "Generate New & Login" : "Generate & Login") : "Login Token")
                : (this.selectedUuid != null ? "Login & Update" : "Login & Save");
        drawPrimaryButton(primaryLabel, this.primaryX, this.primaryY, this.primaryW, this.dynBtnH, mouseX, mouseY, !this.busy);

        // status text
        if (this.status != null && !this.status.isEmpty()) {
            final String shown = endTruncate(this.status, (int) this.fieldW, 12);
            drawTextCentered(12, shown, this.statusTextX, this.statusTextY, this.statusColor);
        }

        // bottom buttons
        final AltManager manager = AltManager.getInstance();
        final boolean canRestore = manager.hasBaseline() && !manager.isOnBaseline(this.mc.getSession()) && !this.busy;
        drawSmallButton("Restore", this.btnRestoreX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY, canRestore);

        final boolean canRemove = this.selectedUuid != null && !this.busy;
        final boolean removeArmed = this.isArmed("row:" + this.selectedUuid);
        drawSmallButton(removeArmed ? "Confirm?" : "Remove",
                this.btnRemoveX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY, canRemove);

        final boolean clearArmed = this.isArmed("clear");
        drawSmallButton(clearArmed ? "Confirm?" : "Clear All",
                this.btnClearX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY, !this.busy);

        drawSmallButton("Back", this.btnBackX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY, true);
    }

    private void drawDeviceCodePane(final int mouseX, final int mouseY) {
        final double cx = this.rightPaneX + this.rightPaneW / 2.0;

        // instructions
        final String[] lines;
        if (this.deviceCodePollSuccess) {
            lines = new String[] {
                "Authentication successful!",
                "",
                "You can now close the browser window.",
                "Your account will be logged in momentarily."
            };
        } else if (this.deviceCodeInfo != null) {
            lines = new String[] {
                "1. Open this URL in your browser:",
                this.deviceCodeInfo.verificationUri,
                "",
                "2. Enter the code: " + this.deviceCodeInfo.userCode,
                "",
                "3. Sign in with your Microsoft account and authorize the app.",
                "",
                "Waiting for authorization" + getDotAnimation()
            };
        } else {
            lines = new String[] {
                "Starting device code flow...",
                "",
                "Please wait."
            };
        }

        double lineY = this.rightPaneY + 60;
        for (final String line : lines) {
            if (line.startsWith("1. Open this URL") || line.startsWith("2. Enter the code")) {
                drawText(12, line, this.rightPaneX + 16, lineY, TEXT_SEC);
                lineY += 16;
            } else if (line.startsWith("http")) {
                drawText(12, line, this.rightPaneX + 16, lineY, TEXT_PRIMARY);
                lineY += 18;
            } else if (line.startsWith("3. Sign in") || line.startsWith("4.")) {
                drawText(12, line, this.rightPaneX + 16, lineY, TEXT_SEC);
                lineY += 16;
            } else if (line.startsWith("Authentication successful")) {
                drawText(14, line, this.rightPaneX + 16, lineY, ACTIVE_GREEN);
                lineY += 20;
            } else if (line.startsWith("Waiting")) {
                drawTextCentered(12, line, cx, lineY, TEXT_SEC);
                lineY += 16;
            } else {
                lineY += 12;
            }
        }

        // polling status
        if (this.deviceCodePollStatus != null) {
            drawTextCentered(12, this.deviceCodePollStatus, cx, lineY + 20, TEXT_FAINT);
        }

        // cancel / close button
        final String btnLabel = this.deviceCodePollSuccess ? "Close" : "Cancel";
        final boolean btnHover = GUIUtil.mouseOver(this.primaryX, this.primaryY + 20, this.primaryW, this.dynBtnH, mouseX, mouseY);
        final int btnFill = btnHover ? BG_BUTTON_HV : BG_BUTTON;
        RenderUtil.drawRoundedRect(this.primaryX, this.primaryY + 20, this.primaryW, this.dynBtnH, 6, btnFill);
        drawTextCentered(14, btnLabel, this.primaryX + this.primaryW / 2.0, this.primaryY + 20 + (this.dynBtnH - fontH(14)) / 2.0, TEXT_PRIMARY);
    }

    private int dotFrame;
    private long lastDotUpdate;

    private String getDotAnimation() {
        final long now = System.currentTimeMillis();
        if (now - this.lastDotUpdate > 500) {
            this.dotFrame = (this.dotFrame + 1) % 4;
            this.lastDotUpdate = now;
        }
        final String dots = "...".substring(0, this.dotFrame);
        return dots;
    }

    // Microsoft token, Altening API key, and cracked username share this row.

    private void drawFormField1(final int mouseX, final int mouseY) {
        if (this.cracked) {
            this.crackedNameField.setLeftPad(30);
            this.crackedNameField.setBounds(this.fieldX, this.fieldY1, this.fieldW - this.diceSize - 10, this.dynFieldH);
            this.crackedNameField.draw();
            RenderUtil.drawIcon(ICON_PERSON, this.fieldX + 8, this.fieldY1 + (this.dynFieldH - this.iconSize) / 2.0, this.iconSize);
            // dice button
            RenderUtil.drawIcon(ICON_DICE, this.diceX, this.diceY, this.diceSize);
        } else if (this.altening) {
            this.alteningApiKeyField.setLeftPad(30);
            this.alteningApiKeyField.setBounds(this.fieldX, this.fieldY1, this.fieldW, this.dynFieldH);
            this.alteningApiKeyField.draw();
            RenderUtil.drawIcon(ICON_KEY, this.fieldX + 8, this.fieldY1 + (this.dynFieldH - this.iconSize) / 2.0, this.iconSize);
        } else {
            // paste-only Microsoft: the device-code "Get Token" button is intentionally not surfaced, so the
            // token field takes the full width.
            this.tokenField.setLeftPad(30);
            this.tokenField.setBounds(this.fieldX, this.fieldY1, this.fieldW, this.dynFieldH);
            this.tokenField.draw();
            RenderUtil.drawIcon(ICON_KEY, this.fieldX + 8, this.fieldY1 + (this.dynFieldH - this.iconSize) / 2.0, this.iconSize);
        }
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawFormField(final InputField field, final double y, final ResourceLocation icon, final boolean isPass) {
        final double rightPad = isPass ? this.eyeSize + 10 : 0;
        field.setLeftPad(30);
        field.setBounds(this.fieldX, y, this.fieldW - rightPad, this.dynFieldH);
        field.draw();
        RenderUtil.drawIcon(icon, this.fieldX + 8, y + (this.dynFieldH - this.iconSize) / 2.0, this.iconSize);
    }

    private void drawTab(final String label, final double x, final double y, final double w, final double h,
                         final int mouseX, final int mouseY, final boolean selected, final ResourceLocation icon) {
        final boolean hover = GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY);
        final int fill = selected ? BG_BUTTON : (hover ? BG_HOVER : BG_ROW);
        final int border = selected ? BORDER_FOCUS : (hover ? BORDER_DEF : 0x00000000);
        final int textColor = selected ? TEXT_PRIMARY : TEXT_SEC;

        RenderUtil.drawRoundedRect(x, y, w, h, 6, fill);
        if (border != 0) {
            RenderUtil.drawRoundedOutline(x, y, w, h, 6, 1.0f, border);
        }

        // icon + text centered as group
        final double iconSz = this.iconSize;
        final double gap = 6;
        final int textW = stringWidth(13, label);
        final double groupW = iconSz + gap + textW;
        final double startX = x + (w - groupW) / 2.0;
        final double baselineY = y + (h - fontH(13)) / 2.0;
        final double iconY = baselineY + (fontH(13) - iconSz) / 2.0;

        RenderUtil.drawIcon(icon, startX, iconY, iconSz);
        drawText(13, label, startX + iconSz + gap, baselineY, textColor);
    }

    private void drawPrimaryButton(final String label, final double x, final double y, final double w, final double h,
                                    final int mouseX, final int mouseY, final boolean enabled) {
        final boolean hover = enabled && GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY);
        final int fill = enabled ? (hover ? BG_BUTTON_HV : BG_BUTTON) : BG_ROW;

        RenderUtil.drawRoundedRect(x, y, w, h, 6, fill);

        // icon + text centered as group
        final double iconSz = this.iconSize;
        final double gap = 8;
        final int textW = stringWidth(14, label);
        final double groupW = iconSz + gap + textW;
        final double startX = x + (w - groupW) / 2.0;
        final double baselineY = y + (h - fontH(14)) / 2.0;
        final double iconY = baselineY + (fontH(14) - iconSz) / 2.0;

        RenderUtil.drawIcon(ICON_LOCK, startX, iconY, iconSz);
        drawText(14, label, startX + iconSz + gap, baselineY, enabled ? TEXT_PRIMARY : TEXT_FAINT);
    }

    private void drawSmallButton(final String label, final double x, final double y, final double w, final double h,
                                  final int mouseX, final int mouseY, final boolean enabled) {
        final boolean hover = enabled && GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY);
        final int fill = hover ? BG_HOVER : BG_ROW;

        RenderUtil.drawRoundedRect(x, y, w, h, 5, fill);

        final float fh = fontH(12);
        drawTextCentered(12, label, x + w / 2.0, y + (h - fh) / 2.0, enabled ? TEXT_SEC : TEXT_FAINT);
    }

    private void drawEyeButton(final double x, final double y, final double size, final boolean masked,
                                final int mouseX, final int mouseY) {
        final boolean hover = GUIUtil.mouseOver(x, y, size, size, mouseX, mouseY);
        final int color = hover ? TEXT_PRIMARY : TEXT_SEC;
        // routed through RenderUtil.drawIcon so the eye gets the same trilinear
        // sampling + mipmaps as every other icon (it used to hand-roll LINEAR-only)
        RenderUtil.drawIcon(masked ? ICON_EYE_CROSS : ICON_EYE, x, y, size,
                ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
    }

    // input handling

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        this.updateLayout();

        this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.cracked) {
            this.crackedNameField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (this.altening) {
            this.alteningApiKeyField.mouseClicked(mouseX, mouseY, mouseButton);
        } else {
            this.tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        this.proxyField.mouseClicked(mouseX, mouseY, mouseButton);
        this.proxyUserField.mouseClicked(mouseX, mouseY, mouseButton);
        this.proxyPassField.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) return;

        // device code: cancel/close button
        if (this.deviceCodeFlowActive && GUIUtil.mouseOver(this.primaryX, this.primaryY + 20, this.primaryW, this.dynBtnH, mouseX, mouseY)) {
            this.cancelDeviceCodeFlow();
            return;
        }

        // back works even while busy
        if (GUIUtil.mouseOver(this.btnBackX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY)) {
            this.cancelDeviceCodeFlow();
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }

        boolean disarm = true;
        try {
            // refresh
            if (GUIUtil.mouseOver(this.refreshBtnX, this.refreshBtnY, this.refreshBtnSize, this.refreshBtnSize, mouseX, mouseY)) {
                for (final AltHistoryEntry e : AltManager.getInstance().getHistory()) {
                    HeadCache.invalidate(e.getUuid());
                }
                this.rowHoverAnim.clear();
                return;
            }

            // tabs (blocked during device code flow)
            if (!this.busy && !this.deviceCodeFlowActive && GUIUtil.mouseOver(this.tabMsX, this.tabY, this.tabW, this.dynTabH, mouseX, mouseY)) {
                this.cracked = false;
                this.altening = false;
                this.unfocusAll();
                return;
            }
            if (!this.busy && !this.deviceCodeFlowActive && GUIUtil.mouseOver(this.tabCrX, this.tabY, this.tabW, this.dynTabH, mouseX, mouseY)) {
                this.cracked = true;
                this.altening = false;
                this.unfocusAll();
                return;
            }
            if (!this.busy && !this.deviceCodeFlowActive && GUIUtil.mouseOver(this.tabAltX, this.tabY, this.tabW, this.dynTabH, mouseX, mouseY)) {
                this.cracked = false;
                this.altening = true;
                this.unfocusAll();
                return;
            }

            // dice
            if (this.cracked && !this.busy
                    && GUIUtil.mouseOver(this.diceX, this.diceY, this.diceSize, this.diceSize, mouseX, mouseY)) {
                this.crackedNameField.setText(AltManager.generateRandomName());
                return;
            }

            // checkbox
            if (GUIUtil.mouseOver(this.eyeX, this.eyeY, this.eyeSize, this.eyeSize, mouseX, mouseY)) {
                this.proxyPassField.setMasked(!this.proxyPassField.isMasked());
                return;
            }

            // primary
            if (!this.busy && !this.deviceCodeFlowActive && GUIUtil.mouseOver(this.primaryX, this.primaryY, this.primaryW, this.dynBtnH, mouseX, mouseY)) {
                this.startLogin();
                return;
            }

            // bottom row
            final AltManager manager = AltManager.getInstance();
            if (manager.hasBaseline() && !manager.isOnBaseline(this.mc.getSession()) && !this.busy
                    && GUIUtil.mouseOver(this.btnRestoreX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY)) {
                this.restoreOriginal();
                return;
            }
            if (this.selectedUuid != null && !this.busy
                    && GUIUtil.mouseOver(this.btnRemoveX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY)) {
                disarm = false;
                final AltHistoryEntry sel = this.getSelectedEntry();
                if (sel != null && this.armOrFire("row:" + sel.getUuid())) {
                    this.deleteEntry(sel);
                }
                return;
            }
            if (!this.busy
                    && GUIUtil.mouseOver(this.btnClearX, this.bottomBtnY, this.bottomBtnW, this.dynSmallBtnH, mouseX, mouseY)) {
                disarm = false;
                if (this.armOrFire("clear")) {
                    this.clearHistory();
                }
                return;
            }

            // list row click
            if (GUIUtil.mouseOver(this.listX, this.listY, this.listW, this.listH, mouseX, mouseY)) {
                this.handleListClick(mouseX, mouseY);
            } else if (!GUIUtil.mouseOver(this.rightPaneX, this.rightPaneY, this.rightPaneW, this.rightPaneH, mouseX, mouseY)
                    && !GUIUtil.mouseOver(this.leftPaneX, this.leftPaneY, this.leftPaneW, this.leftPaneH, mouseX, mouseY)) {
                this.selectedUuid = null;
            }
        } finally {
            if (disarm) this.deleteArmedKey = null;
        }
    }

    private void handleListClick(final int mouseX, final int mouseY) {
        final List<AltHistoryEntry> filtered = this.getFilteredHistory();
        if (filtered.isEmpty()) return;

        for (int i = 0; i < filtered.size(); i++) {
            final double rowY = this.listY + 4 * this.uiScale + i * (this.rowH + this.rowGap) + this.scrollUtil.getScroll();
            if (!(mouseY >= rowY && mouseY < rowY + this.rowH)) continue;
            if (mouseY < this.listY || mouseY >= this.listY + this.listH) continue;

            final AltHistoryEntry entry = filtered.get(i);
            final long now = System.currentTimeMillis();

            if (entry.getUuid().equals(this.lastRowClickUuid) && now - this.lastRowClickAt < DOUBLE_CLICK_MS) {
                this.lastRowClickAt = 0;
                this.lastRowClickUuid = null;
                this.loginExistingEntry(entry);
                return;
            }
            this.lastRowClickAt = now;
            this.lastRowClickUuid = entry.getUuid();
            this.selectedUuid = entry.getUuid();
            this.prefillFrom(entry);
            return;
        }
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.selectedUuid != null) {
                this.selectedUuid = null;
                return;
            }
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            this.focusNextField();
            return;
        }

        if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            this.scrollUtil.setTarget(0);
            return;
        }
        if (this.cracked) {
            if (this.crackedNameField.textboxKeyTyped(typedChar, keyCode)) return;
        } else if (this.altening) {
            if (this.alteningApiKeyField.textboxKeyTyped(typedChar, keyCode)) return;
        } else {
            if (this.tokenField.textboxKeyTyped(typedChar, keyCode)) return;
        }
        if (this.proxyField.textboxKeyTyped(typedChar, keyCode)) return;
        if (this.proxyUserField.textboxKeyTyped(typedChar, keyCode)) return;
        if (this.proxyPassField.textboxKeyTyped(typedChar, keyCode)) return;

        if (keyCode == Keyboard.KEY_DELETE && this.selectedUuid != null && !this.busy) {
            final AltHistoryEntry sel = this.getSelectedEntry();
            if (sel != null && this.armOrFire("row:" + sel.getUuid())) {
                this.deleteEntry(sel);
                return;
            }
            return;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.selectedUuid == null && !this.searchField.getText().isEmpty()) {
                final List<AltHistoryEntry> filtered = this.getFilteredHistory();
                if (!filtered.isEmpty()) {
                    this.loginExistingEntry(filtered.get(0));
                    return;
                }
            }
            this.startLogin();
        }
    }

    private void focusNextField() {
        final InputField[] visible;
        if (this.cracked) {
            visible = new InputField[] { this.crackedNameField, this.proxyField, this.proxyUserField, this.proxyPassField };
        } else if (this.altening) {
            visible = new InputField[] { this.alteningApiKeyField, this.proxyField, this.proxyUserField, this.proxyPassField };
        } else {
            visible = new InputField[] { this.tokenField, this.proxyField, this.proxyUserField, this.proxyPassField };
        }
        int idx = -1;
        for (int i = 0; i < visible.length; i++) {
            if (visible[i].isFocused()) { idx = i; break; }
        }
        this.unfocusAll();
        visible[(idx + 1) % visible.length].setFocused(true);
    }

    private void unfocusAll() {
        this.tokenField.setFocused(false);
        this.alteningApiKeyField.setFocused(false);
        this.crackedNameField.setFocused(false);
        this.proxyField.setFocused(false);
        this.proxyUserField.setFocused(false);
        this.proxyPassField.setFocused(false);
        this.searchField.setFocused(false);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        try {
            final String alteningInput = this.alteningApiKeyField.getText();
            if (isAlteningApiKey(alteningInput)) {
                AltManager.getInstance().saveLastAlteningApiKey(this.mc.mcDataDir, alteningInput);
            }
        } catch (final IOException ignored) {
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // login logic

    private void startLogin() {
        if (this.busy) return;
        this.debug("startLogin mode=" + this.currentMode()
                + " selected=" + (this.selectedUuid == null ? "(none)" : this.selectedUuid)
                + " crackedField=" + this.crackedNameField.getText().trim().length()
                + " tokenField=" + this.tokenField.getText().trim().length()
                + " alteningKeyField=" + this.alteningApiKeyField.getText().trim().length());

        if (this.altening) {
            final String alteningInput = this.alteningApiKeyField.getText().trim();
            if (isAlteningApiKey(alteningInput)) {
                this.generateAlteningAccount();
            } else {
                this.loginAlteningToken(alteningInput);
            }
            return;
        }

        final boolean cracked = this.cracked;
        final String input = cracked ? this.crackedNameField.getText() : this.tokenField.getText();
        final String proxy = this.proxyField.getText();
        final String proxyUser = this.proxyUserField.getText();
        final String proxyPass = this.proxyPassField.getText();
        final String tokenInput = cracked ? "" : this.tokenField.getText().trim();
        String refreshTokenTmp = this.selectedUuid != null ? this.getSelectedRefreshToken() : null;
        if (refreshTokenTmp == null && !cracked && !tokenInput.isEmpty() && !looksLikeJwt(tokenInput)) {
            refreshTokenTmp = tokenInput; // pasted M.C… refresh token for a brand-new account
        }
        final String refreshToken = refreshTokenTmp;
        final boolean useMicrosoft = !cracked && refreshToken != null;
        this.setBusy(true, cracked ? "Logging in as cracked..." : (useMicrosoft ? "Authenticating with Microsoft..." : "Validating account..."), TEXT_SEC);

        new Thread("AltManager Login") {
            @Override
            public void run() {
                try {
                    AltManager.getInstance().captureBaselineIfNeeded(
                            GuiAltManager.this.mc.getSession(),
                            AltManager.getInstance().getActiveProxy());
                    final ProxyConfig proxyConfig = ProxyConfig.parse(proxy, proxyUser, proxyPass);
                    final Session session;
                    final String newRefreshToken;
                    if (cracked) {
                        final String name = input.trim();
                        if (name.isEmpty()) {
                            throw new IOException("Username cannot be empty for cracked login.");
                        }
                        final String offlineUuid = java.util.UUID.nameUUIDFromBytes(
                                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                        session = new Session(name, offlineUuid, "0", "legacy");
                        newRefreshToken = null;
                    } else if (useMicrosoft) {
                        final MicrosoftAuthenticator.AuthResult result = MicrosoftAuthenticator.authenticateWithRefreshToken(refreshToken);
                        session = result.session;
                        newRefreshToken = result.refreshToken;
                    } else {
                        session = GuiAltManager.this.authService.login(input);
                        newRefreshToken = null;
                    }
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AlteningService.switchToMojang();
                                GuiAltManager.this.mc.setSession(session);
                                AltManager.getInstance().activate(
                                        session, proxyConfig,
                                        GuiAltManager.this.mc.mcDataDir,
                                        cracked ? "Cracked" : (useMicrosoft ? "Logged in (Microsoft)" : "Logged in"),
                                        proxy, proxyUser, proxyPass,
                                        cracked, false, session.getToken(),
                                        useMicrosoft ? newRefreshToken : null);
                                final String via = proxyConfig == null ? "no proxy" : "via " + proxyConfig.getDisplayAddress();
                                GuiAltManager.this.setBusy(false,
                                        (cracked ? "Cracked: " : "Logged in as ") + session.getUsername() + " " + via,
                                        TEXT_SEC);
                            } catch (final IOException e) {
                                GuiAltManager.this.setBusy(false, "Login saved session but history failed: " + e.getMessage(), ERROR_CLR);
                            }
                        }
                    });
                } catch (final Exception e) {
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            GuiAltManager.this.setBusy(false, "Login failed: " + cleanMessage(e), ERROR_CLR);
                        }
                    });
                }
            }
        }.start();
    }

    private void loginExistingEntry(final AltHistoryEntry entry) {
        if (this.busy) return;
        if (entry != null && entry.isAltening()) {
            this.loginAlteningEntry(entry);
            return;
        }
        this.prefillFrom(entry);
        if (entry != null && entry.isMicrosoft() && entry.hasRefreshToken()) {
            this.setBusy(true, "Authenticating with Microsoft...", TEXT_SEC);
            new Thread("AltManager Microsoft Login") {
                @Override
                public void run() {
                    try {
                        AltManager.getInstance().captureBaselineIfNeeded(
                                GuiAltManager.this.mc.getSession(),
                                AltManager.getInstance().getActiveProxy());
                        final ProxyConfig proxyConfig = ProxyConfig.parse(
                                entry.getProxyAddress(), entry.getProxyUsername(), entry.getProxyPassword());
                        final MicrosoftAuthenticator.AuthResult result = MicrosoftAuthenticator.authenticateWithRefreshToken(entry.getRefreshToken());
                        final Session session = result.session;
                        GuiAltManager.this.runOnClientThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    AlteningService.switchToMojang();
                                    GuiAltManager.this.mc.setSession(session);
                                    AltManager.getInstance().activate(
                                            session, proxyConfig,
                                            GuiAltManager.this.mc.mcDataDir,
                                            "Logged in (Microsoft)",
                                            entry.getProxyAddress(), entry.getProxyUsername(), entry.getProxyPassword(),
                                            false, false, session.getToken(),
                                            result.refreshToken);
                                    GuiAltManager.this.setBusy(false,
                                            "Logged in as " + session.getUsername(),
                                            TEXT_SEC);
                                } catch (final IOException e) {
                                    GuiAltManager.this.setBusy(false, "Login saved session but history failed: " + e.getMessage(), ERROR_CLR);
                                }
                            }
                        });
                    } catch (final Exception e) {
                        GuiAltManager.this.runOnClientThread(new Runnable() {
                            @Override
                            public void run() {
                                GuiAltManager.this.setBusy(false, "Login failed: " + cleanMessage(e), ERROR_CLR);
                            }
                        });
                    }
                }
            }.start();
        } else {
            this.startLogin();
        }
    }

    private void generateAlteningAccount() {
        if (this.busy) return;

        final String apiKey = this.alteningApiKeyField.getText().trim();
        this.debug("generateAlteningAccount requested keyLen=" + apiKey.length()
                + " proxy=" + this.proxyField.getText().trim()
                + " proxyUserLen=" + this.proxyUserField.getText().trim().length());
        if (apiKey.isEmpty()) {
            this.setBusy(false, "API key is required.", ERROR_CLR);
            return;
        }

        final String proxy = this.proxyField.getText();
        final String proxyUser = this.proxyUserField.getText();
        final String proxyPass = this.proxyPassField.getText();
        this.setBusy(true, "Generating TheAltening account...", TEXT_SEC);
        try {
            AltManager.getInstance().saveLastAlteningApiKey(this.mc.mcDataDir, apiKey);
            this.debug("saved Altening API key len=" + apiKey.length());
        } catch (final IOException ignored) {
        }

        new Thread("AltManager Altening Generate") {
            @Override
            public void run() {
                final String[] step = new String[] { "generate" };
                try {
                    AltManager.getInstance().captureBaselineIfNeeded(
                            GuiAltManager.this.mc.getSession(),
                            AltManager.getInstance().getActiveProxy());
                    final ProxyConfig proxyConfig = ProxyConfig.parse(proxy, proxyUser, proxyPass);
                    GuiAltManager.this.debug("generate thread proxyConfig="
                            + (proxyConfig == null ? "(none)" : proxyConfig.getDisplayAddress())
                            + " usernameLen=" + (proxyConfig == null || proxyConfig.getUsername() == null ? 0 : proxyConfig.getUsername().length()));
                    final AltAccount generated;
                    try {
                        generated = AlteningApiClient.generate(apiKey, proxyConfig);
                    } catch (final java.net.SocketTimeoutException timeout) {
                        GuiAltManager.this.debug("generate request timed out: " + cleanMessage(timeout));
                        throw timeout;
                    }
                    GuiAltManager.this.debug("generated account username=" + generated.getUsername()
                            + " tokenLen=" + (generated.getToken() == null ? 0 : generated.getToken().length()));
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            GuiAltManager.this.setBusy(true, "Token generated, logging in...", TEXT_SEC);
                        }
                    });
                    step[0] = "auth";
                    final Session session;
                    try {
                        session = AlteningService.login(generated, proxyConfig);
                    } catch (final java.net.SocketTimeoutException timeout) {
                        GuiAltManager.this.debug("auth request timed out: " + cleanMessage(timeout));
                        throw timeout;
                    }

                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GuiAltManager.this.mc.setSession(session);
                                AltManager.getInstance().activate(
                                        session, proxyConfig,
                                        GuiAltManager.this.mc.mcDataDir,
                                        "Generated",
                                        proxy, proxyUser, proxyPass, false, true, generated.getToken());
                                final String via = proxyConfig == null ? "no proxy" : "via " + proxyConfig.getDisplayAddress();
                                GuiAltManager.this.setBusy(false,
                                        "Generated and logged in as " + session.getUsername() + " " + via,
                                        TEXT_SEC);
                            } catch (final IOException e) {
                                GuiAltManager.this.setBusy(false, "Login saved session but history failed: " + e.getMessage(), ERROR_CLR);
                            }
                        }
                    });
                } catch (final java.net.SocketTimeoutException timeout) {
                    System.err.println("[AltManagerDebug] Altening " + step[0] + " request timed out");
                    GuiAltManager.this.debug("generate thread timed out: " + cleanMessage(timeout));
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            GuiAltManager.this.setBusy(false, "TheAltening " + step[0] + " request timed out. Try again.", ERROR_CLR);
                        }
                    });
                } catch (final Exception e) {
                    GuiAltManager.this.debug("generate thread failed type=" + e.getClass().getSimpleName() + " msg=" + cleanMessage(e));
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            GuiAltManager.this.setBusy(false, "TheAltening failed: " + cleanMessage(e), ERROR_CLR);
                        }
                    });
                }
            }
        }.start();
    }

    private void loginAlteningToken(final String token) {
        if (this.busy) return;
        if (token == null || token.trim().isEmpty()) {
            this.setBusy(false, "TheAltening token or API key is required.", ERROR_CLR);
            return;
        }

        final String cleanedToken = token.trim();
        final String proxy = this.proxyField.getText();
        final String proxyUser = this.proxyUserField.getText();
        final String proxyPass = this.proxyPassField.getText();
        this.debug("loginAlteningToken requested tokenLen=" + cleanedToken.length()
                + " proxy=" + proxy.trim()
                + " proxyUserLen=" + proxyUser.trim().length());
        this.setBusy(true, "Logging in with TheAltening token...", TEXT_SEC);

        new Thread("AltManager Altening Token Login") {
            @Override
            public void run() {
                try {
                    AltManager.getInstance().captureBaselineIfNeeded(
                            GuiAltManager.this.mc.getSession(),
                            AltManager.getInstance().getActiveProxy());
                    final ProxyConfig proxyConfig = ProxyConfig.parse(proxy, proxyUser, proxyPass);
                    final Session session = AlteningService.login(cleanedToken, proxyConfig);

                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GuiAltManager.this.mc.setSession(session);
                                AltManager.getInstance().activate(
                                        session, proxyConfig,
                                        GuiAltManager.this.mc.mcDataDir,
                                        "Logged in",
                                        proxy, proxyUser, proxyPass, false, true, cleanedToken);
                                final String via = proxyConfig == null ? "no proxy" : "via " + proxyConfig.getDisplayAddress();
                                GuiAltManager.this.setBusy(false,
                                        "Logged in as " + session.getUsername() + " " + via,
                                        TEXT_SEC);
                            } catch (final IOException e) {
                                GuiAltManager.this.setBusy(false, "Login saved session but history failed: " + e.getMessage(), ERROR_CLR);
                            }
                        }
                    });
                } catch (final Exception e) {
                    GuiAltManager.this.debug("altening token login failed type=" + e.getClass().getSimpleName() + " msg=" + cleanMessage(e));
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            GuiAltManager.this.setBusy(false, "Login failed: " + cleanMessage(e), ERROR_CLR);
                        }
                    });
                }
            }
        }.start();
    }

    private void loginAlteningEntry(final AltHistoryEntry entry) {
        if (this.busy || entry == null) return;

        final String proxy = entry.getProxyAddress();
        final String proxyUser = entry.getProxyUsername();
        final String proxyPass = entry.getProxyPassword();
        this.debug("loginAlteningEntry username=" + entry.getUsername()
                + " uuid=" + entry.getUuid()
                + " tokenLen=" + (entry.getToken() == null ? 0 : entry.getToken().length())
                + " proxy=" + proxy);
        this.setBusy(true, "Logging in as " + entry.getUsername() + "...", TEXT_SEC);

        new Thread("AltManager Altening Login") {
            @Override
            public void run() {
                try {
                    AltManager.getInstance().captureBaselineIfNeeded(
                            GuiAltManager.this.mc.getSession(),
                            AltManager.getInstance().getActiveProxy());
                    final ProxyConfig proxyConfig = ProxyConfig.parse(proxy, proxyUser, proxyPass);
                    final Session session = AlteningService.login(entry.getToken(), proxyConfig);

                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GuiAltManager.this.mc.setSession(session);
                                AltManager.getInstance().activate(
                                        session, proxyConfig,
                                        GuiAltManager.this.mc.mcDataDir,
                                        "Logged in",
                                        proxy, proxyUser, proxyPass, false, true, entry.getToken());
                                final String via = proxyConfig == null ? "no proxy" : "via " + proxyConfig.getDisplayAddress();
                                GuiAltManager.this.setBusy(false,
                                        "Logged in as " + session.getUsername() + " " + via,
                                        TEXT_SEC);
                            } catch (final IOException e) {
                                GuiAltManager.this.setBusy(false, "Login saved session but history failed: " + e.getMessage(), ERROR_CLR);
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (e instanceof java.net.SocketTimeoutException) {
                        System.err.println("[AltManagerDebug] Altening auth request timed out");
                    }
                    GuiAltManager.this.debug("altening relog failed type=" + e.getClass().getSimpleName() + " msg=" + cleanMessage(e));
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            GuiAltManager.this.setBusy(false, "Login failed: " + cleanMessage(e), ERROR_CLR);
                        }
                    });
                }
            }
        }.start();
    }

    // A JWT (eyJ..., two dots) is a ready-to-use MS bearer token; anything else pasted is a refresh token.
    private static boolean looksLikeJwt(final String value) {
        if (value == null || !value.startsWith("eyJ")) {
            return false;
        }
        int dots = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '.') {
                dots++;
            }
        }
        return dots >= 2;
    }

    private static boolean isAlteningApiKey(final String value) {
        return value != null && value.trim().startsWith("api-");
    }

    private String getSelectedRefreshToken() {
        if (this.selectedUuid == null) {
            return null;
        }
        for (final AltHistoryEntry e : AltManager.getInstance().getHistory()) {
            if (e.getUuid().equals(this.selectedUuid)) {
                return e.hasRefreshToken() ? e.getRefreshToken() : null;
            }
        }
        return null;
    }

    private void prefillFrom(final AltHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        this.debug("prefillFrom username=" + entry.getUsername()
                + " cracked=" + entry.isCracked()
                + " altening=" + entry.isAltening()
                + " microsoft=" + entry.isMicrosoft()
                + " tokenLen=" + (entry.getToken() == null ? 0 : entry.getToken().length())
                + " refreshTokenLen=" + (entry.hasRefreshToken() ? entry.getRefreshToken().length() : 0));
        this.cracked = entry.isCracked();
        this.altening = entry.isAltening();
        if (entry.isCracked()) {
            this.crackedNameField.setText(entry.getUsername());
        } else if (entry.isAltening()) {
            this.alteningApiKeyField.setText(entry.getToken());
        } else if (!entry.isAltening()) {
            this.tokenField.setText(entry.hasRefreshToken() ? entry.getRefreshToken() : entry.getToken());
        }
        this.proxyField.setText(entry.getProxyAddress());
        this.proxyUserField.setText(entry.getProxyUsername());
        this.proxyPassField.setText(entry.getProxyPassword());
    }

    // Device code flow

    private void startDeviceCodeFlow() {
        if (this.busy || this.deviceCodeFlowActive) {
            return;
        }
        this.deviceCodeFlowActive = true;
        this.deviceCodeInfo = null;
        this.deviceCodePollStatus = null;
        this.deviceCodePollDone = false;
        this.deviceCodePollSuccess = false;
        this.deviceCodeResultToken = null;
        this.deviceCodeResultRefreshToken = null;

        new Thread("DeviceCodeFlow") {
            @Override
            public void run() {
                try {
                    // start device code flow
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceCodePollStatus = "Requesting device code...";
                        }
                    });

                    final MicrosoftAuthenticator.DeviceCodeInfo info = MicrosoftAuthenticator.startDeviceCodeFlow();
                    deviceCodeInfo = info;
                    deviceCodePollStatus = null;

                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            // no-op, status clears when device code info appears
                        }
                    });

                    // poll for authorization
                    final MicrosoftAuthenticator.MsTokenResponse msTokens = MicrosoftAuthenticator.pollDeviceCode(
                            info.deviceCode, info.interval, info.expiresIn);
                    deviceCodeInfo = info; // keep info for UI (the URL + code)

                    if (msTokens.refreshToken == null) {
                        throw new RuntimeException("No refresh_token in device code response");
                    }

                    // full auth chain using the device code's access token
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceCodePollStatus = "Completing authentication...";
                        }
                    });

                    final MicrosoftAuthenticator.AuthResult result = MicrosoftAuthenticator.authenticateWithMsToken(
                            msTokens.accessToken, msTokens.refreshToken);

                    deviceCodePollDone = true;
                    deviceCodePollSuccess = true;
                    deviceCodeResultToken = result.session.getToken();
                    deviceCodeResultRefreshToken = result.refreshToken;

                    // log in on the client thread
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final Session session = result.session;
                                AlteningService.switchToMojang();
                                GuiAltManager.this.mc.setSession(session);
                                AltManager.getInstance().activate(
                                        session, null,
                                        GuiAltManager.this.mc.mcDataDir,
                                        "Logged in (Microsoft)",
                                        "", "", "",
                                        false, false, session.getToken(),
                                        result.refreshToken);
                                GuiAltManager.this.setBusy(false, "Logged in as " + session.getUsername(), TEXT_SEC);
                                // close the device code pane after success
                                deviceCodeFlowActive = false;
                            } catch (final IOException e) {
                                GuiAltManager.this.setBusy(false, "Login saved session but history failed: " + e.getMessage(), ERROR_CLR);
                                deviceCodeFlowActive = false;
                            }
                        }
                    });
                } catch (final Exception e) {
                    GuiAltManager.this.runOnClientThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceCodePollDone = true;
                            GuiAltManager.this.setBusy(false, "Device code flow failed: " + cleanMessage(e), ERROR_CLR);
                            // let the user see the error, then close the pane on next click
                        }
                    });
                }
            }
        }.start();
    }

    private void cancelDeviceCodeFlow() {
        if (this.deviceCodePollThread != null && this.deviceCodePollThread.isAlive()) {
            this.deviceCodePollThread.interrupt();
        }
        this.deviceCodeFlowActive = false;
        this.deviceCodeInfo = null;
        this.deviceCodePollStatus = null;
        this.deviceCodePollDone = false;
        this.deviceCodePollSuccess = false;
        this.deviceCodeResultToken = null;
        this.deviceCodeResultRefreshToken = null;
        this.deviceCodePollThread = null;
        this.setBusy(false, "", TEXT_SEC);
    }

    private void deleteEntry(final AltHistoryEntry entry) {
        try {
            if (AltManager.getInstance().removeHistoryEntry(this.mc.mcDataDir, entry.getUuid())) {
                this.setBusy(false, "Removed " + entry.getUsername(), TEXT_SEC);
            }
        } catch (final IOException e) {
            this.setBusy(false, "Remove failed: " + e.getMessage(), ERROR_CLR);
        }
        this.selectedUuid = null;
    }

    private void restoreOriginal() {
        if (this.busy) return;
        final AltManager manager = AltManager.getInstance();
        if (!manager.hasBaseline() || manager.isOnBaseline(this.mc.getSession())) return;

        if (manager.restoreBaseline(this.mc)) {
            this.setBusy(false, "Restored " + this.mc.getSession().getUsername(), TEXT_SEC);
        } else {
            this.setBusy(false, "Restore failed", ERROR_CLR);
        }
    }

    private void clearHistory() {
        if (this.busy) return;
        try {
            AltManager.getInstance().clearHistory(this.mc.mcDataDir);
            this.setBusy(false, "History cleared", TEXT_SEC);
            this.selectedUuid = null;
        } catch (final IOException e) {
            this.setBusy(false, "Clear failed: " + e.getMessage(), ERROR_CLR);
        }
    }

    private static String generateRandomName() {
        final String[] prefixes = {
            "xX", "ii", "Craft", "Night", "Dark", "Fire", "Ice", "Blaze", "Storm", "Shadow",
            "Ghost", "Wolf", "Dragon", "Pixel", "Nova", "Aero", "Volt", "Frost", "Ember", "Lunar",
            "Solar", "Omega", "Alpha", "Neon", "Cyber", "Hyper", "Crypto", "Echo", "Ion", "Void"
        };
        final String[] suffixes = {
            "Wolf", "Lord", "King", "Pro", "MC", "Craft", "Xx", "Boy", "Girl", "God",
            "Warrior", "Knight", "Hunter", "Slayer", "Ninja", "Gamer", "Master", "One", "HD", "FTW",
            "Boss", "Star", "Fury", "Blade", "Core", "Dash", "Byte", "Flux", "Hawk", "PvP"
        };
        final Random rng = new Random();
        final String name = prefixes[rng.nextInt(prefixes.length)]
                + suffixes[rng.nextInt(suffixes.length)]
                + (100 + rng.nextInt(900));
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    // arm-then-fire confirm helper

    private boolean armOrFire(final String key) {
        if (key == null) return false;
        final long now = System.currentTimeMillis();
        if (key.equals(this.deleteArmedKey) && now - this.deleteArmedAt < DELETE_ARM_MS) {
            this.deleteArmedKey = null;
            return true;
        }
        this.deleteArmedKey = key;
        this.deleteArmedAt = now;
        return false;
    }

    private boolean isArmed(final String key) {
        return key != null && key.equals(this.deleteArmedKey)
                && System.currentTimeMillis() - this.deleteArmedAt < DELETE_ARM_MS;
    }

    // list helpers

    private List<AltHistoryEntry> getFilteredHistory() {
        final List<AltHistoryEntry> all = AltManager.getInstance().getHistory();
        final String q = this.searchField.getText().trim().toLowerCase();
        if (q.isEmpty()) return all;
        final List<AltHistoryEntry> filtered = new ArrayList<AltHistoryEntry>();
        for (final AltHistoryEntry e : all) {
            if (e.getUsername() != null && e.getUsername().toLowerCase().contains(q)) filtered.add(e);
        }
        return filtered;
    }

    private AltHistoryEntry getSelectedEntry() {
        if (this.selectedUuid == null) return null;
        for (final AltHistoryEntry e : AltManager.getInstance().getHistory()) {
            if (this.selectedUuid.equals(e.getUuid())) return e;
        }
        return null;
    }

    private void ensureSelectionValid() {
        if (this.selectedUuid != null && this.getSelectedEntry() == null) {
            this.selectedUuid = null;
        }
    }

    // misc helpers

    private void runOnClientThread(final Runnable runnable) {
        if (this.mc.isCallingFromMinecraftThread()) {
            runnable.run();
        } else {
            this.mc.addScheduledTask(runnable);
        }
    }

    private void setBusy(final boolean busy, final String status, final int statusColor) {
        this.busy = busy;
        this.status = status;
        this.statusColor = statusColor;
    }

    /** Font size after the responsive uiScale (floored at 7 to stay legible). */
    private int scaledFont(final int size) {
        return Math.max(7, (int) Math.round(size * this.uiScale));
    }

    private void drawText(final int size, final String s, final double x, final double y, final int color) {
        GuiText.draw(scaledFont(size), s, x, y, color);
    }

    private void drawTextCentered(final int size, final String s, final double cx, final double y, final int color) {
        GuiText.drawCentered(scaledFont(size), s, cx, y, color);
    }

    private int stringWidth(final int size, final String s) {
        return GuiText.width(scaledFont(size), s);
    }

    private float fontH(final int size) {
        return GuiText.height(scaledFont(size));
    }

    private String endTruncate(final String text, final int maxWidth, final int fontSize) {
        if (text == null || text.isEmpty()) return "";
        if (stringWidth(fontSize, text) <= maxWidth) return text;
        String shown = text;
        while (shown.length() > 1 && stringWidth(fontSize, shown + "\u2026") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "\u2026";
    }

    private static String cleanMessage(final Exception e) {
        final String msg = e.getMessage();
        return msg == null || msg.trim().isEmpty() ? e.getClass().getSimpleName() : msg;
    }

    private String currentMode() {
        if (this.cracked) {
            return "cracked";
        }
        if (this.altening) {
            return "altening";
        }
        return "microsoft";
    }

    private void debug(final String message) {
        if (DEBUG) {
            final String line = "[AltManagerDebug] " + message;
            System.out.println(line);
            LOG.info(line);
        }
    }

    private static int lerpColor(final int a, final int b, final float t) {
        final float k = Math.max(0f, Math.min(1f, t));
        final int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        final int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        final int rr = (int) (ar + (br - ar) * k);
        final int rg = (int) (ag + (bg - ag) * k);
        final int rb = (int) (ab + (bb - ab) * k);
        final int ra = (int) (aa + (ba - aa) * k);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
