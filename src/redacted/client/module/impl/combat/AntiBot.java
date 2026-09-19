package redacted.client.module.impl.combat;

import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.world.WorldSettings;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.BooleanValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AntiBot extends Module {
    private static final int GROUND_GRACE_TICKS = 10;
    private static final int MOVEMENT_GRACE_TICKS = 20;
    private static final double MIN_MOVEMENT_DISTANCE = 0.05;

    private final BooleanValue tabList = new BooleanValue("Tab List", this, true).describe("Flag players missing from the tab list");
    private final BooleanValue gamemode = new BooleanValue("Gamemode", this, true).describe("Flag players with no gamemode set");
    private final BooleanValue ping = new BooleanValue("Ping", this, true).describe("Flag players with no ping");
    private final BooleanValue ground = new BooleanValue("Ground", this, true).describe("Flag players that never touch the ground");
    private final BooleanValue movement = new BooleanValue("Movement", this, true).describe("Flag players that never move");
    private final BooleanValue invisibility = new BooleanValue("Invisibility", this, false).describe("Flag invisible players as bots");
    private final BooleanValue entityId = new BooleanValue("Entity ID", this, false).describe("Flag players with a non-positive entity ID");

    private final Map<Integer, TrackingData> tracked = new HashMap<>();
    private int ticks;

    public AntiBot() {
        super("AntiBot", "Ignores fake players in combat", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onDisable() {
        reset();
    }

    public static boolean isBot(final Entity entity) {
        final AntiBot module = Client.getInstance().getModuleManager().getModule(AntiBot.class);
        return module != null && module.isEnabled() && module.flagged(entity);
    }

    // above AutoBlock (200): refresh the bot-tracking map before any combat module reads the shared
    // CombatManager candidate cache, so the cached isValidTarget result matches a live scan.
    @EventTarget(priority = 300)
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.theWorld == null || mc.thePlayer == null) {
            reset();
            return;
        }

        ticks++;
        final Set<Integer> active = new HashSet<>();

        for (final EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;

            final int id = player.getEntityId();
            active.add(id);

            TrackingData data = tracked.get(id);
            if (data == null) {
                data = new TrackingData(ticks, player.posX, player.posY, player.posZ);
                tracked.put(id, data);
            } else {
                final double dx = player.posX - data.lastX;
                final double dy = player.posY - data.lastY;
                final double dz = player.posZ - data.lastZ;
                data.totalMoved += Math.sqrt(dx * dx + dy * dy + dz * dz);
                data.lastX = player.posX;
                data.lastY = player.posY;
                data.lastZ = player.posZ;
            }

            if (player.onGround) data.everOnGround = true;
        }

        tracked.keySet().retainAll(active);
    }

    private boolean flagged(final Entity entity) {
        if (!(entity instanceof EntityPlayer) || mc.thePlayer == null) return false;
        final EntityPlayer player = (EntityPlayer) entity;
        if (player == mc.thePlayer || !player.isEntityAlive()) return false;

        final NetworkPlayerInfo info = mc.getNetHandler() == null
                ? null : mc.getNetHandler().getPlayerInfo(player.getUniqueID());

        if (tabList.getValue() && info == null) return true;
        if (gamemode.getValue() && (info == null || info.getGameType() == WorldSettings.GameType.NOT_SET)) return true;
        if (ping.getValue() && (info == null || info.getResponseTime() <= 0)) return true;

        final TrackingData data = tracked.get(player.getEntityId());
        final int age = data == null ? 0 : ticks - data.spawnTick;

        if (ground.getValue() && data != null && age >= GROUND_GRACE_TICKS && !data.everOnGround) return true;
        if (movement.getValue() && data != null && age >= MOVEMENT_GRACE_TICKS
                && data.totalMoved <= MIN_MOVEMENT_DISTANCE) return true;
        if (invisibility.getValue() && player.isPotionActive(Potion.invisibility)) return true;

        return entityId.getValue() && player.getEntityId() <= 0;
    }

    private void reset() {
        tracked.clear();
        ticks = 0;
    }

    private static final class TrackingData {
        private final int spawnTick;
        private boolean everOnGround;
        private double totalMoved;
        private double lastX, lastY, lastZ;

        private TrackingData(final int spawnTick, final double x, final double y, final double z) {
            this.spawnTick = spawnTick;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
        }
    }
}
