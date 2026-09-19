package redacted.client.gui.config;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

import redacted.client.Client;
import redacted.client.config.ClientConfigManager;
import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.GuiText;
import redacted.client.util.gui.InputField;
import redacted.client.util.gui.ScrollUtil;
import redacted.client.util.render.RenderUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// config profile manager - front-end over ClientConfigManager.save/load/list/delete/rename.
// snapshot model: load applies once, the live autosave keeps writing latest.json untouched.
// every action routes through the boolean *Profile APIs so the status bar reports real
// outcomes (the old void path claimed success even when the file was missing or the write failed).
public class GuiConfigManager extends GuiScreen {
    // palette (shared with RedactedClickGUI / GuiAltManager)
    private static final int BG_BASE      = 0xFF0A0A0A;
    private static final int BG_PANE      = 0xFF0F0F0F;
    private static final int BG_ROW       = 0xFF141414;
    private static final int BG_HOVER     = 0xFF1A1A1A;
    private static final int BG_SELECTED  = 0xFF1E1E1E;
    private static final int BG_INPUT     = 0xFF111111;
    private static final int BG_BUTTON    = 0xFF222222;
    private static final int BG_BUTTON_HV = 0xFF333333;
    private static final int BG_PRIMARY   = 0xFF2E2E2E;
    private static final int BG_PRIMARY_HV= 0xFF3D3D3D;
    private static final int BG_DANGER_HV = 0xFF3A2020;
    private static final int BORDER_DEF   = 0xFF333333;
    private static final int BORDER_FOCUS = 0xFF555555;
    private static final int BORDER_DANGER= 0xFFBB6666;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SEC     = 0xFF888888;
    private static final int TEXT_FAINT   = 0xFF555555;
    private static final int SHADOW_CLR   = 0x30000000;
    private static final int DIVIDER      = 0xFF1F1F1F;
    private static final int OK_CLR       = 0xFF66BB66;
    private static final int ERR_CLR      = 0xFFBB6666;
    private static final int ERR_TEXT_CLR = 0xFFDD8888;
    private static final int SCROLL_CLR   = 0x28FFFFFF;
    private static final int ACCENT_CLR   = 0xFFDDDDDD;

    private static final double PAD = 16;
    private static final double HEADER_H = 20;
    private static final double CLOSE_S = 16;
    private static final double SEARCH_H = 28;
    private static final double ROW_H = 30;
    private static final double ROW_GAP = 3;
    private static final double FIELD_H = 28;
    private static final double BTN_H = 28;
    private static final double BTN_GAP = 8;
    private static final double CREATE_W = 86;
    private static final double IMPORT_W = 64;
    private static final double STATUS_H = 22;
    private static final double SCROLL_W = 3;
    private static final long DOUBLE_CLICK_MS = 400L;
    private static final long REMOVE_ARM_MS = 3000L;
    private static final long STATUS_TIMEOUT_MS = 3200L;

    // "latest" is the internal live autosave file, not a user profile -- keep it out of the list and off-limits as a name
    private static final String RESERVED = "latest";

    private final GuiScreen parentScreen;
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    // window resizes re-run initGui -- only play the open zoom on the first init
    private boolean animatedIn;
    private final ScrollUtil scrollUtil = new ScrollUtil();
    private final InputField searchField = new InputField(false, "Search configs...");
    private final InputField nameField = new InputField(false, "New config name...");
    // per-row / per-button hover fades, keyed by config name or button id
    private final Map<String, Animation> hoverAnims = new HashMap<String, Animation>();

    private String selected;
    private long lastRowClickAt;
    private String lastRowClickName;
    private long removeArmedAt;
    private boolean removeArmed;

    // status bar state; type drives the severity icon, timestamp drives the auto-clear
    private enum StatusType { INFO, SUCCESS, ERROR }
    private String status = "";
    private StatusType statusType = StatusType.INFO;
    private int statusColor = TEXT_SEC;
    private long statusAt;

    private volatile boolean importing;

    // cached layout
    private double left, top, panelW, panelH;
    private double innerX, innerW;
    private double headerY, closeX, closeY, countX;
    private double searchX, searchY, searchW;
    private double listX, listY, listW, listH;
    private double nameX, nameY, nameW, nameH, createX, createW, importX, importW;
    private double btnY, btnH, btnW, loadX, updateX, renameX, removeX;
    private double stripTop;

