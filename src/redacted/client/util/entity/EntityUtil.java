package redacted.client.util.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import redacted.client.module.impl.combat.AntiBot;
import redacted.client.module.impl.combat.Team;
import redacted.util.RotationUtil;

public final class EntityUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private EntityUtil() {
    }

    public static boolean isValidTarget(final EntityLivingBase entity) {
        // dead/dying entities (death animation still running, health already 0) are unhittable
        // never valid targets for any consumer
        if (entity == mc.thePlayer || !entity.isEntityAlive()
                || entity.deathTime > 0 || entity.getHealth() <= 0.0F) return false;
        if (Team.respectScoreboard() && mc.thePlayer.getTeam() != null && mc.thePlayer.isOnSameTeam(entity)) return false;
        if (Team.isTeam(entity)) return false;
        return !AntiBot.isBot(entity);
    }

    // NPC = player-shaped but not in the server's tab list. Robust version: try the name as well
    // as the UUID (some servers' tab entries don't match the entity UUID, which made every player
    // look like an NPC and silently emptied the ESP/target filters), and with an EMPTY tab list we
    // can't tell anyone apart -- assume everyone is a real player instead of filtering them all.
    public static boolean isNpc(final EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return false;

        if (mc.getNetHandler() == null) return false;
        if (mc.getNetHandler().getPlayerInfoMap().isEmpty()) return false;
        return mc.getNetHandler().getPlayerInfo(entity.getUniqueID()) == null
                && mc.getNetHandler().getPlayerInfo(entity.getName()) == null;
    }

    // shared target-type filter for the aura/aim modules
    public static boolean isTypeEnabled(final EntityLivingBase entity, final boolean players,
                                        final boolean mobs, final boolean animals, final boolean npcs) {
        final boolean npc = isNpc(entity);
        if (!npcs && npc) return false;
        if (npcs && npc) return true;
        if (players && entity instanceof EntityPlayer) return true;
        if (mobs && entity instanceof EntityMob) return true;
        if (animals && entity instanceof EntityAnimal) return true;
        return false;
    }

    // nearest living target within range that passes the type filter and sits inside the fov cone off the
    // player's current look. selection only -- reach/raycast/packets stay the caller's job.
    public static EntityLivingBase findClosestTarget(final double range, final double fov, final boolean players,
                                                     final boolean mobs, final boolean animals, final boolean npcs) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;

        EntityLivingBase closest = null;
        double closestDist = range;

        for (final Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityLivingBase)) continue;
            final EntityLivingBase entity = (EntityLivingBase) o;

            if (!isValidTarget(entity)) continue;
            if (!isTypeEnabled(entity, players, mobs, animals, npcs)) continue;

            final double dist = mc.thePlayer.getDistanceToEntity(entity);
            if (dist > closestDist) continue;

            final float[] rots = RotationUtil.getRotations(mc.thePlayer, entity);
            if (RotationUtil.getRotationDifference(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, rots[0], rots[1]) > fov) continue;

            closestDist = dist;
            closest = entity;
        }

        return closest;
    }
}
