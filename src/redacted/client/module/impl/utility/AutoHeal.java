package redacted.client.module.impl.utility;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.ItemGridValue;
import redacted.client.value.impl.RangeValue;
import redacted.util.CombatManager;
import redacted.util.RotationUtil;

import java.util.List;

// soup + splash-heal pots in one module (merge of the old AutoPot/AutoSoup). it never references the
// aura -- it just stamps CombatManager.holdWire() while a pot rotation is parked, or pauseHealing()
// while souping, and SilentAura reads those. pots win when present (instant burst), soup is the
// fallback. only the four requested settings are exposed; everything else is a constant.
public class AutoHeal extends Module {

    private final RangeValue delay  = new RangeValue("Delay", this, 100.0, 200.0, 50.0, 1000.0, 5.0).describe("Ms between heal actions");
    private final RangeValue health = new RangeValue("Health", this, 5.0, 8.0, 1.0, 10.0, 1.0).describe("Heal when HP drops under a value rolled in this range (hearts)");

    private final ItemGridValue.Entry soupEntry   = new ItemGridValue.Entry("Soup", new ItemStack(Items.mushroom_stew), true);
    private final ItemGridValue        soupGrid    = new ItemGridValue("Soup", this, soupEntry).describe("Eat soup when low");
    private final ItemGridValue.Entry potionEntry = new ItemGridValue.Entry("Splash Healing", new ItemStack(Items.potionitem, 1, 16389), true);
    private final ItemGridValue        potionGrid  = new ItemGridValue("Potions", this, potionEntry).describe("Throw splash healing pots when low");

    private static final float ROTATION_SPEED   = 180.0F;
    private static final float THROW_PITCH       = 90.0F;   // straight down -- splash lands at our feet
    private static final float PITCH_TOLERANCE   = 3.0F;
    private static final long  COMBAT_COOLDOWN_MS = 500L;
    private static final long  BOWL_DROP_MS       = 120L;   // space repeated Q-drops across the server's slot update
    private static final long  BOWL_GRACE_MS      = 300L;   // wait out the last eat's bowl before we put the weapon back
    private static final long  ACTION_TIMEOUT_MS  = 1250L;

    private static final int IDLE = 0, PREPARE = 1, SWITCHING = 2, USING = 3, RESTORING = 4;

    private final StopWatch actionTimer = new StopWatch();   // delay gate, shared (only one path runs at a time)
    private final StopWatch combatTimer = new StopWatch();
    private final StopWatch stageTimer  = new StopWatch();
    private final StopWatch bowlTimer   = new StopWatch();
    private final StopWatch refillTimer = new StopWatch();

    private long nextAction;
    private double rolledThreshold;   // re-rolled per heal cycle so the trigger point jitters like a human
    private int groundTicks;

    // potion path
    private int   potStage = IDLE;
    private int   potionSlot = -1;
    private int   potPrevSlot = -1;
    private float throwYaw;
    private int   releaseGuardTick = -1;

    // soup path
    private boolean souping;
    private int     soupPrevSlot = -1;   // weapon to flip back to
    private int     soupSlot = -1;       // the slot we switched to (notices a manual scroll-away)

    // refill path
    private GuiScreen lastInvScreen;
    private long refillDelay;

    public AutoHeal() {
        super("AutoHeal", "Eats soup and throws splash heal pots when low.", Keyboard.KEY_NONE, Category.UTILITY);
    }

    @Override
    public void onEnable() {
        resetAll();
        rerollThreshold();
    }

    @Override
    public void onDisable() {
        resetPotion(true);
        endSoup(true);
        releaseGuardTick = -1;
    }

    @EventTarget
    public void onLeftClick(final LeftClickEvent e) {
        if (e.isFromAura()) return;   // only manual hits gate healing -- aura clicks every tick
        combatTimer.reset();
    }

    @EventTarget(priority = 50)   // above SilentAura (0): the flags land before the aura reads them, after CombatManager's epoch bump
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (!hasPlayerContext()) { resetAll(); return; }

        clearExpiredReleaseGuard();

        // refill only when the user opened their survival inventory by hand
        if (mc.currentScreen instanceof GuiInventory) { tickRefill(); return; }
        lastInvScreen = null;

        // any other screen (chest / custom GUI) -- bail out of a pot throw safely, leave soup state put
        if (mc.currentScreen != null) {
            if (potStage != IDLE) resetPotion(true);
            return;
        }

        groundTicks = mc.thePlayer.onGround ? groundTicks + 1 : 0;

        // a pot throw, once begun, runs to completion before anything else
        if (potStage != IDLE) { tickPotion(); return; }

        final boolean low = mc.thePlayer.getHealth() <= rolledThreshold * 2.0;

        // healing outranks combat the moment it's needed -- stand the aura down while we're still
        // approaching the heal too (airborne from KB, combat cooldown / action delay pending), not
        // just once an episode goes live. gated on supplies so low HP with nothing left to heal
        // with never mutes the aura. tickSoup/tickPotion re-stamp their own pauses mid-episode.
        if (low && ((potionEnabled() && splashHealSlot(0, 9) != -1)
                || (soupEnabled() && findSoupSlot(0, 9) != -1))) {
            CombatManager.pauseHealing();
        }

