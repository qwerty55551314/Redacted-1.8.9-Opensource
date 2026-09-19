package redacted.client.module.impl.developer;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.player.MotionEvent;
import redacted.client.gui.packetlog.GuiPacketLog;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.ChatUtil;
import redacted.client.value.impl.BooleanValue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Comprehensive timed packet logger for scaffold analysis.
 * Captures: pre-motion -> C03 -> C08 -> C0B -> C09 -> C0A -> S23 -> S32 -> post-motion.
 *
 * Timing: tracks time from session start, with separate tracking for active bridging.
 * Commands: .packetlog <tower|straight|diagonal|backward|strafe|jump|stop|start|status|gui>
 */
public class PacketLogger extends Module {
    private static PacketLogger instance;

    public enum Phase { NONE, TOWER, STRAIGHT, DIAGONAL, BACKWARD, STRAFE, JUMP }
    private Phase currentPhase = Phase.NONE;

    private BufferedWriter writer;
    private long sessionStartUs;
    private long lastPacketUs;
    private long lastC08Us;
    private long trackingStartUs;  // when active bridging started
    private int packetIndex;
    private int c08Total;
    private int c08Phase;
    private int c08Tracking;       // blocks placed during active tracking
    private boolean logging;
    private boolean tracking;      // whether we're actively tracking a phase
    private int gameTick;
    private String currentFileName;

    // Packet filter (toggled via GUI): default all core packets on
    private final boolean[] packetFilter = new boolean[PACKET_TYPES.length];
    public static final String[] PACKET_TYPES = {
        "C03", "C04", "C05", "C06", "C08", "C0B", "C09", "C0A",
        "C0E", "C0F", "C10", "C11", "C13", "C17",
        "S23", "S32", "S08", "S12", "S14", "S15", "S17", "S2C"
    };

    private double lastX, lastY, lastZ;
    private float lastYaw, lastPitch;
    private boolean lastOnGround;
    private boolean lastSneaking;
    private boolean lastSprinting;
    private int lastSlot;

    public PacketLogger() {
        super("Packet Logger", "Comprehensive packet capture for scaffold analysis.", Keyboard.KEY_NONE, Category.DEVELOPER);
        instance = this;
    }

    @Override
    public void onEnable() {
        logging = false; tracking = false;
        packetIndex = 0; c08Total = 0; c08Phase = 0; c08Tracking = 0;
        currentPhase = Phase.NONE; lastX = lastY = lastZ = 0;
        lastYaw = lastPitch = 0; lastOnGround = false;
        lastSneaking = false; lastSprinting = false; lastSlot = -1;
        lastC08Us = 0; gameTick = 0; currentFileName = null;
        java.util.Arrays.fill(packetFilter, true);

        String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File dir = new File(mc.mcDataDir, "logs"); dir.mkdirs();
        File f = new File(dir, "scaffold-" + ts + ".csv");
        try {
            writer = new BufferedWriter(new FileWriter(f, StandardCharsets.UTF_8));
            writer.write(header() + "\n");
            sessionStartUs = System.nanoTime() / 1000;
            lastPacketUs = sessionStartUs;
            trackingStartUs = sessionStartUs;
            logging = true;
            currentFileName = f.getName();
        } catch (IOException e) { ChatUtil.say("§c[PacketLog] §fFailed: " + e.getMessage()); return; }
        ChatUtil.say("§a[PacketLog] §fLogging → §7" + f.getName());
        ChatUtil.say("§a[PacketLog] §fFile: §7logs/" + f.getName());
    }

    @Override
    public void onDisable() {
        endPhase();
        if (writer != null) {
            try { writer.flush(); writer.close(); } catch (IOException ignored) {}
            long dur = (System.nanoTime() / 1000 - sessionStartUs) / 1000;
            ChatUtil.say("§a[PacketLog] §f═══ SESSION COMPLETE ═══");
            ChatUtil.say("§a[PacketLog] §fPackets: §e" + packetIndex);
            ChatUtil.say("§a[PacketLog] §fBlocks placed: §e" + c08Total);
            ChatUtil.say("§a[PacketLog] §fDuration: §e" + formatDuration(dur));
            ChatUtil.say("§a[PacketLog] §fBPS: §e" + String.format("%.2f", getBps()));
            ChatUtil.say("§a[PacketLog] §fFile: §7logs/" + currentFileName);
            ChatUtil.say("§a[PacketLog] §f══════════════════════════════");
        }
        logging = false; tracking = false; writer = null;
    }

