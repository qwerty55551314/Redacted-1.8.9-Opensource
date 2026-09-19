package redacted.client.module.impl.combat;

import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.MovementUtils;
import redacted.client.util.misc.PacketUtils;
import redacted.client.value.impl.BooleanValue;
import redacted.util.CombatManager;
import redacted.util.RotationUtil;

public class AntiFireBall extends Module {
    private static final int PRIORITY = 100;
    private static final double RANGE = 24.0;
    private static final double ATTACK = 3.0D;
    private static final float ROT_SPEED = 180.0F;

    private final BooleanValue rotate = new BooleanValue("Rotate", this, true).describe("Aim at the fireball and swing to deflect it");
    private final BooleanValue stopMotion = new BooleanValue("Stop Motion", this, true).describe("Halt your movement while a fireball closes in");

    private boolean handlingThreat;
    private boolean wasAiming;
    private EntityFireball fireball;

    public AntiFireBall() {
        super("AntiFireBall", "Deflects incoming fireballs", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onDisable() {
        release();
        handlingThreat = false;
        fireball = null;
    }

    @EventTarget(priority = PRIORITY)
    public void onTick(final EventTick event) {
        if (!event.isPre()) {
            handlingThreat = false;
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null
                || mc.currentScreen != null || !mc.inGameHasFocus) {
            release();
            handlingThreat = false;
            fireball = null;
            return;
        }

        fireball = findNearest();
        handlingThreat = fireball != null;
        if (fireball == null) {
            release();
            return;
        }

        final float[] rots = RotationUtil.getRotations(mc.thePlayer,
                fireball.posX, fireball.posY + fireball.height * 0.5D, fireball.posZ);

        if (rotate.getValue()) {
            RotationUtil.setRotation(rots[0], rots[1], ROT_SPEED);
            wasAiming = true;
            attackIfInRange();
            return;
        }

        if (stopMotion.getValue()) {
            MovementUtils.stop();
        }

        attackIfInRange();
    }

    // EntityPlayerSP.swingItem() already sends the C0A -- pairing it with an explicit
    // C0APacketAnimation was a second animation packet for the same swing, every tick. and the
    // ATTACK gate only guarded the non-rotate path, so aiming fired a C02 at anything inside
    // RANGE (24 blocks) -- reach + multi-swing + attack spam in one branch.
    private void attackIfInRange() {
        if (mc.thePlayer.getDistanceToEntity(fireball) > ATTACK) return;
        mc.thePlayer.swingItem();
        PacketUtils.sendPacket(new C02PacketUseEntity(fireball, C02PacketUseEntity.Action.ATTACK));
        // stamp the epoch so AutoClicker/Triggerbot/aura don't stack a second C02 this tick
        CombatManager.markEntityAttack();
    }

    public boolean isHandlingThreat() {
        return handlingThreat;
    }

    private EntityFireball findNearest() {
        EntityFireball best = null;
        double bestDist = RANGE * RANGE;
        for (final Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityFireball)) continue;

            final EntityFireball fireball = (EntityFireball) object;
            if (!fireball.isEntityAlive()) continue;

            if (fireball.shootingEntity == mc.thePlayer) continue;

            final double dist = mc.thePlayer.getDistanceSqToEntity(fireball);
            if (dist < bestDist) {
                bestDist = dist;
                best = fireball;
            }
        }
        return best;
    }

    private void release() {
        if (!wasAiming) return;
        RotationUtil.beginRelease(ROT_SPEED);
        wasAiming = false;
    }
}
