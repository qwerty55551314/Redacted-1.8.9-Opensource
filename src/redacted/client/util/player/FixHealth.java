package redacted.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

import java.util.Map;

public final class FixHealth {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private FixHealth() {}

    // Best-effort real health for any living entity. Yourself is easy - vanilla applies the S06 stream
    // untouched, so the entity field is already the truth. For other players we lean on whatever the
    // server already exposes (tab-list / below-name hearts) before falling back to entity metadata,
    // since metadata is the channel servers tend to fake or withhold.
    public static float getHealth(final EntityLivingBase entity) {
        if (entity == null) return 0.0F;
        if (entity == mc.thePlayer) return entity.getHealth();

        final float shown = scoreboardHealth(entity);
        if (shown >= 0.0F) return shown;

        return entity.getHealth();
    }

    // Pulls health off any HEARTS-rendered scoreboard objective the server keeps for this player.
    // One scan covers both the tab-list slot and the below-name slot - they're just HEARTS objectives -
    // and even a health objective the server tracks without displaying. Score points are already on the
    // 0-20 half-heart scale (see GuiPlayerTabOverlay: hearts = ceil(points / 2)), so no conversion.
    private static float scoreboardHealth(final EntityLivingBase entity) {
        if (mc.theWorld == null || !(entity instanceof EntityPlayer)) return -1.0F;

        final Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return -1.0F;

        // read-only: getObjectivesForEntity never creates score entries (unlike getValueFromObjective)
        for (final Map.Entry<ScoreObjective, Score> entry : scoreboard.getObjectivesForEntity(entity.getName()).entrySet()) {
            if (entry.getKey().getRenderType() != IScoreObjectiveCriteria.EnumRenderType.HEARTS) continue;
            final int points = entry.getValue().getScorePoints();
            // 0 is ambiguous (real death vs. an uninitialised entry), so let metadata speak for it
            if (points > 0) return Math.min((float) points, entity.getMaxHealth());
        }
        return -1.0F;
    }
}
