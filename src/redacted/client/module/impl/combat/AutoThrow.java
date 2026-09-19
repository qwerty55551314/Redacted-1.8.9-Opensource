package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.entity.EntityUtil;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.*;
import redacted.util.RaycastUtil;
import redacted.util.RotationUtil;

// Throws at whoever the SERVER sees us aiming at -- a module holding the rotation wire (SilentAura,
// SilentAim, Scaffold) means we read its spoofed yaw/pitch, never the camera.
//
// The rod is a knockback tool, and EntityFishHook.onUpdate deletes the hook the moment the angler
// stops holding a rod -- so a cast keeps the hand for the whole flight: cast, wait, reel, restore.
public class AutoThrow extends Module {
    private final NumberValue range = new NumberValue("Range", this, 5.0, 1.0, 6.0, 0.1)
            .describe("How far to throw at a target");
    private final NumberValue rodMinDist = new NumberValue("Rod Min Distance", this, 3.0, 1.0, 6.0, 0.5)
            .describe("Don't cast the rod at targets closer than this");
    private final NumberValue rodHookTicks = new NumberValue("Rod Hook Ticks", this, 10.0, 5.0, 20.0, 1.0)
            .describe("Reel the hook in after this many ticks -- the rod stays in your hand until it does");
    private final BooleanValue rodGroundOnly = new BooleanValue("Rod Ground Only", this, true)
            .describe("Only cast the rod while on the ground");
    private final ItemGridValue.Entry pearlEntry = new ItemGridValue.Entry("EnderPearl", new ItemStack(Items.ender_pearl), false);
    private final ItemGridValue.Entry rodEntry = new ItemGridValue.Entry("FishingRod", new ItemStack(Items.fishing_rod), true);
    private final ItemGridValue.Entry snowballEntry = new ItemGridValue.Entry("SnowBall", new ItemStack(Items.snowball), true);
    private final ItemGridValue.Entry eggEntry = new ItemGridValue.Entry("Egg", new ItemStack(Items.egg), true);
    private final ItemGridValue throwables = new ItemGridValue("Throwables", this, pearlEntry, rodEntry, snowballEntry, eggEntry)
            .describe("Pick which projectiles to throw, in hotbar order");

    private static final long BASE_DELAY_MS = 120L;
    private static final int JITTER_MS = 100;
    // ponytail: EntityFishHook.caughtEntity is server-only, so proximity is the sole hit signal we
    // get -- the server pins a hooked victim's position onto the hook and streams it to us.
    private static final double HOOK_HIT_DIST = 1.5;

    private enum Stage { IDLE, USING, HOOK_FLYING, RESTORING }

    private final StopWatch throttle = new StopWatch();
    private long throwDelayMs;
    private Stage stage = Stage.IDLE;
    private int throwSlot = -1, previousSlot = -1;
    private int hookTicks;
    private boolean hookSeen;
    private EntityLivingBase rodTarget;

    public AutoThrow() {
        super("AutoThrow", "Throws eggs, snowballs or the rod at whoever you're aiming at.", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override public void onEnable() { resetThrottle(); }

    @Override public void onDisable() { reset(); }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) { reset(); return; }
        // a GUI opening mid-switch bails and puts the slot back. HOOK_FLYING is exempt on purpose:
        // letting go of the rod there is what makes the server delete the hook.
        if (mc.currentScreen != null && stage != Stage.HOOK_FLYING) {
            if (stage != Stage.IDLE) reset();
            return;
        }

        switch (stage) {
            case IDLE: tickIdle(); break;
            case USING: tickUsing(); break;
            case HOOK_FLYING: tickHookFlying(); break;
            case RESTORING: reset(); break;
        }
    }

    private void tickIdle() {
        if (mc.thePlayer.isUsingItem() || !throttle.finished(throwDelayMs)) return;
        final int slot = findThrowableSlot();
        if (slot < 0) return;
        final EntityLivingBase aimed = findAimedTarget();
        if (aimed == null || (isRod(mc.thePlayer.inventory.getStackInSlot(slot)) && rodBlocked(aimed))) return;

        throwSlot = slot;
        previousSlot = mc.thePlayer.inventory.currentItem;
        rodTarget = aimed;
        mc.playerController.setCurrentItemAndSync(slot);   // switch this tick, use the next
        resetThrottle();
        stage = Stage.USING;
    }

