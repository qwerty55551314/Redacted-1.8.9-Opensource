package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.NumberValue;
import redacted.util.CombatManager;
import redacted.util.RotationUtil;

// SilentAura's rotation half with no auto-attack: it silently aims at the nearest target and flags the
// spoof to redirect manual clicks, so the player's own left-clicks land the hit. The attack itself rides
// vanilla's clickMouse (C02 before the same-tick C03) -- no packet code here.
public class SilentAim extends Module {

    private static final float ROTATION_SPEED        = 45.0F;
    private static final float MOVING_ROTATION_SPEED = 75.0F;
    private static final int   PRIORITY = 0;

    private final NumberValue range = new NumberValue("Range", this, 3.0, 1.0, 6.0, 0.1).describe("How far to aim at a target");
    private final NumberValue fov   = new NumberValue("FOV", this, 90.0, 5.0, 360.0, 1.0).describe("Cone width to pick a target in");
    // entity types come from the shared Target Filter module (disabled filter = target everything)

    private boolean wasAiming;   // true while we own the wire -- guards reset() from wiping another module's rotation

    public SilentAim() {
        super("Silent Aim", "Silently aims at the nearest target; your own clicks land the hit",
                Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        wasAiming = false;
    }

    @Override
    public void onDisable() {
        releaseAim();
    }

    // drop our claim on the wire -- only if we actually own it, so an idle aim never wipes another
    // module's held rotation.
    private void releaseAim() {
        if (!wasAiming) return;
        RotationUtil.reset();
        wasAiming = false;
    }

    // current aim target, exposed for "Only Aura"-style gates on other modules
    public EntityLivingBase getTarget() {
        return findTarget();
    }

    @EventTarget(priority = PRIORITY)
    public void onTick(final EventTick event) {
        if (!event.isPre() || mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.currentScreen instanceof GuiInventory) { releaseAim(); return; }
        if (mc.currentScreen == null && !mc.inGameHasFocus) { releaseAim(); return; }

        final EntityLivingBase target = findTarget();
        if (target == null) { releaseAim(); return; }

        // redirectClicks=true: EntityRenderer.getMouseOver builds the spoofed interaction pick off this aim
        final float normalSpeed = isPlayerMoving() ? MOVING_ROTATION_SPEED : ROTATION_SPEED;
        final float[] rots = RotationUtil.getRotations(mc.thePlayer, target);
        RotationUtil.setRotation(rots[0], rots[1], normalSpeed, true);
        wasAiming = true;   // after the claim, so the flag never says "owned" without a held wire
    }

    private boolean isPlayerMoving() {
        if (mc.thePlayer == null) return false;
        final double mx = mc.thePlayer.motionX;
        final double mz = mc.thePlayer.motionZ;
        return (mx * mx + mz * mz) > 0.0025;
    }

    private EntityLivingBase findTarget() {
        EntityLivingBase closest = null;
        final double  maxRange   = range.getValue();
        final double  maxRangeSq = maxRange * maxRange;
        double        closestDistSq = maxRangeSq;
        final float   refYaw   = RotationUtil.isActive() ? RotationUtil.getServerYaw()   : mc.thePlayer.rotationYaw;
        final float   refPitch = RotationUtil.isActive() ? RotationUtil.getServerPitch() : mc.thePlayer.rotationPitch;
        final double  fovValue     = fov.getValue();

        // shared per-tick scan: candidates already passed isValidTarget with cached reachSq/rotation
        for (final CombatManager.Candidate c : CombatManager.candidates()) {
            final EntityLivingBase e = c.entity();
            if (!TargetFilter.allows(e)) continue;
            final double reachSq = c.reachSq();
            if (reachSq > maxRangeSq) continue;
            final float[] rot = c.rotations();
            if (RotationUtil.getRotationDifference(refYaw, refPitch, rot[0], rot[1]) > fovValue) continue;
            if (reachSq <= closestDistSq) { closestDistSq = reachSq; closest = e; }
        }
        return closest;
    }
}