    public GuiConfigManager(final GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.searchField.setMaxStringLength(32);
        this.nameField.setMaxStringLength(32);
        if (this.animatedIn) {
            this.openAnimation.setValue(1);
        } else {
            this.openAnimation.setValue(0);
            this.openAnimation.reset();
            this.animatedIn = true;
        }
        this.scrollUtil.setTarget(0);
        this.ensureDropRegistered();
    }

    private void updateLayout() {
        this.panelW = Math.min(this.width - 40, 500);
        this.panelH = Math.min(this.height - 40, 430);
        this.left = (this.width - this.panelW) / 2.0;
        this.top = (this.height - this.panelH) / 2.0;

        this.innerX = this.left + PAD;
        this.innerW = this.panelW - PAD * 2;

        // header: title + count pill left, close button right
        this.headerY = this.top + PAD + 2;
        this.closeX = this.left + this.panelW - PAD - CLOSE_S;
        this.closeY = this.headerY - 3;

        this.searchX = this.innerX;
        this.searchW = this.innerW;
        this.searchY = this.headerY + HEADER_H + 12;
        this.searchField.setBounds(this.searchX, this.searchY, this.searchW, SEARCH_H);
        this.searchField.setLeftPad(22);

        // status strip pinned to the panel bottom; everything above stacks up from it
        this.stripTop = this.top + this.panelH - PAD - STATUS_H;
        this.btnH = BTN_H;
        this.btnY = this.stripTop - BTN_GAP - this.btnH;
        this.btnW = (this.innerW - BTN_GAP * 3) / 4.0;
        this.loadX = this.innerX;
        this.updateX = this.loadX + this.btnW + BTN_GAP;
        this.renameX = this.updateX + this.btnW + BTN_GAP;
        this.removeX = this.renameX + this.btnW + BTN_GAP;

        this.nameH = FIELD_H;
        this.nameY = this.btnY - 10 - this.nameH;
        this.createW = CREATE_W;
        this.importW = IMPORT_W;
        this.nameW = this.innerW - this.createW - this.importW - BTN_GAP * 2;
        this.nameX = this.innerX;
        this.importX = this.nameX + this.nameW + BTN_GAP;
        this.createX = this.importX + this.importW + BTN_GAP;
        this.nameField.setBounds(this.nameX, this.nameY, this.nameW, this.nameH);

        this.listX = this.innerX;
        this.listY = this.searchY + SEARCH_H + 10;
        this.listW = this.innerW;
        this.listH = (this.nameY - 10) - this.listY;
    }

    @Override
    public void updateScreen() {
        this.searchField.updateCursorCounter();
        this.nameField.updateCursorCounter();
        this.tickStatusExpiry();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.updateLayout();
        this.ensureDropRegistered();
        this.tickStatusExpiry();
        if (this.removeArmed && System.currentTimeMillis() - this.removeArmedAt >= REMOVE_ARM_MS) {
            this.removeArmed = false;
        }

        // cover the dirt background
        RenderUtil.drawRect(0, 0, this.width, this.height, 0xCC000000);

        this.openAnimation.run(1.0);
        final double scale = this.openAnimation.getValue();

        GlStateManager.pushMatrix();
        final double cx = this.left + this.panelW / 2.0;
        final double cy = this.top + this.panelH / 2.0;
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(scale, scale, 1.0);
        GlStateManager.translate(-cx, -cy, 0);

        if (scale > 0.95) {
            RenderUtil.drawRoundedRect(this.left - 2, this.top - 2, this.panelW + 4, this.panelH + 4, 12, SHADOW_CLR);
        }
        RenderUtil.drawRoundedRect(this.left, this.top, this.panelW, this.panelH, 10, BG_BASE);
        RenderUtil.drawRoundedOutline(this.left + 1, this.top + 1, this.panelW - 2, this.panelH - 2, 9, 1.0f, 0x0DFFFFFF);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableTexture2D();

        this.drawHeader(mouseX, mouseY);
        this.drawSearchGlyph();
        this.searchField.draw();
        this.drawList(mouseX, mouseY);

        // name field + import + create
        this.nameField.draw();
        this.drawButton("import", "Import", this.importX, this.nameY, this.importW, this.nameH, mouseX, mouseY,
                !this.importing, ButtonStyle.NORMAL);
        final String nameErr = this.validate(this.nameField.getText().trim());
        this.drawButton("create", "Create", this.createX, this.nameY, this.createW, this.nameH, mouseX, mouseY,
                nameErr == null, ButtonStyle.PRIMARY);

        // action row (need a selection)
        final boolean has = this.selected != null;
        this.drawButton("load", "Load", this.loadX, this.btnY, this.btnW, this.btnH, mouseX, mouseY, has, ButtonStyle.NORMAL);
        this.drawButton("update", "Update", this.updateX, this.btnY, this.btnW, this.btnH, mouseX, mouseY, has, ButtonStyle.NORMAL);
        this.drawButton("rename", "Rename", this.renameX, this.btnY, this.btnW, this.btnH, mouseX, mouseY,
                has && !this.nameField.getText().trim().isEmpty(), ButtonStyle.NORMAL);
        this.drawButton("remove", this.removeArmed ? "Confirm remove" : "Remove",
                this.removeX, this.btnY, this.btnW, this.btnH, mouseX, mouseY, has, ButtonStyle.DANGER);

        this.drawStatusBar();

        GlStateManager.popMatrix();
    }