    // PRE/POST MOTION
    @EventTarget(priority = -100)
    public void onMotionPre(MotionEvent event) {
        if (!logging || !event.isPre()) return;
        gameTick++;
        writeRow("PRE", "-", 0, 0,
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, "", 0, 0, 0,
                mc.thePlayer.isSneaking(), mc.thePlayer.isSprinting(),
                mc.thePlayer.inventory.currentItem, "");
    }

    @EventTarget(priority = 100)
    public void onMotionPost(MotionEvent event) {
        if (!logging || event.isPre()) return;
        writeRow("POST", "-", 0, 0,
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.thePlayer.onGround,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, "", 0, 0, 0,
                mc.thePlayer.isSneaking(), mc.thePlayer.isSprinting(),
                mc.thePlayer.inventory.currentItem, "");
    }

    // PACKETS
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!logging || writer == null) return;
        long now = System.nanoTime() / 1000;
        double timeUs = now - sessionStartUs;
        double deltaUs = now - lastPacketUs;
        lastPacketUs = now;
        Packet<?> p = event.getPacket();
        boolean out = event.getDirection() == PacketEvent.Direction.OUTBOUND;

        if (out) {
            if (p instanceof C03PacketPlayer) {
                String t = p instanceof C03PacketPlayer.C06PacketPlayerPosLook ? "C06"
                        : p instanceof C03PacketPlayer.C04PacketPlayerPosition ? "C04"
                        : p instanceof C03PacketPlayer.C05PacketPlayerLook ? "C05" : "C03";
                if (filter(t)) logC03(p, timeUs, deltaUs);
            }
            else if (p instanceof C08PacketPlayerBlockPlacement && filter("C08")) logC08(p, timeUs, deltaUs);
            else if (p instanceof C0BPacketEntityAction && filter("C0B")) logC0B(p, timeUs, deltaUs);
            else if (p instanceof C09PacketHeldItemChange && filter("C09")) logC09(p, timeUs, deltaUs);
            else if (p instanceof C0APacketAnimation && filter("C0A")) logC0A(timeUs, deltaUs);
        } else {
            if (p instanceof S23PacketBlockChange && filter("S23")) logS23(p, timeUs, deltaUs);
            else if (p instanceof S32PacketConfirmTransaction && filter("S32")) logS32(p, timeUs, deltaUs);
        }
    }

    private boolean filter(String type) {
        for (int i = 0; i < PACKET_TYPES.length; i++) {
            if (PACKET_TYPES[i].equals(type)) return packetFilter[i];
        }
        return true;
    }

    public boolean[] getPacketFilter() { return packetFilter; }
    public static String[] getPacketTypes() { return PACKET_TYPES; }
    public void setPacketEnabled(int idx, boolean enabled) {
        if (idx >= 0 && idx < packetFilter.length) packetFilter[idx] = enabled;
    }
    public boolean isPacketEnabled(int idx) {
        return idx >= 0 && idx < packetFilter.length && packetFilter[idx];
    }

    // C03: Position/Look
    private void logC03(Packet<?> p, double timeUs, double deltaUs) {
        C03PacketPlayer c03 = (C03PacketPlayer) p;
        double x = c03.getPositionX();
        double y = c03.getPositionY();
        double z = c03.getPositionZ();
        float yaw = c03.getYaw();
        float pitch = c03.getPitch();
        boolean ground = c03.isOnGround();

        String type;
        if (p instanceof C03PacketPlayer.C06PacketPlayerPosLook) type = "C06";
        else if (p instanceof C03PacketPlayer.C04PacketPlayerPosition) type = "C04";
        else if (p instanceof C03PacketPlayer.C05PacketPlayerLook) type = "C05";
        else type = "C03";

        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        float yd = MathHelper.wrapAngleTo180_float(yaw - lastYaw);
        float pd = pitch - lastPitch;

        lastX = x; lastY = y; lastZ = z;
        lastYaw = yaw; lastPitch = pitch;
        lastOnGround = ground;

        writeRow("C0", type, timeUs, deltaUs,
                x, y, z, yaw, pitch, ground,
                dx, dy, dz, dist, yd, pd,
                0, 0, 0, "", 0, 0, 0,
                lastSneaking, lastSprinting, lastSlot, "");
    }

    // C08: Block Placement
    private void logC08(Packet<?> p, double timeUs, double deltaUs) {
        C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) p;
        BlockPos pos = c08.getPosition();
        int faceId = c08.getPlacedBlockDirection();

        double px = pos != null ? pos.getX() : 0;
        double py = pos != null ? pos.getY() : 0;
        double pz = pos != null ? pos.getZ() : 0;
        String f = (faceId >= 0 && faceId <= 5) ? EnumFacing.getFront(faceId).getName() : "?";
        double cx = c08.getPlacedBlockOffsetX();
        double cy = c08.getPlacedBlockOffsetY();
        double cz = c08.getPlacedBlockOffsetZ();
        String item = c08.getStack() != null ? c08.getStack().getDisplayName() : "null";

        c08Total++; c08Phase++;
        if (tracking) c08Tracking++;
        lastC08Us = (long) timeUs;

        writeRow("C0", "C08", timeUs, deltaUs,
                lastX, lastY, lastZ, lastYaw, lastPitch, lastOnGround,
                0, 0, 0, 0, 0, 0,
                px, py, pz, f, cx, cy, cz,
                lastSneaking, lastSprinting, lastSlot, item);
    }

    // C0B: Entity Action
    private void logC0B(Packet<?> p, double timeUs, double deltaUs) {
        C0BPacketEntityAction c0B = (C0BPacketEntityAction) p;
        String action = c0B.getAction().name();
        boolean sneak = lastSneaking, sprint = lastSprinting;
        switch (c0B.getAction()) {
            case START_SNEAKING: sneak = true; break;
            case STOP_SNEAKING: sneak = false; break;
            case START_SPRINTING: sprint = true; break;
            case STOP_SPRINTING: sprint = false; break;
        }
        lastSneaking = sneak; lastSprinting = sprint;
        writeRow("C0", "C0B", timeUs, deltaUs,
                lastX, lastY, lastZ, lastYaw, lastPitch, lastOnGround,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, "", 0, 0, 0,
                sneak, sprint, lastSlot, action);
    }

    // C09: Held Item Change
    private void logC09(Packet<?> p, double timeUs, double deltaUs) {
        C09PacketHeldItemChange c09 = (C09PacketHeldItemChange) p;
        lastSlot = c09.getSlotId();
        writeRow("C0", "C09", timeUs, deltaUs,
                lastX, lastY, lastZ, lastYaw, lastPitch, lastOnGround,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, "", 0, 0, 0,
                lastSneaking, lastSprinting, lastSlot, "slot=" + lastSlot);
    }

    // C0A: Swing
    private void logC0A(double timeUs, double deltaUs) {
        writeRow("C0", "C0A", timeUs, deltaUs,
                lastX, lastY, lastZ, lastYaw, lastPitch, lastOnGround,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, "", 0, 0, 0,
                lastSneaking, lastSprinting, lastSlot, "swing");
    }

    // S23: Server Block Change
    private void logS23(Packet<?> p, double timeUs, double deltaUs) {
        S23PacketBlockChange s23 = (S23PacketBlockChange) p;
        BlockPos pos = s23.getBlockPosition();
        writeRow("S2", "S23", timeUs, deltaUs,
                lastX, lastY, lastZ, lastYaw, lastPitch, lastOnGround,
                0, 0, 0, 0, 0, 0,
                pos != null ? pos.getX() : 0, pos != null ? pos.getY() : 0, pos != null ? pos.getZ() : 0,
                "", 0, 0, 0,
                lastSneaking, lastSprinting, lastSlot, "confirm");
    }

    // S32: Transaction
    private void logS32(Packet<?> p, double timeUs, double deltaUs) {
        S32PacketConfirmTransaction s32 = (S32PacketConfirmTransaction) p;
        writeRow("S2", "S32", timeUs, deltaUs,
                lastX, lastY, lastZ, lastYaw, lastPitch, lastOnGround,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, "", 0, 0, 0,
                lastSneaking, lastSprinting, lastSlot,
                "win=" + s32.getWindowId() + " id=" + s32.getActionNumber() + " ok=" + s32.func_148888_e());
    }

    // HELPERS
    private void writeRow(String event, String type, double timeUs, double deltaUs,
                          double x, double y, double z, float yaw, float pitch, boolean ground,
                          double dx, double dy, double dz, double dist, float yd, float pd,
                          double px, double py, double pz, String face, double cx, double cy, double cz,
                          boolean sneak, boolean sprint, int slot, String details) {
        try {
            writer.write(
                gameTick + "," + currentPhase.name() + "," + event + "," + type + ","
                + String.format("%.0f", timeUs) + "," + String.format("%.0f", deltaUs) + ","
                + String.format("%.4f", x) + "," + String.format("%.4f", y) + "," + String.format("%.4f", z) + ","
                + String.format("%.2f", yaw) + "," + String.format("%.2f", pitch) + "," + ground + ","
                + String.format("%.4f", dx) + "," + String.format("%.4f", dy) + "," + String.format("%.4f", dz) + ","
                + String.format("%.4f", dist) + "," + String.format("%.2f", yd) + "," + String.format("%.2f", pd) + ","
                + String.format("%.0f", px) + "," + String.format("%.0f", py) + "," + String.format("%.0f", pz) + "," + face + ","
                + String.format("%.3f", cx) + "," + String.format("%.3f", cy) + "," + String.format("%.3f", cz) + ","
                + sneak + "," + sprint + "," + slot + "," + details + ","
                + c08Total + "," + (lastC08Us > 0 ? String.format("%.0f", timeUs - lastC08Us) : "") + ","
                + String.format("%.2f", getBps()) + "," + String.format("%.2f", getBps()) + "\n"
            );
            packetIndex++;
        } catch (IOException ignored) {}
    }

    private String header() {
        return "tick,phase,event,type,time_us,delta_us,"
            + "posX,posY,posZ,yaw,pitch,onGround,"
            + "dx,dy,dz,dist,yawDelta,pitchDelta,"
            + "placeX,placeY,placeZ,placeFace,cursorX,cursorY,cursorZ,"
            + "sneak,sprint,slot,details,"
            + "c08_count,time_since_last_place_us,bps,cps";
    }

    // PUBLIC API
    public void toggleLogging() {
        if (logging) {
            mc.thePlayer.sendChatMessage(".packetlog stop");
        } else {
            setEnabled(true);
        }
    }

    public boolean isLogging() { return logging; }
    public boolean isTracking() { return tracking; }
    public Phase getCurrentPhase() { return currentPhase; }
    public int getBlockCount() { return c08Total; }
    public int getPacketCount() { return packetIndex; }
    public String getFileName() { return currentFileName; }

    public long getDurationMs() {
        if (!logging) return 0;
        return (System.nanoTime() / 1000 - sessionStartUs) / 1000;
    }

    public long getTrackingDurationMs() {
        if (!tracking) return 0;
        return (System.nanoTime() / 1000 - trackingStartUs) / 1000;
    }

    public double getBps() {
        long dur = getDurationMs();
        return c08Total > 1 && dur > 0 ? (c08Total - 1) * 1000.0 / dur : 0;
    }

    public double getTrackingBps() {
        long dur = getTrackingDurationMs();
        return c08Tracking > 1 && dur > 0 ? (c08Tracking - 1) * 1000.0 / dur : 0;
    }

    // COMMANDS
    public void handleCommand(String args) {
        if (!logging) { ChatUtil.say("§c[PacketLog] §fEnable module first!"); return; }
        switch (args.trim().toLowerCase()) {
            case "tower":    startPhase(Phase.TOWER);    break;
            case "straight": startPhase(Phase.STRAIGHT); break;
            case "diagonal": startPhase(Phase.DIAGONAL); break;
            case "backward": startPhase(Phase.BACKWARD); break;
            case "strafe":   startPhase(Phase.STRAFE);   break;
            case "jump":     startPhase(Phase.JUMP);      break;
            case "stop":     endPhase();                  break;
            case "start":    startTracking();             break;
            case "status":   printStatus();               break;
            case "gui":      openGui();                   break;
            default: ChatUtil.say("§c[PacketLog] §fUse: tower|straight|diagonal|backward|strafe|jump|stop|start|status|gui");
        }
    }

    private void startPhase(Phase phase) {
        endPhase(); currentPhase = phase; c08Phase = 0; c08Tracking = 0;
        tracking = true; trackingStartUs = System.nanoTime() / 1000;
        ChatUtil.say("§e[PacketLog] §f══ PHASE: §b" + phase.name() + " §f══");
        switch (phase) {
            case TOWER:    ChatUtil.say("§e[PacketLog] §fHold JUMP + bridge-assist + autoclicker. Place 10+ blocks."); break;
            case STRAIGHT: ChatUtil.say("§e[PacketLog] §fW only + bridge-assist + autoclicker. Bridge 10+ blocks."); break;
            case DIAGONAL: ChatUtil.say("§e[PacketLog] §fW+A or W+D + bridge-assist + autoclicker. Bridge 10+ blocks."); break;
            case BACKWARD: ChatUtil.say("§e[PacketLog] §fS only + bridge-assist + autoclicker. Bridge 10+ blocks."); break;
            case STRAFE:   ChatUtil.say("§e[PacketLog] §fA or D only + bridge-assist + autoclicker. Bridge 10+ blocks."); break;
            case JUMP:     ChatUtil.say("§e[PacketLog] §fBridge while JUMPING + bridge-assist + autoclicker. 10+ blocks."); break;
        }
        ChatUtil.say("§e[PacketLog] §fSay §b.packetlog stop §fto end phase.");
    }

    private void startTracking() {
        tracking = true; trackingStartUs = System.nanoTime() / 1000; c08Tracking = 0;
        ChatUtil.say("§a[PacketLog] §fTracking started — say §b.packetlog stop §fto end.");
    }

    private void endPhase() {
        if (tracking) {
            long dur = getTrackingDurationMs();
            double bps = getTrackingBps();
            ChatUtil.say("§a[PacketLog] §fPhase §b" + currentPhase.name() + " §fended — §e" + c08Tracking + " blocks§f, §e" + formatDuration(dur) + "§f, §e" + String.format("%.2f", bps) + " BPS§f");
            try { writer.write("# PHASE_END " + currentPhase.name() + " blocks=" + c08Tracking + " duration_ms=" + dur + " bps=" + String.format("%.2f", bps) + "\n"); } catch (IOException ignored) {}
        }
        currentPhase = Phase.NONE; c08Phase = 0; c08Tracking = 0; tracking = false;
    }

    private void printStatus() {
        long dur = getDurationMs();
        long trackDur = getTrackingDurationMs();
        ChatUtil.say("§e[PacketLog] §f═══ STATUS ═══");
        ChatUtil.say("§e[PacketLog] §fLogging: §a" + (logging ? "YES" : "NO"));
        ChatUtil.say("§e[PacketLog] §fTracking: §a" + (tracking ? "YES" : "NO") + " §f(§b" + currentPhase.name() + "§f)");
        ChatUtil.say("§e[PacketLog] §fSession: §e" + formatDuration(dur) + "§f, §e" + packetIndex + " packets");
        ChatUtil.say("§e[PacketLog] §fTotal blocks: §e" + c08Total + "§f, BPS: §e" + String.format("%.2f", getBps()));
        if (tracking) {
            ChatUtil.say("§e[PacketLog] §fPhase blocks: §e" + c08Tracking + "§f, Duration: §e" + formatDuration(trackDur) + "§f, BPS: §e" + String.format("%.2f", getTrackingBps()));
        }
        ChatUtil.say("§e[PacketLog] §fFile: §7logs/" + currentFileName);
        ChatUtil.say("§e[PacketLog] §f══════════════════════════════");
    }

    private void openGui() {
        mc.displayGuiScreen(new GuiPacketLog(null));
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm %ds", ms / 60000, (ms % 60000) / 1000);
    }

    public static PacketLogger getInstance() { return instance; }
}