        // mid soup episode -> keep servicing it through to restore (drops bowls, eats, winds down)
        if (souping) { tickSoup(low); return; }

        if (!low) return;

        // pots first (instant, no slowdown); soup is the fallback
        if (potionEnabled() && splashHealSlot(0, 9) != -1) {
            if (!mc.thePlayer.isUsingItem() && mc.thePlayer.onGround
                    && combatTimer.finished(COMBAT_COOLDOWN_MS) && actionTimer.finished(nextAction)) {
                beginPotion(splashHealSlot(0, 9));
                tickPotion();
            }
            return;   // potion-priority -- don't start souping just because the throw delay isn't up
        }
        if (soupEnabled() && findSoupSlot(0, 9) != -1) {
            souping = true;
            tickSoup(low);
        }
    }

    // soup

    private void tickSoup(final boolean low) {
        CombatManager.pauseHealing();   // aura stands down for the whole episode (incl. the inter-eat delay)

        if (mc.thePlayer.isUsingItem()) return;

        final ItemStack held = mc.thePlayer.inventory.getCurrentItem();

        // 1. clear a held empty bowl first -- genuine Q-drop (C07), spaced so we don't spam the update window.
        // hold the C07 until no rotation is parked (the aura releases its aim this tick off pauseHealing),
        // matching the codebase's "no world interaction while RotationUtil active" convention.
        if (held != null && held.getItem() == Items.bowl) {
            if (!RotationUtil.isActive() && bowlTimer.finished(BOWL_DROP_MS)) {
                mc.thePlayer.dropOneItem(true);
                bowlTimer.reset();
            }
            return;
        }

        // 2. still low -> keep eating
        if (low) {
            if (groundTicks <= 1) return;
            if (!combatTimer.finished(COMBAT_COOLDOWN_MS)) return;
            if (!actionTimer.finished(nextAction)) return;

            if (held != null && held.getItem() instanceof ItemSoup
                    && mc.thePlayer.inventory.currentItem == soupSlot) {
                doEat(held);
                return;
            }
            final int slot = findSoupSlot(0, 9);
            if (slot != -1) {
                soupSlot = slot;
                if (slot == mc.thePlayer.inventory.currentItem) {
                    doEat(mc.thePlayer.inventory.getCurrentItem());
                } else {
                    if (soupPrevSlot == -1) soupPrevSlot = mc.thePlayer.inventory.currentItem;
                    // setCurrentItemAndSync (not raw C09) so sendUseItem's internal sync won't dupe the C09
                    mc.playerController.setCurrentItemAndSync(slot);
                }
                return;
            }
            endSoup(true);   // out of soup -- weapon back so we can fight (refill is GUI-only)
            return;
        }

        // 3. healthy -> wind down, but hold a beat for the last eat's bowl so we don't orphan it
        if (!actionTimer.finished(BOWL_GRACE_MS)) return;
        endSoup(true);
    }

    // sendUseItem fires C08 (-1,-1,-1,255) like vanilla eating -- the server needs dir 255 to tryUseItem
    private void doEat(final ItemStack stack) {
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
        nextAction = (long) Randoms.between(delay.getRangeMin(), delay.getRangeMax());
        actionTimer.reset();
    }

    private void endSoup(final boolean restore) {
        // pass soupSlot so we only flip back if we're still on the slot we switched to (no yank if the
        // user scrolled off it themselves)
        if (restore && souping) InventoryUtil.restoreHotbarSlot(soupPrevSlot, soupSlot);
        soupPrevSlot = -1;
        soupSlot = -1;
        souping = false;
        rerollThreshold();
    }

    private int findSoupSlot(final int start, final int end) {
        for (int i = start; i < end; i++) {
            final ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemSoup) return i;
        }
        return -1;
    }

    // potion

    private void beginPotion(final int slot) {
        potionSlot = slot;
        potPrevSlot = mc.thePlayer.inventory.currentItem;
        throwYaw = heldYaw();
        releaseGuardTick = -1;
        setPotStage(PREPARE);
    }

    private void tickPotion() {
        CombatManager.holdWire();   // we own the wire (pitch-down) -- aura must hold, not wipe it

        if (potStage != IDLE && stageTimer.finished(ACTION_TIMEOUT_MS)) { resetPotion(true); return; }

        switch (potStage) {
            case PREPARE:
                if (!potionSlotUsable() || mc.thePlayer.isUsingItem()) { resetPotion(true); return; }
                // knocked airborne while approaching (KB) -> give the wire back instead of parking
                // the pitch-down spoof through the flight (holdWire would otherwise pin it up to
                // ACTION_TIMEOUT_MS). retry once we land. nothing switched yet, so the restore is a no-op.
                if (!mc.thePlayer.onGround) { resetPotion(true); return; }
                applyThrowRotation();
                if (!pitchReady()) return;
                setPotStage(SWITCHING);
                break;
            case SWITCHING:
                if (!potionSlotUsable()) { resetPotion(true); return; }
                applyThrowRotation();
                if (!pitchReady()) return;
                mc.playerController.setCurrentItemAndSync(potionSlot);
                setPotStage(USING);
                break;
            case USING:
                applyThrowRotation();
                if (!pitchReady()) return;
                if (mc.thePlayer.inventory.currentItem != potionSlot) { resetPotion(true); return; }
                final ItemStack hand = mc.thePlayer.inventory.getCurrentItem();
                if (!isSplashPotion(hand)) { resetPotion(true); return; }
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, hand);
                nextAction = (long) Randoms.between(delay.getRangeMin(), delay.getRangeMax());
                actionTimer.reset();
                setPotStage(RESTORING);
                break;
            case RESTORING:
                applyThrowRotation();
                InventoryUtil.restoreHotbarSlot(potPrevSlot);
                markReleaseGuard();
                resetPotion(false);
                rerollThreshold();
                break;
        }
    }

    private void setPotStage(final int stage) {
        this.potStage = stage;
        stageTimer.reset();
    }

    private void resetPotion(final boolean restoreSlot) {
        if (restoreSlot && potStage != IDLE) InventoryUtil.restoreHotbarSlot(potPrevSlot);
        potStage = IDLE;
        potionSlot = -1;
        potPrevSlot = -1;
        throwYaw = 0.0F;
    }

    private boolean potionSlotUsable() {
        if (potionSlot < 0 || potionSlot > 8) return false;
        return isSplashPotion(mc.thePlayer.inventory.getStackInSlot(potionSlot));
    }

    private void applyThrowRotation() {
        RotationUtil.setRotation(throwYaw, THROW_PITCH, ROTATION_SPEED);
    }

    // fire only once the server actually sees the down-pitch (judged off the reported rotation, not the camera)
    private boolean pitchReady() {
        return Math.abs(mc.thePlayer.getLastReportedPitch() - THROW_PITCH) <= PITCH_TOLERANCE;
    }

    private float heldYaw() {
        return RotationUtil.isActive() ? RotationUtil.getServerYaw() : mc.thePlayer.getLastReportedYaw();
    }

    private void markReleaseGuard() {
        releaseGuardTick = mc.thePlayer.ticksExisted;
    }

    private void clearExpiredReleaseGuard() {
        if (releaseGuardTick >= 0 && (mc.thePlayer == null || mc.thePlayer.ticksExisted != releaseGuardTick)) {
            releaseGuardTick = -1;
        }
    }

    // first hotbar/inventory slot in [start,end) holding a splash potion with a (good) instant-health effect
    private int splashHealSlot(final int start, final int end) {
        for (int i = start; i < end; i++) {
            final ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (!isSplashPotion(s)) continue;
            final List<PotionEffect> effects = ((ItemPotion) s.getItem()).getEffects(s);
            if (effects == null) continue;
            for (final PotionEffect e : effects) {
                final Potion p = Potion.potionTypes[e.getPotionID()];
                if (p == null || p.isBadEffect()) continue;
                if (e.getPotionID() == Potion.heal.getId()) return i;
            }
        }
        return -1;
    }

    private boolean isSplashPotion(final ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof ItemPotion
                && ItemPotion.isSplash(stack.getMetadata());
    }

    // refill (only while the survival inventory is open)

    private void tickRefill() {
        if (mc.currentScreen != lastInvScreen) {   // fresh open -> wait a human beat before the first click
            lastInvScreen = (GuiScreen) mc.currentScreen;
            refillDelay = (long) Randoms.between(300.0, 600.0);
            refillTimer.reset();
        }
        if (!refillTimer.finished(refillDelay)) return;
        if (mc.thePlayer.inventory.getItemStack() != null) return;   // cursor busy -> shift-click no-ops

        // one shift-click per beat, restocking whichever items are enabled but missing from the hotbar
        if (potionEnabled() && splashHealSlot(0, 9) == -1) {
            final int src = splashHealSlot(9, 36);
            if (src != -1) { shiftClick(src); refillTimer.reset(); return; }
        }
        if (soupEnabled() && findSoupSlot(0, 9) == -1) {
            final int src = findSoupSlot(9, 36);
            if (src != -1) { shiftClick(src); refillTimer.reset(); }
        }
    }

    private void shiftClick(final int slot) {
        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slot, 0, 1, mc.thePlayer);
    }

    // shared

    private boolean hasPlayerContext() {
        return mc.thePlayer != null && mc.theWorld != null && mc.playerController != null;
    }

    private boolean soupEnabled()   { return soupEntry.isEnabled(); }
    private boolean potionEnabled() { return potionEntry.isEnabled(); }

    private void rerollThreshold() {
        rolledThreshold = Randoms.between(health.getRangeMin(), health.getRangeMax());
    }

    private void resetAll() {
        resetPotion(false);
        soupPrevSlot = -1;
        soupSlot = -1;
        souping = false;
        groundTicks = 0;
        releaseGuardTick = -1;
        lastInvScreen = null;
    }
}