    private void drawHeader(final int mouseX, final int mouseY) {
        GuiText.draw(16, "Configs", this.innerX, this.headerY, TEXT_PRIMARY);

        // count pill: "N profiles", or "visible/total" while filtering
        final int total = this.totalProfiles();
        final int visible = this.getFilteredConfigs().size();
        final boolean filtering = !this.searchField.getText().trim().isEmpty();
        final String label = filtering
                ? visible + " of " + total
                : total + (total == 1 ? " profile" : " profiles");
        final int labelW = GuiText.width(10, label);
        this.countX = this.innerX + GuiText.width(16, "Configs") + 10;
        final double pillH = 15;
        final double pillY = this.headerY + (GuiText.height(16) - pillH) / 2.0 + 2;
        RenderUtil.drawRoundedRect(this.countX, pillY, labelW + 12, pillH, pillH / 2.0, BG_ROW);
        RenderUtil.drawRoundedOutline(this.countX, pillY, labelW + 12, pillH, pillH / 2.0, 1.0f, BORDER_DEF);
        GuiText.draw(10, label, this.countX + 6, pillY + (pillH - GuiText.height(10)) / 2.0 - 1, TEXT_SEC);

        // close button (same as pressing Escape)
        final boolean hov = GUIUtil.mouseOver(this.closeX, this.closeY, CLOSE_S, CLOSE_S, mouseX, mouseY);
        final double t = this.hoverT("close", hov);
        if (t > 0.01) {
            RenderUtil.drawRoundedRect(this.closeX, this.closeY, CLOSE_S, CLOSE_S, 4, withAlpha(BG_HOVER, (int) (255 * t)));
        }
        final int xClr = lerpColor(TEXT_FAINT, TEXT_PRIMARY, (float) t);
        final double cxm = this.closeX + CLOSE_S / 2.0;
        final double cym = this.closeY + CLOSE_S / 2.0;
        final double arm = 4;
        RenderUtil.drawLine(cxm - arm, cym - arm, cxm + arm, cym + arm, 1.4f, xClr);
        RenderUtil.drawLine(cxm - arm, cym + arm, cxm + arm, cym - arm, 1.4f, xClr);
    }

    // magnifier glyph inside the search field (no icon asset needed)
    private void drawSearchGlyph() {
        final double cx = this.searchX + 12;
        final double cy = this.searchY + SEARCH_H / 2.0 - 1.5;
        RenderUtil.drawCircleOutline(cx, cy, 4, 1.2f, TEXT_FAINT);
        RenderUtil.drawLine(cx + 2.8, cy + 2.8, cx + 6.2, cy + 6.2, 1.4f, TEXT_FAINT);
    }

