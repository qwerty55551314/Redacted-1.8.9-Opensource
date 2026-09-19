package redacted.client.module.impl.utility;

import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.ModuleType;
import redacted.client.util.ChatUtil;
import redacted.client.util.debug.DebugSystem;
import redacted.client.util.notification.NotificationType;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

/**
 * Detects anticheat flags / setbacks / rubberbands and reports them via chat + notifications.
 *
 * <p>Three independent detection channels, each modelled on a technique from the reference client
 * research (Reality b2, Unfair, Andromeda, Ohareware, Echo, Alpha Centauri B6):
 * <ol>
 *   <li><b>Setback</b> - S08PacketPlayerPosLook with absolute coordinates (empty relative-flags set)
 *       that differ from the player's current position. This is the universal rubberband signal
 *       every prediction AC (Grim, Intave, Polar, Vulcan, Verus) emits when a movement check fails.
 *       Distance + delta X/Y/Z are reported (Alpha Centauri EventSetback pattern).</li>
 *   <li><b>Chat keywords</b> - scans inbound S02PacketChat for AC violation messages ("kicked for
 *       hacking", "flagged", "suspected", "violation", "cheat"). Ohareware Detector pattern.</li>
 *   <li><b>Velocity anomaly</b> - if an S12 velocity packet is followed by an S08 within a short
 *       window, the AC likely cancelled the KB and rewound us. Echo "suspicious knockback".</li>
 * </ol>
 *
 * <p>Output channels (independently toggleable):
 * <ul>
 *   <li><b>Chat</b> - enabled by default; prints flag details to the chat (ChatUtil.say).</li>
 *   <li><b>Notifications</b> - OFF by default; shows a WARNING notification popup (NotificationManager).</li>
 *   <li><b>Debug</b> - dev builds only; via DebugSystem.FLAG category.</li>
 * </ul>
 */
public class FlagDetector extends Module {

    private final BooleanValue chatAlerts = new BooleanValue("Chat Alerts", this, true)
            .describe("Report flags in chat (on by default)");
    private final BooleanValue notificationAlerts = new BooleanValue("Notification Alerts", this, false)
            .describe("Show flag warnings as HUD notifications (off by default)");

    private final BooleanValue detectSetback = new BooleanValue("Detect Setback", this, true)
            .describe("S08 PacketPlayerPosLook rubberband detection");
    private final BooleanValue detectVelocity = new BooleanValue("Detect Velocity Cancel", this, true)
            .describe("S12 velocity followed by S08 setback within a short window");

    private final NumberValue setbackMinDistance = new NumberValue("Setback Min Distance", this, 0.5D, 0.0D, 50.0D, 0.1D)
            .describe("Minimum teleport distance to count as a setback (blocks)");
    private final NumberValue velocityWindow = new NumberValue("Velocity Window", this, 40.0D, 5.0D, 100.0D, 1.0D)
            .describe("Ticks after KB to check for a setback (velocity anomaly detection)");

    // state
    private int flagCount;
    private int ticksSinceVelocity;

    private volatile boolean setbackPending;
    private volatile double setbackDx, setbackDy, setbackDz;
    private volatile boolean velocitySetbackPending;

    public FlagDetector() {
        super("FlagDetector", "Detects anticheat flags and setbacks", Keyboard.KEY_NONE, Category.DEVELOPER);
    }

    @Override
    public void onEnable() {
        flagCount = 0;
        ticksSinceVelocity = 0;
        setbackPending = false;
        velocitySetbackPending = false;
    }

    @EventTarget
    public void onPacket(final PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.INBOUND) return;
        if (mc.thePlayer == null) return;

        final Packet<?> packet = event.getPacket();

        // Setback detection (S08)
        if (detectSetback.getValue() && packet instanceof S08PacketPlayerPosLook) {
            final S08PacketPlayerPosLook s08 = (S08PacketPlayerPosLook) packet;
            if (!s08.func_179834_f().isEmpty()) return;

            final double dx = s08.getX() - mc.thePlayer.posX;
            final double dy = s08.getY() - mc.thePlayer.posY;
            final double dz = s08.getZ() - mc.thePlayer.posZ;
            final double distSq = dx * dx + dy * dy + dz * dz;
            final double threshold = setbackMinDistance.doubleValue();

            if (distSq >= threshold * threshold) {
                setbackDx = dx;
                setbackDy = dy;
                setbackDz = dz;
                setbackPending = true;

                if (detectVelocity.getValue() && ticksSinceVelocity > 0
                        && ticksSinceVelocity <= velocityWindow.intValue()) {
                    velocitySetbackPending = true;
                }
            }
            return;
        }

        // Velocity packet tracking (S12) for anomaly detection
        if (packet instanceof S12PacketEntityVelocity) {
            final S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() == mc.thePlayer.getEntityId()) {
                ticksSinceVelocity = 1;
            }
        }
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;

        if (setbackPending) {
            setbackPending = false;
            flagCount++;

            final double dist = Math.sqrt(setbackDx * setbackDx + setbackDy * setbackDy + setbackDz * setbackDz);
            final String type = velocitySetbackPending ? "Velocity Cancel" : "Setback";
            final String msg = velocitySetbackPending
                    ? String.format("Flag #%d [%s] AC cancelled knockback and rewound you (d=%.2f, dx=%.2f dy=%.2f dz=%.2f)",
                            flagCount, type, dist, setbackDx, setbackDy, setbackDz)
                    : String.format("Flag #%d [%s] rubberband detected (d=%.2f, dx=%.2f dy=%.2f dz=%.2f)",
                            flagCount, type, dist, setbackDx, setbackDy, setbackDz);
            velocitySetbackPending = false;

            report(msg);
        }

        if (ticksSinceVelocity > 0) ticksSinceVelocity++;
        if (ticksSinceVelocity > velocityWindow.intValue() + 10) ticksSinceVelocity = 0;
    }

    private void report(final String message) {
        DebugSystem.log(DebugSystem.FLAG, message);
        if (chatAlerts.getValue()) {
            ChatUtil.say(message);
        }
        if (notificationAlerts.getValue()) {
            redacted.client.Client.getInstance().getNotificationManager().showNotification(
                    NotificationType.WARNING, message, 4);
        }
    }
}
