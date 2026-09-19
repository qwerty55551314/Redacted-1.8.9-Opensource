package redacted.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import redacted.client.util.entity.EntityUtil;

public class ESPUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static double[] getInterpolatedPos(Entity entity, float partialTicks) {
        return new double[]{
            entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX,
            entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY,
            entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ
        };
    }

    /** Caller-buffered variant of {@link #getInterpolatedPos} for per-entity per-frame loops. */
    public static double[] getInterpolatedPos(Entity entity, float partialTicks, double[] out) {
        out[0] = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        out[1] = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY;
        out[2] = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;
        return out;
    }

    public static AxisAlignedBB getInterpolatedBoundingBox(Entity entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double offsetX = x - entity.posX;
        double offsetY = y - entity.posY;
        double offsetZ = z - entity.posZ;
        return box.offset(offsetX, offsetY, offsetZ)
                .offset(-mc.getRenderManager().viewerPosX, -mc.getRenderManager().viewerPosY, -mc.getRenderManager().viewerPosZ);
    }

    // unified gate for EntityESP's shared global toggles -- players/mobs/animals/npcs as separate axes
    // plus invisibles. the local player is never a target, in any camera mode.
    public static boolean shouldRender(Entity entity, boolean players, boolean mobs, boolean animals, boolean npcs, boolean invisibles) {
        if (entity == mc.thePlayer) return false;
        return matchesFilter(entity, players, mobs, animals, invisibles, npcs);
    }

    // GUI item icons inside a nametag-style billboard. renderItemAndEffectIntoGUI adds 50, then
    // translates by 100 + zLevel - -150 nets out to the billboard plane. The finally re-arms the
    // billboard text state (no lighting/depth, blend on) that GUI item lighting clobbers, and
    // re-enables alpha test - renderItemIntoGUI disables it on exit, which would otherwise black out
    // the first-person hand rendered right after Render3DEvent.
    public static void drawBillboardItems(Runnable draw) {
        float oldZLevel = mc.getRenderItem().zLevel;
        try {
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().zLevel = -150.0F;
            draw.run();
        }
        finally {
            mc.getRenderItem().zLevel = oldZLevel;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableAlpha();
        }
    }

    private static boolean matchesFilter(Entity entity, boolean players, boolean mobs, boolean animals, boolean invisibles, boolean npcs) {
        if (entity.isDead) return false;
        if (entity.isInvisible() && !invisibles) return false;
        if (!(entity instanceof EntityLivingBase)) return false;

        EntityLivingBase living = (EntityLivingBase) entity;
        if (players && entity instanceof EntityPlayer && !EntityUtil.isNpc(living)) return true;
        if (npcs && EntityUtil.isNpc(living)) return true;
        if (mobs && entity instanceof EntityMob) return true;
        return animals && entity instanceof EntityAnimal;
    }
}