    private void drawList(final int mouseX, final int mouseY) {
        RenderUtil.drawRoundedRect(this.listX, this.listY, this.listW, this.listH, 6, BG_INPUT);

        final List<String> configs = this.getFilteredConfigs();

        // scroll (wheel owned here, bounded to the list rect; ScrollUtil only animates)
        final int wheel = Mouse.getDWheel();
        if (wheel != 0 && GUIUtil.mouseOver(this.listX, this.listY, this.listW, this.listH, mouseX, mouseY)) {
            this.scrollUtil.setTarget(this.scrollUtil.getTarget() + (wheel > 0 ? 30 : -30));
        }
        final double contentH = Math.max(0, configs.size() * (ROW_H + ROW_GAP) - ROW_GAP);
        final double viewH = this.listH - 8;
        final double maxScroll = -Math.max(0, contentH - viewH);
        this.scrollUtil.setMax(maxScroll);
        this.scrollUtil.onRender();

        if (configs.isEmpty()) {
            final boolean filtering = !this.searchField.getText().trim().isEmpty();
            final double midX = this.listX + this.listW / 2.0;
            final double midY = this.listY + this.listH / 2.0;
            GuiText.drawCentered(13, filtering ? "No matches" : "No saved configs", midX, midY - 14, TEXT_FAINT);
            GuiText.drawCentered(11,
                    filtering ? "Try a different search" : "Drop a .json here, click Import, or create one",
                    midX, midY + 6, 0xFF3C3C3C);
            return;
        }

        // clip to the list rect once the open animation settles; scissor is window-space
        // and would desync from the zoom matrix
        final boolean clip = this.openAnimation.getValue() >= 0.99;
        if (clip) {
            final ScaledResolution sr = new ScaledResolution(this.mc);
            final int sf = Math.max(1, sr.getScaleFactor());
            final int sw = (int) Math.round(this.listW * sf);
            final int sh = (int) Math.round(this.listH * sf);
            if (sw > 0 && sh > 0) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor((int) Math.round(this.listX * sf),
                        (int) Math.round((sr.getScaledHeight() - (this.listY + this.listH)) * sf), sw, sh);
            }
        }

        final double rowX = this.listX + 8;
        final double rowW = this.listW - 16;
        for (int i = 0; i < configs.size(); i++) {
            final String name = configs.get(i);
            final double rowY = this.listY + 4 + i * (ROW_H + ROW_GAP) + this.scrollUtil.getScroll();
            if (rowY + ROW_H < this.listY - 2 || rowY > this.listY + this.listH + 2) continue;

            final boolean hover = GUIUtil.mouseOver(rowX, rowY, rowW, ROW_H, mouseX, mouseY)
                    && mouseY >= this.listY && mouseY < this.listY + this.listH;
            final boolean sel = name.equals(this.selected);
            final double t = this.hoverT("row:" + name, hover);

            final int fill = sel ? BG_SELECTED : (int) lerpColor(BG_ROW, BG_HOVER, (float) t);
            RenderUtil.drawRoundedRect(rowX, rowY, rowW, ROW_H, 5, fill);
            if (sel) {
                RenderUtil.drawRoundedOutline(rowX, rowY, rowW, ROW_H, 5, 1.0f, 0x14FFFFFF);
            }

            // selection accent bar
            if (sel) {
                RenderUtil.drawRoundedRect(rowX + 4, rowY + ROW_H / 2.0 - 6, 2.5, 12, 1.25, ACCENT_CLR);
            }

            final float nameT = sel ? 1.0f : (float) (t * 0.7);
            final int nameClr = lerpColor(TEXT_SEC, TEXT_PRIMARY, nameT);
            final double nameX = rowX + 14;
            GuiText.draw(13, endTruncate(name, rowW - 110, 13), nameX, rowY + (ROW_H - GuiText.height(13)) / 2.0, nameClr);

            // right-aligned meta: size + age
            final String meta = this.rowMeta(name);
            if (meta != null) {
                GuiText.draw(10, meta, rowX + rowW - 10 - GuiText.width(10, meta),
                        rowY + (ROW_H - GuiText.height(10)) / 2.0, TEXT_FAINT);
            }
        }
        if (clip) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        // scrollbar thumb when the content overflows
        if (maxScroll < 0 && contentH > 0) {
            final double thumbH = Math.max(18, viewH * (viewH / contentH));
            final double progress = this.scrollUtil.getScroll() / maxScroll; // 0..1
            final double thumbY = this.listY + 3 + (viewH - thumbH) * progress;
            RenderUtil.drawRoundedRect(this.listX + this.listW - 4, thumbY, SCROLL_W, thumbH, SCROLL_W / 2.0, SCROLL_CLR);
        }

