package redacted.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import redacted.client.module.impl.combat.Team;

import java.awt.Color;
import java.util.Map;

public final class EntityColorUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private EntityColorUtil() {
    }

    public static Color resolve(
            Entity entity,
            String colorMode,
            int alpha,
            int customRed,
            int customGreen,
            int customBlue,
            Map<Entity, Color> randomColors) {
        return new Color(resolveARGB(entity, colorMode, alpha, customRed, customGreen, customBlue, randomColors), true);
    }

    /** Packed-ARGB twin of {@link #resolve}; hot per-entity render paths use this to avoid Color churn. */
    public static int resolveARGB(
            Entity entity,
            String colorMode,
            int alpha,
            int customRed,
            int customGreen,
            int customBlue,
            Map<Entity, Color> randomColors) {
        if (Team.isTeam(entity)) {
            return ColorUtil.withAlpha(Team.getTeamColor().getRGB(), alpha);
        }

        switch (colorMode) {
            case "Custom":
                return (alpha << 24) | (customRed << 16) | (customGreen << 8) | customBlue;
            case "Random":
                Color c = randomColors.computeIfAbsent(entity, e -> ColorUtil.randomHue());
                return ColorUtil.withAlpha(c.getRGB(), alpha);
            case "Sync":
                return ColorUtil.withAlpha(ColorUtil.sync().getRGB(), alpha);
            case "Team":
                if (entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;
                    if (player.isOnSameTeam(mc.thePlayer)) {
                        return ColorUtil.withAlpha(0x00FF00, alpha);
                    }
                    return ColorUtil.withAlpha(0xFF0000, alpha);
                }
                return ColorUtil.withAlpha(0xFFFF00, alpha);
            default:
                if (entity instanceof EntityPlayer) return ColorUtil.withAlpha(0xFF0000, alpha);
                if (entity instanceof EntityMob) return ColorUtil.withAlpha(0xFF8000, alpha);
                return ColorUtil.withAlpha(0x00FF00, alpha);
        }
    }
}
