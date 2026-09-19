package redacted.client.gui.packetlog;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjglx.input.Keyboard;
import redacted.client.module.impl.developer.PacketLogger;
import redacted.client.util.gui.GUIUtil;
import redacted.client.util.gui.GuiText;
import redacted.client.util.render.RenderUtil;

import java.io.IOException;

/**
 * GUI for PacketLogger - lists all packet types, shows stats, phase controls.
 * Open only via .packetlog gui command.
 */
public class GuiPacketLog extends GuiScreen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 400;

    private static final int BG_BASE      = 0xFF0A0A0A;
    private static final int BG_PANE      = 0xFF0F0F0F;
    private static final int BG_ROW       = 0xFF141414;
    private static final int BG_HOVER     = 0xFF1A1A1A;
    private static final int BG_BUTTON    = 0xFF222222;
    private static final int BG_BUTTON_HV = 0xFF333333;
    private static final int BORDER_DEF   = 0xFF333333;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SEC     = 0xFF888888;
    private static final int TEXT_FAINT   = 0xFF555555;
    private static final int ACCENT       = 0xFF6C5CE7;
    private static final int ACCENT_HV    = 0xFF8B7CF0;
    private static final int DANGER       = 0xFFE74C3C;
    private static final int DANGER_HV    = 0xFFC0392B;
    private static final int SUCCESS      = 0xFF2ECC71;
    private static final int WARN         = 0xFFF39C12;

    private static final int FONT_SM = 11;
    private static final int FONT_MD = 14;
    private static final int FONT_LG = 18;
    private static final int FONT_XL = 22;

    private double px, py, pw, ph;
    private GuiScreen parent;

    private boolean[] packetEnabled;
    private int scrollOffset = 0;

    public GuiPacketLog(GuiScreen parent) {
        this.parent = parent;
        String[] types = PacketLogger.getPacketTypes();
        packetEnabled = new boolean[types.length];
        PacketLogger logger = PacketLogger.getInstance();
        for (int i = 0; i < types.length; i++) {
            packetEnabled[i] = logger != null && logger.isPacketEnabled(i);
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        double scale = getScale();
        pw = PANEL_W;
        ph = PANEL_H;
        px = (width / scale - pw) / 2;
        py = (height / scale - ph) / 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        double scale = getScale();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1);

        // Panel background
        RenderUtil.drawRoundedRect(px, py, pw, ph, 8, BG_BASE);
        RenderUtil.drawRoundedOutline(px, py, pw, ph, 8, 1, BORDER_DEF);

        // Title
        GuiText.draw(FONT_XL, "Packet Logger", px + 14, py + 12, TEXT_PRIMARY);
        PacketLogger logger = PacketLogger.getInstance();
        boolean active = logger != null && logger.isEnabled();
        GuiText.draw(FONT_SM, active ? "§a● ACTIVE" : "§7● INACTIVE", px + pw - 80, py + 16, active ? SUCCESS : TEXT_FAINT);

        double y = py + 42;

        // START/STOP
        boolean isLogging = logger != null && logger.isLogging();
        boolean startHov = GUIUtil.mouseOver(px + 14, y, pw - 28, 30, mouseX / scale, mouseY / scale);
        RenderUtil.drawRoundedRect(px + 14, y, pw - 28, 30, 5, isLogging ? (startHov ? DANGER_HV : DANGER) : (startHov ? ACCENT_HV : ACCENT));
        GuiText.drawCentered(FONT_MD, isLogging ? "STOP LOGGING" : "START LOGGING", px + pw / 2, y + 8, TEXT_PRIMARY);
        y += 40;

        // PHASE BUTTONS
        GuiText.draw(FONT_SM, "PHASES", px + 14, y, TEXT_FAINT);
        y += 16;
        String[] phases = {"Tower", "Straight", "Diagonal", "Backward", "Strafe", "Jump"};
        double phaseW = (pw - 28 - 5 * 5) / 6.0;
        for (int i = 0; i < 6; i++) {
            double bx = px + 14 + i * (phaseW + 5);
            boolean hov = GUIUtil.mouseOver(bx, y, phaseW, 24, mouseX / scale, mouseY / scale);
            boolean phaseActive = logger != null && logger.getCurrentPhase() != null
                && logger.getCurrentPhase().name().equalsIgnoreCase(phases[i]);
            RenderUtil.drawRoundedRect(bx, y, phaseW, 24, 4, phaseActive ? ACCENT : (hov ? BG_BUTTON_HV : BG_BUTTON));
            GuiText.drawCentered(FONT_SM, phases[i], bx + phaseW / 2, y + 6, phaseActive ? TEXT_PRIMARY : TEXT_SEC);
        }
        y += 34;

        // STATS
        GuiText.draw(FONT_SM, "STATS", px + 14, y, TEXT_FAINT);
        y += 16;
        if (logger != null) {
            GuiText.draw(FONT_SM, "Blocks: " + logger.getBlockCount(), px + 14, y, TEXT_SEC);
            GuiText.draw(FONT_SM, "Packets: " + logger.getPacketCount(), px + 140, y, TEXT_SEC);
            GuiText.draw(FONT_SM, "BPS: " + String.format("%.2f", logger.getBps()), px + 260, y, TEXT_SEC);
            y += 16;
            GuiText.draw(FONT_SM, "Duration: " + formatDuration(logger.getDurationMs()), px + 14, y, TEXT_SEC);
            GuiText.draw(FONT_SM, "Phase: " + (logger.getCurrentPhase() != null ? logger.getCurrentPhase().name() : "NONE"), px + 140, y, TEXT_SEC);
            GuiText.draw(FONT_SM, "File: " + (logger.getFileName() != null ? logger.getFileName() : "none"), px + 260, y, TEXT_SEC);
        } else {
            GuiText.draw(FONT_SM, "Module not loaded", px + 14, y, TEXT_FAINT);
        }
        y += 26;

        // PACKET LIST
        GuiText.draw(FONT_SM, "PACKET FILTERS", px + 14, y, TEXT_FAINT);
        y += 16;

        double listH = py + ph - y - 14;
        double rowH = 18;
        int visibleRows = (int) (listH / rowH);

        // Clip region for scrolling
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        String[] types = PacketLogger.getPacketTypes();
        for (int i = 0; i < Math.min(types.length, visibleRows + 1); i++) {
            int idx = i + scrollOffset;
            if (idx >= types.length) break;
            double ry = y + i * rowH;
            if (ry + rowH > py + ph - 14) break;

            boolean hov = GUIUtil.mouseOver(px + 14, ry, pw - 28, rowH - 2, mouseX / scale, mouseY / scale);
            if (hov) RenderUtil.drawRoundedRect(px + 14, ry, pw - 28, rowH - 2, 3, BG_HOVER);

            // Toggle checkbox
            double cbX = px + 18;
            double cbY = ry + 3;
            double cbS = rowH - 8;
            RenderUtil.drawRoundedRect(cbX, cbY, cbS, cbS, 3, packetEnabled[idx] ? ACCENT : BG_BUTTON);
            if (packetEnabled[idx]) {
                GuiText.drawCentered(FONT_SM, "✓", cbX + cbS / 2, cbY + 1, TEXT_PRIMARY);
            }

            // Packet info
            GuiText.draw(FONT_SM, types[idx], cbX + cbS + 6, ry + 3, TEXT_PRIMARY);
            GuiText.draw(FONT_SM, packDesc(types[idx]), cbX + cbS + 50, ry + 3, TEXT_SEC);
        }

        GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) return;
        double scale = getScale();
        double mx = mouseX / scale;
        double my = mouseY / scale;

        PacketLogger logger = PacketLogger.getInstance();
        if (logger == null) return;

        double y = py + 42;

        // Start/Stop
        if (GUIUtil.mouseOver(px + 14, y, pw - 28, 30, mx, my)) {
            if (logger.isLogging()) {
                logger.handleCommand("stop");
            } else {
                logger.toggleLogging();
            }
            return;
        }
        y += 40;

        // Phase buttons
        String[] phases = {"Tower", "Straight", "Diagonal", "Backward", "Strafe", "Jump"};
        double phaseW = (pw - 28 - 5 * 5) / 6.0;
        for (int i = 0; i < 6; i++) {
            double bx = px + 14 + i * (phaseW + 5);
            if (GUIUtil.mouseOver(bx, y, phaseW, 24, mx, my)) {
                logger.handleCommand(phases[i].toLowerCase());
                return;
            }
        }
        y += 34 + 26;

        // Packet list toggles
        double listY = y + 16;
        double rowH = 18;
        String[] types2 = PacketLogger.getPacketTypes();
        for (int i = 0; i < types2.length; i++) {
            int visIdx = i - scrollOffset;
            if (visIdx < 0) continue;
            double ry = listY + visIdx * rowH;
            if (ry + rowH > py + ph - 14) break;

            double cbX = px + 18;
            double cbY = ry + 3;
            double cbS = rowH - 8;
            if (GUIUtil.mouseOver(cbX, cbY, cbS, cbS, mx, my)) {
                packetEnabled[i] = !packetEnabled[i];
                PacketLogger log = PacketLogger.getInstance();
                if (log != null) log.setPacketEnabled(i, packetEnabled[i]);
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjglx.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            double listH = ph - 120;
            double rowH = 18;
            int maxScroll = Math.max(0, PacketLogger.getPacketTypes().length - (int)(listH / rowH));
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (dWheel > 0 ? 1 : -1)));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private double getScale() {
        return Math.min(width / (double) PANEL_W, height / (double) PANEL_H);
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm %ds", ms / 60000, (ms % 60000) / 1000);
    }

    private String packDesc(String type) {
        switch (type) {
            case "C03": return "Ground";
            case "C04": return "Position";
            case "C05": return "Look";
            case "C06": return "Pos+Look";
            case "C08": return "Block Place";
            case "C0B": return "Entity Action";
            case "C09": return "Slot Change";
            case "C0A": return "Swing";
            case "C0E": return "Click Window";
            case "C0F": return "TX Confirm";
            case "C10": return "Creative";
            case "C11": return "Enchant";
            case "C13": return "Abilities";
            case "C17": return "Custom Payload";
            case "S23": return "Block Change";
            case "S32": return "TX Confirm";
            case "S08": return "Player Pos";
            case "S12": return "Entity Look";
            case "S14": return "Entity Move";
            case "S15": return "Look+Move";
            case "S17": return "Teleport";
            default: return type;
        }
    }
}