        // fade strips so partial rows blend into the list edges (vertical fade, list bg base)
        RenderUtil.drawGradientRectVertical(this.listX, this.listY, this.listW, 6, BG_INPUT, 0x00111111);
        RenderUtil.drawGradientRectVertical(this.listX, this.listY + this.listH - 6, this.listW, 6, 0x00111111, BG_INPUT);
    }

    private enum ButtonStyle { NORMAL, PRIMARY, DANGER }

    private void drawButton(final String id, final String label, final double x, final double y, final double w, final double h,
                            final int mouseX, final int mouseY, final boolean enabled, final ButtonStyle style) {
        final boolean hover = enabled && GUIUtil.mouseOver(x, y, w, h, mouseX, mouseY);
        final double t = this.hoverT("btn:" + id, hover);

        int fill;
        int border = BORDER_DEF;
        int text = enabled ? TEXT_PRIMARY : TEXT_FAINT;
        switch (style) {
            case PRIMARY:
                fill = lerpColor(BG_PRIMARY, BG_PRIMARY_HV, (float) t);
                border = lerpColor(BORDER_DEF, BORDER_FOCUS, (float) t);
                break;
            case DANGER:
                // danger only shows its colour on hover, or while the remove is armed
                if (this.removeArmed && "remove".equals(id)) {
                    fill = lerpColor(BG_DANGER_HV, 0xFF4A2424, (float) Math.max(0.35f, (float) t));
                    border = BORDER_DANGER;
                    text = ERR_TEXT_CLR;
                } else {
                    fill = hover ? BG_DANGER_HV : lerpColor(BG_BUTTON, BG_DANGER_HV, (float) t);
                    border = lerpColor(BORDER_DEF, BORDER_DANGER, (float) t);
                }
                break;
            default:
                fill = lerpColor(BG_BUTTON, BG_BUTTON_HV, (float) t);
                border = lerpColor(BORDER_DEF, BORDER_FOCUS, (float) t);
                break;
        }
        if (!enabled) fill = BG_ROW;

        RenderUtil.drawRoundedRect(x, y, w, h, 5, fill);
        RenderUtil.drawRoundedOutline(x, y, w, h, 5, 1.0f, border);
        GuiText.drawCentered(12, endTruncate(label, w - 12, 12), x + w / 2.0, y + (h - GuiText.height(12)) / 2.0, text);
    }

    private void drawStatusBar() {
        // divider between the action rows and the status strip
        RenderUtil.drawRect(this.innerX, this.stripTop - 8, this.innerW, 1, DIVIDER);

        if (this.status == null || this.status.isEmpty()) {
            GuiText.draw(11, "Double-click a config to load it", this.innerX,
                    this.stripTop + (STATUS_H - GuiText.height(11)) / 2.0, TEXT_FAINT);
            return;
        }

        // severity icon
        final double icY = this.stripTop + STATUS_H / 2.0;
        switch (this.statusType) {
            case SUCCESS:
                RenderUtil.drawTick(this.innerX, icY - 4, 10, 1.6f, OK_CLR);
                break;
            case ERROR:
                RenderUtil.drawLine(this.innerX + 1, icY - 4, this.innerX + 8, icY + 3, 1.6f, ERR_CLR);
                RenderUtil.drawLine(this.innerX + 1, icY + 3, this.innerX + 8, icY - 4, 1.6f, ERR_CLR);
                break;
            default:
                RenderUtil.drawFilledCircle(this.innerX + 4, icY, 2, TEXT_SEC);
                break;
        }
        GuiText.draw(11, this.status, this.innerX + 18,
                this.stripTop + (STATUS_H - GuiText.height(11)) / 2.0, this.statusColor);
    }

    // hover fade helper: animates 0..1 for the given key, value persists per key
    private double hoverT(final String key, final boolean hovered) {
        Animation a = this.hoverAnims.get(key);
        if (a == null) {
            a = new Animation(Easing.EASE_OUT_QUAD, 140);
            a.setValue(hovered ? 1 : 0);
            this.hoverAnims.put(key, a);
        }
        a.run(hovered ? 1.0 : 0.0);
        return a.getValue();
    }

    // configs minus the reserved autosave, search-filtered, sorted
    private List<String> getFilteredConfigs() {
        final List<String> out = new ArrayList<String>();
        final String q = this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        for (final String name : Client.getInstance().getConfigManager().list()) {
            if (name.equalsIgnoreCase(RESERVED)) continue;
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(name);
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private int totalProfiles() {
        int n = 0;
        for (final String name : Client.getInstance().getConfigManager().list()) {
            if (!name.equalsIgnoreCase(RESERVED)) n++;
        }
        return n;
    }

    // "12 KB · 2h ago" for the row's right edge, or null when the file can't be resolved
    private String rowMeta(final String name) {
        final File file = Client.getInstance().getConfigManager().profileFile(name);
        if (file == null || !file.isFile()) return null;
        return formatSize(file.length()) + " \u00b7 " + formatAge(file.lastModified());
    }

    private static String formatSize(final long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String formatAge(final long mtime) {
        if (mtime <= 0) return "?";
        final long s = (System.currentTimeMillis() - mtime) / 1000L;
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        if (s < 86400 * 30) return (s / 86400) + "d ago";
        return String.format("%te %<tb %<ty", mtime);
    }

    private static String endTruncate(final String text, final double maxWidth, final int fontSize) {
        if (text == null || text.isEmpty()) return "";
        if (GuiText.width(fontSize, text) <= maxWidth) return text;
        String shown = text;
        while (shown.length() > 1 && GuiText.width(fontSize, shown + "\u2026") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown + "\u2026";
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        this.updateLayout();

        this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        this.nameField.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) return;

        if (GUIUtil.mouseOver(this.closeX, this.closeY, CLOSE_S, CLOSE_S, mouseX, mouseY)) {
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }
        if (GUIUtil.mouseOver(this.importX, this.nameY, this.importW, this.nameH, mouseX, mouseY)) {
            this.openImportDialog();
            return;
        }
        if (GUIUtil.mouseOver(this.createX, this.nameY, this.createW, this.nameH, mouseX, mouseY)) {
            this.doCreate();
            return;
        }
        if (this.selected != null && GUIUtil.mouseOver(this.loadX, this.btnY, this.btnW, this.btnH, mouseX, mouseY)) {
            this.doLoad(this.selected);
            return;
        }
        if (this.selected != null && GUIUtil.mouseOver(this.updateX, this.btnY, this.btnW, this.btnH, mouseX, mouseY)) {
            this.doUpdate();
            return;
        }
        if (this.selected != null && GUIUtil.mouseOver(this.renameX, this.btnY, this.btnW, this.btnH, mouseX, mouseY)) {
            this.doRename();
            return;
        }
        if (this.selected != null && GUIUtil.mouseOver(this.removeX, this.btnY, this.btnW, this.btnH, mouseX, mouseY)) {
            this.doRemove();
            return;
        }

        if (GUIUtil.mouseOver(this.listX, this.listY, this.listW, this.listH, mouseX, mouseY)) {
            this.handleListClick(mouseX, mouseY);
        }
    }

    private void handleListClick(final int mouseX, final int mouseY) {
        final List<String> configs = this.getFilteredConfigs();
        for (int i = 0; i < configs.size(); i++) {
            final double rowY = this.listY + 4 + i * (ROW_H + ROW_GAP) + this.scrollUtil.getScroll();
            if (!(mouseY >= rowY && mouseY < rowY + ROW_H)) continue;
            if (mouseY < this.listY || mouseY >= this.listY + this.listH) continue;

            final String name = configs.get(i);
            final long now = System.currentTimeMillis();
            if (name.equals(this.lastRowClickName) && now - this.lastRowClickAt < DOUBLE_CLICK_MS) {
                this.lastRowClickAt = 0;
                this.lastRowClickName = null;
                this.doLoad(name);
                return;
            }
            this.lastRowClickAt = now;
            this.lastRowClickName = name;
            this.select(name);
            return;
        }
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.removeArmed) {
                this.removeArmed = false;
                this.setTransientStatus("Remove cancelled", StatusType.INFO, TEXT_SEC);
                return;
            }
            if (this.selected != null) {
                this.select(null);
                return;
            }
            this.mc.displayGuiScreen(this.parentScreen);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            final boolean toName = !this.nameField.isFocused();
            this.searchField.setFocused(!toName);
            this.nameField.setFocused(toName);
            return;
        }

        if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            this.scrollUtil.setTarget(0);
            return;
        }
        if (this.nameField.textboxKeyTyped(typedChar, keyCode)) return;

        // shortcuts only when neither field is typing
        if (this.selected != null && keyCode == Keyboard.KEY_DELETE) {
            this.doRemove();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!this.nameField.getText().trim().isEmpty() && this.validate(this.nameField.getText().trim()) == null) {
                this.doCreate();
            } else if (this.selected != null) {
                this.doLoad(this.selected);
            }
        }
    }

    // selection change disarms a pending remove
    private void select(final String name) {
        this.selected = name;
        this.removeArmed = false;
    }

    // actions

    private void doLoad(final String name) {
        if (Client.getInstance().getConfigManager().loadProfile(name)) {
            this.select(name);
            this.setTransientStatus("Loaded '" + name + "'", StatusType.SUCCESS, OK_CLR);
        } else {
            this.setTransientStatus("Load failed \u2014 '" + name + "' is missing or corrupt", StatusType.ERROR, ERR_TEXT_CLR);
        }
    }

    private void doCreate() {
        final String name = this.nameField.getText().trim();
        final String err = this.validate(name);
        if (err != null) {
            this.setTransientStatus(err, StatusType.ERROR, ERR_TEXT_CLR);
            return;
        }
        final boolean existed = this.exists(name);
        if (Client.getInstance().getConfigManager().saveProfile(name)) {
            this.select(name);
            this.nameField.setText("");
            this.setTransientStatus((existed ? "Overwrote '" : "Created '") + name + "'", StatusType.SUCCESS, OK_CLR);
        } else {
            this.setTransientStatus("Create failed \u2014 couldn't write '" + name + ".json'", StatusType.ERROR, ERR_TEXT_CLR);
        }
    }

    private void doUpdate() {
        if (this.selected == null) return;
        if (Client.getInstance().getConfigManager().saveProfile(this.selected)) {
            this.setTransientStatus("Updated '" + this.selected + "'", StatusType.SUCCESS, OK_CLR);
        } else {
            this.setTransientStatus("Update failed \u2014 couldn't write '" + this.selected + ".json'", StatusType.ERROR, ERR_TEXT_CLR);
        }
    }

    private void doRename() {
        if (this.selected == null) return;
        final String newName = this.nameField.getText().trim();
        final String err = this.validate(newName);
        if (err != null) {
            this.setTransientStatus(err, StatusType.ERROR, ERR_TEXT_CLR);
            return;
        }
        if (this.exists(newName)) {
            this.setTransientStatus("'" + newName + "' already exists", StatusType.ERROR, ERR_TEXT_CLR);
            return;
        }
        if (Client.getInstance().getConfigManager().rename(this.selected, newName)) {
            this.select(newName);
            this.nameField.setText("");
            this.setTransientStatus("Renamed to '" + newName + "'", StatusType.SUCCESS, OK_CLR);
        } else {
            this.setTransientStatus("Rename failed \u2014 '" + this.selected + "' is gone", StatusType.ERROR, ERR_TEXT_CLR);
        }
    }

    private void doRemove() {
        if (this.selected == null) return;
        final long now = System.currentTimeMillis();
        // two-click arm: first press asks, second press within the window deletes
        if (!this.removeArmed) {
            this.removeArmed = true;
            this.removeArmedAt = now;
            this.setTransientStatus("Click remove again to delete '" + this.selected + "'", StatusType.INFO, ERR_TEXT_CLR);
            return;
        }
        final String name = this.selected;
        this.removeArmed = false;
        if (Client.getInstance().getConfigManager().deleteProfile(name)) {
            this.select(null);
            this.setTransientStatus("Removed '" + name + "'", StatusType.SUCCESS, TEXT_SEC);
        } else {
            this.setTransientStatus("Remove failed \u2014 '" + name + "' couldn't be deleted", StatusType.ERROR, ERR_TEXT_CLR);
        }
    }

    // opens a native file picker (off the render thread) and copies the chosen
    // .json into the configs dir so it shows up in the list.
    // returns the imported profile name, "" when the user cancelled, null on failure.
    private static String tryImportDialog() {
        try {
            final java.awt.Frame frame = new java.awt.Frame();
            final java.awt.FileDialog fd = new java.awt.FileDialog(frame, "Import config", java.awt.FileDialog.LOAD);
            fd.setFilenameFilter((java.io.File dir, final String fname) -> fname.toLowerCase(Locale.ROOT).endsWith(".json"));
            fd.setVisible(true);
            final String dir = fd.getDirectory();
            final String file = fd.getFile();
            frame.dispose();
            if (dir == null || file == null) return "";
            return Client.getInstance().getConfigManager().importConfig(new java.io.File(dir, file));
        } catch (final Throwable t) {
            return null;
        }
    }

    private void openImportDialog() {
        if (this.importing) return;
        this.importing = true;
        this.setTransientStatus("Choose a .json in the file dialog...", StatusType.INFO, TEXT_SEC);
        new Thread(() -> {
            // "" = cancelled, null = failed, otherwise the imported profile name
            final String result = tryImportDialog();
            final boolean cancelled = result != null && result.isEmpty();
            final String importedName = cancelled ? null : result;
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                this.importing = false;
                if (cancelled) {
                    this.setTransientStatus("Import cancelled", StatusType.INFO, TEXT_SEC);
                } else if (importedName == null) {
                    this.setTransientStatus("Import failed \u2014 not a usable config file", StatusType.ERROR, ERR_TEXT_CLR);
                } else {
                    this.select(importedName);
                    this.setTransientStatus("Imported '" + importedName + "'", StatusType.SUCCESS, OK_CLR);
                }
            });
        }, "ConfigImport").start();
    }

    // native OS drag-drop: GLFW delivers dropped file paths to this callback. we only
    // act while the config menu is the active screen, importing any dropped .json.
    private static final GLFWDropCallback DROP_CALLBACK = new GLFWDropCallback() {
        @Override
        public void invoke(final long window, final int count, final long names) {
            final net.minecraft.client.gui.GuiScreen cur = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
            if (!(cur instanceof GuiConfigManager)) return;
            final GuiConfigManager menu = (GuiConfigManager) cur;
            String last = null;
            for (int i = 0; i < count; i++) {
                final String path = GLFWDropCallback.getName(names, i);
                if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                final String imported = Client.getInstance().getConfigManager().importConfig(new java.io.File(path));
                if (imported != null) last = imported;
            }
            if (last != null) {
                menu.select(last);
                menu.setTransientStatus("Imported '" + last + "'", StatusType.SUCCESS, OK_CLR);
            } else {
                menu.setTransientStatus("No .json config dropped", StatusType.ERROR, ERR_TEXT_CLR);
            }
        }
    };

    private static long glfwWindow = 0;
    private static boolean dropRegistered = false;

    private void ensureDropRegistered() {
        if (dropRegistered) return;
        final long window = GLFW.glfwGetCurrentContext();
        if (window == 0) return;
        GLFW.glfwSetDropCallback(window, DROP_CALLBACK);
        glfwWindow = window;
        dropRegistered = true;
    }

    private String validate(final String name) {
        if (name == null || name.isEmpty()) return "Enter a name";
        if (name.equalsIgnoreCase(RESERVED)) return "'" + RESERVED + "' is reserved";
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return "Invalid characters";
        return null;
    }

    private boolean exists(final String name) {
        for (final String c : Client.getInstance().getConfigManager().list()) {
            if (c.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void setTransientStatus(final String text, final StatusType type, final int color) {
        this.status = text;
        this.statusType = type;
        this.statusColor = color;
        this.statusAt = System.currentTimeMillis();
    }

    // status messages self-clear so stale errors/successes don't linger forever
    private void tickStatusExpiry() {
        if (this.status != null && !this.status.isEmpty()
                && System.currentTimeMillis() - this.statusAt >= STATUS_TIMEOUT_MS) {
            this.status = "";
            this.statusType = StatusType.INFO;
        }
    }

    private static int withAlpha(final int color, final int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(final int a, final int b, final float tRaw) {
        final float t = Math.max(0f, Math.min(1f, tRaw));
        final int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        final int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        final int rr = (int) (ar + (br - ar) * t);
        final int rg = (int) (ag + (bg - ag) * t);
        final int rb = (int) (ab + (bb - ab) * t);
        final int ra = (int) (aa + (ba - aa) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
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