    private void tickUsing() {
        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (mc.thePlayer.inventory.currentItem != throwSlot || !isEnabledThrowable(held)) { reset(); return; }
        // re-check the aim on the live server rotation, not the tick we committed on
        final EntityLivingBase aimed = findAimedTarget();
        if (aimed == null || (isRod(held) && rodBlocked(aimed))) { reset(); return; }

        final boolean rod = isRod(held);
        rodTarget = aimed;
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        resetThrottle();
        stage = rod ? Stage.HOOK_FLYING : Stage.RESTORING;   // the rod stays in hand or the hook dies
    }

    private void tickHookFlying() {
        hookTicks++;
        final EntityFishHook hook = mc.thePlayer.fishEntity;
        if (hook == null) {
            // only the server spawns the hook, so it takes a round trip to reach us. once we've
            // seen it, null means it's gone (reeled by hand, rod dropped, angler out of range).
            if (hookSeen || hookTicks > rodHookTicks.intValue()) stage = Stage.RESTORING;
            return;
        }
        hookSeen = true;
        if (!shouldReel(hook)) return;

        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        // only reel while a hook is really out -- the same C08 with no hook casts a fresh one
        if (isRod(held)) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        stage = Stage.RESTORING;
    }

    private boolean shouldReel(final EntityFishHook hook) {
        if (hookTicks > rodHookTicks.intValue()) return true;
        if (rodTarget == null || !EntityUtil.isValidTarget(rodTarget)) return true;
        final double targetDist = mc.thePlayer.getDistanceToEntity(rodTarget);
        if (targetDist > range.getValue() + 2.0) return true;
        if (hook.getDistanceToEntity(rodTarget) < HOOK_HIT_DIST) return true;   // connected
        return mc.thePlayer.getDistanceToEntity(hook) > targetDist + 2.0;       // flew past
    }

    // put the slot back and stand down. passing throwSlot as the expected current slot means a
    // player who scrolled away themselves keeps their own choice (InventoryUtil.restoreHotbarSlot).
    private void reset() {
        InventoryUtil.restoreHotbarSlot(previousSlot, throwSlot);
        stage = Stage.IDLE;
        throwSlot = previousSlot = -1;
        hookTicks = 0;
        hookSeen = false;
        rodTarget = null;
    }

    private void resetThrottle() {
        throwDelayMs = BASE_DELAY_MS + (long) Randoms.between(0.0, JITTER_MS);
        throttle.reset();
    }

    // plain hotbar order -- turn off what you don't want thrown in the Throwables grid
    private int findThrowableSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isEnabledThrowable(mc.thePlayer.inventory.getStackInSlot(slot))) return slot;
        }
        return -1;
    }

    private boolean isRod(final ItemStack stack) { return stack != null && stack.getItem() instanceof ItemFishingRod; }

    private boolean isEnabledThrowable(final ItemStack stack) {
        final Item item = stack == null ? null : stack.getItem();
        return (item instanceof ItemEnderPearl && pearlEntry.isEnabled())
                || (item instanceof ItemEgg && eggEntry.isEnabled())
                || (item instanceof ItemSnowball && snowballEntry.isEnabled())
                || (item instanceof ItemFishingRod && rodEntry.isEnabled());
    }

    // a hook already out turns the next cast into a reel, point-blank targets flop, mid-air casts look broken
    private boolean rodBlocked(final EntityLivingBase target) {
        return mc.thePlayer.fishEntity != null
                || (rodGroundOnly.getValue() && !mc.thePlayer.onGround)
                || mc.thePlayer.getDistanceToEntity(target) < rodMinDist.doubleValue();
    }

    // whoever the server sees us aiming at: the spoofed rotation while a module holds the wire,
    // otherwise the last rotation we actually reported. never the camera.
    private EntityLivingBase findAimedTarget() {
        final boolean spoofed = RotationUtil.isActive();
        final float yaw = spoofed ? RotationUtil.getServerYaw() : mc.thePlayer.getLastReportedYaw();
        final float pitch = spoofed ? RotationUtil.getServerPitch() : mc.thePlayer.getLastReportedPitch();
        final double max = range.getValue();
        EntityLivingBase best = null;
        double bestDist = Double.MAX_VALUE;
        for (final Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityPlayer)) continue;
            final EntityPlayer player = (EntityPlayer) o;
            // +1.0 slack: distance is feet-based while the ray flies from the eyes
            final double dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist >= bestDist || dist > max + 1.0) continue;
            if (!EntityUtil.isValidTarget(player) || !EntityUtil.isTypeEnabled(player, true, false, false, false)) continue;
            if (!RaycastUtil.aimRayHitsEntity(player, yaw, pitch, max)) continue;
            bestDist = dist;
            best = player;
        }
        return best;
    }
}
