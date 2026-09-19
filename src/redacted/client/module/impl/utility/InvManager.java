package redacted.client.module.impl.utility;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.CleanerGridValue;
import redacted.client.value.impl.HotbarValue;
import redacted.client.value.impl.RangeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvManager extends Module {
    // shared pacing for every sub-feature whose Instant Swap is off (AutoArmor + AutoHotBar + Cleaner).
    // range sits ABOVE NCP's 155ms inventory-interaction floor (old 100-200 mean was right under it),
    // and the roll is bell-shaped -- a flat uniform delay is exactly what Photon's KS-test calls "bot".
    private final RangeValue delay = new RangeValue("Delay", this, 120.0, 220.0, 0.0, 1000.0, 10.0).describe("Time between inventory actions (ms range)");

    private final BooleanValue autoArmor = new BooleanValue("AutoArmor", this, false).describe("Auto-equip the best armor you're carrying");
    private final BooleanValue instantSwap = new BooleanValue("Instant Swap", this, false, autoArmor, true).describe("Equip armor instantly, ignoring the delay");

    private final BooleanValue autoHotBar = new BooleanValue("AutoHotBar", this, false).describe("Sort your hotbar to the saved layout");
    private final BooleanValue hotbarInstant = new BooleanValue("Instant Swap", this, false, autoHotBar, true).describe("Sort the hotbar instantly, ignoring the delay");
    private final HotbarValue hotbar = new HotbarValue("Layout", this, autoHotBar, true).describe("Pick which item goes in each hotbar slot");

    private final BooleanValue cleaner = new BooleanValue("Cleaner", this, true).describe("Drop junk items out of your inventory");
    private final CleanerGridValue cleanerItems = new CleanerGridValue("Items", this, cleaner, true).describe("Per-item Drop / Keep-one / Ignore list");
    // automatic "drop the worse copies" sweep -- on by default so gear you already have a better
    // version of (worse armor piece, weaker sword, duplicate tools) gets thrown out on its own.
    // the per-item grid above always wins: anything you configured there is never auto-junked.
    private final BooleanValue dropJunk = new BooleanValue("Drop Junk", this, true, cleaner, true)
            .describe("Auto-drop gear you already have a better copy of (worse armor, weaker swords, etc.)");

    private final BooleanValue inventory = new BooleanValue("Inventory", this, false).describe("Manage the open inventory screen");
    private final BooleanValue autoClose = new BooleanValue("Auto Close", this, false, inventory, true).describe("Close the inventory once jobs are done");

    private final StopWatch timer = new StopWatch();
    private final java.util.Random random = new java.util.Random();
    private long currentDelay;
    private GuiInventory invScreen;        // the inventory session we're currently servicing
    private boolean workedThisScreen;      // did any sub-feature act since this inventory opened
    private boolean closeScheduled;        // waiting out the randomized beat before Auto Close fires
    private int scanStartSlot;             // random main-inv start so sweeps never run 9..35 in order
    private int armorTypeOffset;           // random armor-type start so checks never run 0..3 in order

    // human reaction before the FIRST action after an inventory opens (mirrors ChestStealer's
    // opening window); every later action paces off the Delay range again
    private static final double REACT_MIN_MS = 300.0D;
    private static final double REACT_MAX_MS = 600.0D;
    // occasional mid-sort hesitation: a small share of actions stall an extra beat, so the cadence
    // carries rare long pauses instead of being merely noise around one average
    private static final double HESITATION_CHANCE = 0.06;
    private static final double HESITATION_MIN_FACTOR = 1.75;
    private static final double HESITATION_MAX_FACTOR = 2.75;
    private static final double HESITATION_CAP = 2.0;     // spikes never exceed twice the range max
    private static final double CLOSE_MIN_MS = 100.0D;    // randomized beat between the last action
    private static final double CLOSE_MAX_MS = 250.0D;    // and Auto Close actually closing
    private static final int MAIN_SPAN_SLOTS = 35 - InventoryUtil.MAIN_START + 1;  // 9..35, hotbar excluded

    // per-slot tick when the current stack first appeared -- the junk sweep skips fresh pickups so
    // something you just looted isn't insta-dropped the moment you open your inventory
    private static final int JUNK_GRACE_TICKS = 20;
    private final Map<Integer, String> lastStackKey = new HashMap<>();
    private final Map<Integer, Integer> seenTick = new HashMap<>();

    public InvManager() {
        super("InvManager", "Manages your inventory while it's open", Keyboard.KEY_NONE, Category.UTILITY);
    }

    @Override
    public void onEnable() {
        timer.reset();
        currentDelay = 0L;
        invScreen = null;
        workedThisScreen = false;
        closeScheduled = false;
    }

    // PRE only (same phase as ChestStealer): every packet we send is an inventory packet
    // C0E (window click) and C0D (close window), never C07/C08/C02 -- so firing before
    // updateController() in the tick is fine. the isPlayerMoving guard now reads LAST tick's
    // movementInput (input refreshes mid-tick between PRE and POST); that one-tick-late
    // sensitivity is the tradeoff ChestStealer's clicks already accept.
    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // stamp seen-ticks EVERY tick (not just while the inventory is open): the junk sweep's
        // fresh-pickup grace is meant for items you JUST looted, but stamping only on open made
        // every pre-existing item look fresh on the first open of a session, delaying its drop
        // by the full grace window for no reason. gated on the junk sweep because isFresh() is its
        // only reader -- with Drop Junk off this was 27 registry lookups + string builds per tick,
        // forever, for nothing.
        if (cleaner.getValue() && dropJunk.getValue()) stampSeenTicks();

        if (!(mc.currentScreen instanceof GuiInventory)) {
            invScreen = null;
            closeScheduled = false;
            return;
        }
        if (mc.currentScreen != invScreen) {   // new inventory open -> fresh job session
            invScreen = (GuiInventory) mc.currentScreen;
            workedThisScreen = false;
            closeScheduled = false;
            timer.reset();                     // the first action waits out a human reaction,
            currentDelay = (long) Randoms.between(REACT_MIN_MS, REACT_MAX_MS);   // not the instant the screen appears
            // randomized visit orders: sequential low->high sweeps are exactly the
            // "indifferent to distance" pattern Intave's analyzer flags
            scanStartSlot = random.nextInt(MAIN_SPAN_SLOTS);
            armorTypeOffset = random.nextInt(4);
        }

        // stand down while the player is mid-action: a populated 2x2 crafting grid must not be
        // disturbed, and moving anything while the cursor holds a stack (dragging, picked up an
        // item) would desync the carried item from the server
        if (InventoryUtil.isCraftingBusy(mc.thePlayer)) return;
        if (mc.thePlayer.inventory.getItemStack() != null) return;

        if (InventoryUtil.isPlayerMoving(mc.thePlayer)) return;   // CLICK_WINDOW while moving -> Grim MultiActionsC
        if (!timer.finished(currentDelay)) return;

        // one action per tick across every sub-feature -- keeps us to a single C0E/tick.
        // acting also cancels a pending Auto Close: new work beats closing out.
        if (autoArmor.getValue() && autoArmorTick()) { workedThisScreen = true; closeScheduled = false; return; }
        if (autoHotBar.getValue() && autoHotBarTick()) { workedThisScreen = true; closeScheduled = false; return; }
        if (cleaner.getValue() && cleanerTick()) { workedThisScreen = true; closeScheduled = false; return; }

        // nothing left to do this pass -> close, but only after we actually finished a job, and
        // never on the same tick as the last click: a flat instant-close right after the final
        // action is a classic bot tell, so wait out a randomized beat first (like ChestStealer)
        if (inventory.getValue() && autoClose.getValue() && workedThisScreen) {
            if (!closeScheduled) {
                closeScheduled = true;
                timer.reset();
                currentDelay = (long) Randoms.between(CLOSE_MIN_MS, CLOSE_MAX_MS);
                return;
            }
            closeScheduled = false;
            workedThisScreen = false;
            invScreen = null;
            mc.thePlayer.closeScreen();
        }
    }

    private boolean autoArmorTick() {
        for (int step = 0; step < 4; step++) {
            final int type = (armorTypeOffset + step) % 4;  // rotated start: never checks 0..3 in order
            final int armorSlot = 5 + type;                 // container slot for this armor type
            final ItemStack equipped = InventoryUtil.getContainerStack(mc.thePlayer, armorSlot);

            // Don't disturb a non-armor item the player placed in a slot (pumpkin/skull in helmet).
            if (equipped != null && !(equipped.getItem() instanceof ItemArmor)) continue;

            // scoreArmor(equipped) is -1.0 when the slot is empty, so anything beats nothing
            final int bestSlot = InventoryUtil.findBestMainArmorSlot(mc.thePlayer, type, InventoryUtil.scoreArmor(equipped));

            if (bestSlot == -1) continue;                    // nothing better for this slot

            if (equipped != null) {
                if (!InventoryUtil.hasFreeMainSlot(mc.thePlayer)) continue; // no room to drop it; skip
                click(armorSlot);                            // unequip first (slot must be empty to equip)
            } else {
                click(bestSlot);
            }

            schedule(instantSwap.getValue());
            return true;                                     // exactly one action per pass
        }
        return false;
    }

    // sort the real hotbar to the saved template: each slot pulls the best of its preferred items
    // that's actually carried (primary, then fallbacks). one swap per pass; upgrades a held item to a
    // strictly better one but keeps it on ties, so it converges instead of thrashing.
    private boolean autoHotBarTick() {
        final List<List<String>> layout = hotbar.getSlots();
        for (int slot = 0; slot < HotbarValue.SLOTS; slot++) {
            final List<String> prefs = layout.get(slot);
            if (prefs.isEmpty()) continue;

            final int dest = 36 + slot;
            final ItemStack current = InventoryUtil.getContainerStack(mc.thePlayer, dest);

            int src = -1;
            for (final String key : prefs) {
                src = resolveKey(key, slot, layout);
                if (src != -1) break;
            }
            if (src == -1 || src == dest) continue;          // nothing carried, or already holding the chosen best
            // keep what we have if it already fits this slot and is no worse than the candidate -- upgrades to a
            // strictly better item, but won't downgrade or ping-pong between equal copies (ties keep the held one).
            // the comparison is category-aware so food/rod/pearl/block slots rank their own way
            if (current != null && slotWantsStack(prefs, current)
                    && HotbarValue.scoreForPrefs(prefs, current) >= HotbarValue.scoreForPrefs(prefs, InventoryUtil.getContainerStack(mc.thePlayer, src)))
                continue;

            // don't yank an item out of another hotbar slot that legitimately wants it -- two slots
            // would otherwise fight over a single copy forever. main-inv sources (9..35) are always free.
            if (src >= 36 && src <= 44) {
                final int owner = src - 36;
                if (owner != slot && slotWantsStack(layout.get(owner), InventoryUtil.getContainerStack(mc.thePlayer, src))) continue;
            }

            InventoryUtil.swapToHotbar(mc.thePlayer, src, slot);
            schedule(hotbarInstant.getValue());
            return true;                                     // exactly one action per pass
        }
        return false;
    }

    // drop one stack out of the main inventory (never the hotbar). a type set to Drop loses every
    // stack; Keep one loses every stack except the lowest-index one; Ignore is left alone; anything
    // with NO grid entry falls to the auto junk sweep below. one throw per pass so we stay at a
    // single C0E/tick like the other sub-features.
    private boolean cleanerTick() {
        for (int step = 0; step < MAIN_SPAN_SLOTS; step++) {   // 9..35 = main inventory; 36..44 = hotbar (skip)
            // rotated start so the sweep never visits the slots in ascending order
            final int slot = InventoryUtil.MAIN_START + (scanStartSlot + step) % MAIN_SPAN_SLOTS;
            final ItemStack stack = InventoryUtil.getContainerStack(mc.thePlayer, slot);
            if (stack == null) continue;

            final CleanerGridValue.CleanerMode mode = cleanerItems.effectiveMode(stack);
            if (mode != null) {
                if (mode == CleanerGridValue.CleanerMode.IGNORE) continue;
                if (mode == CleanerGridValue.CleanerMode.KEEP_ONE && !hasLowerOfType(slot, stack)) continue;   // this is the one we keep
                return dropAndSchedule(slot);   // Drop, or Keep-one with a lower copy still sat here
            }

            // nothing configured for this item -> automatic sweep: drop gear that's a worse duplicate
            // of the best copy we carry (InventoryUtil.isJunkGear). fresh pickups get a grace window
            // so they're not insta-dropped.
            if (dropJunk.getValue() && !isFresh(slot) && InventoryUtil.isJunkGear(mc.thePlayer, slot, stack)) {
                return dropAndSchedule(slot);
            }
        }
        return false;
    }

    private boolean dropAndSchedule(final int slot) {
        if (InventoryUtil.dropContainerSlot(mc.thePlayer, slot, true)) {
            schedule(false);
            return true;
        }
        return false;   // wanted to drop but the cursor's busy -> try again next pass
    }

    // remember when each slot's current stack first appeared (keyed by item + count so eating one
    // off a stack or picking a new one resets the clock)
    private void stampSeenTicks() {
        for (int slot = InventoryUtil.MAIN_START; slot <= 35; slot++) {
            final ItemStack stack = InventoryUtil.getContainerStack(mc.thePlayer, slot);
            final String key = stack == null ? "" : HotbarValue.keyOf(stack) + "x" + stack.stackSize;
            final String prev = lastStackKey.get(slot);
            if (key.equals(prev)) continue;
            lastStackKey.put(slot, key);
            seenTick.put(slot, mc.thePlayer.ticksExisted);
        }
    }

    private boolean isFresh(final int slot) {
        final Integer seen = seenTick.get(slot);
        return seen != null && mc.thePlayer.ticksExisted - seen < JUNK_GRACE_TICKS;
    }

    // is there a same-type stack in an earlier main-inventory slot? (Keep one keeps the lowest-index one)
    private boolean hasLowerOfType(final int slot, final ItemStack stack) {
        final String key = HotbarValue.keyOf(stack);
        if (key == null) return false;
        for (int i = InventoryUtil.MAIN_START; i < slot; i++) {
            final ItemStack other = InventoryUtil.getContainerStack(mc.thePlayer, i);
            if (other != null && key.equals(HotbarValue.keyOf(other))) return true;
        }
        return false;
    }

    // a slot key resolves to the container slot holding the wanted item, or -1. an item key matches
    // that exact item; a "@cat:Name" key matches the best non-excluded member of that category carried.
    private int resolveKey(final String key, final int forSlot, final List<List<String>> layout) {
        if (HotbarValue.isCategoryKey(key)) {
            final int cat = hotbar.categoryIndexOf(key);
            return cat < 0 ? -1 : bestCategorySlot(cat, forSlot, layout);
        }
        final HotbarValue.Entry e = hotbar.entryForKey(key);
        if (e == null) return -1;
        final ItemStack icon = e.getIcon();
        return InventoryUtil.findContainerSlotMatching(mc.thePlayer, icon.getItem(), icon.getMetadata(), e.isSubtypes());
    }

    // best non-excluded member of a category currently carried; main inventory (9..35) wins ties, and
    // a member already sitting in another slot that wants it is skipped so a second slot can still take
    // a lesser unclaimed copy instead of stalling on the claimed best one.
    private int bestCategorySlot(final int cat, final int forSlot, final List<List<String>> layout) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = InventoryUtil.MAIN_START; i <= InventoryUtil.MAIN_END; i++) {
            final ItemStack st = InventoryUtil.getContainerStack(mc.thePlayer, i);
            if (st == null || !hotbar.categoryIncludes(cat, st)) continue;
            if (i >= 36 && i <= 44) {
                final int owner = i - 36;
                if (owner != forSlot && slotWantsStack(layout.get(owner), st)) continue;   // claimed by its own slot
            }
            final double sc = HotbarValue.categoryScore(cat, st);
            if (sc > bestScore) { bestScore = sc; best = i; }
        }
        return best;
    }

    private boolean slotWantsStack(final List<String> prefs, final ItemStack stack) {
        if (stack == null) return false;
        for (final String key : prefs) {
            if (HotbarValue.isCategoryKey(key)) {
                final int cat = hotbar.categoryIndexOf(key);
                if (cat >= 0 && hotbar.categoryIncludes(cat, stack)) return true;
                continue;
            }
            final HotbarValue.Entry e = hotbar.entryForKey(key);
            if (e == null) continue;
            final ItemStack icon = e.getIcon();
            if (stack.getItem() == icon.getItem() && (!e.isSubtypes() || stack.getMetadata() == icon.getMetadata())) return true;
        }
        return false;
    }

    private void click(final int slotId) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slotId, 0, 1, mc.thePlayer);
    }

    // every feature schedules through here so they all share the one Delay knob. bell-shaped roll
    // inside the range (Randoms.gaussian): keeps a healthy std-dev for Intave's DelayAnalyzer and
    // never a flat uniform stream for Photon's statistical checks. on top sits a small hesitation
    // chance -- humans occasionally stall a beat mid-sort, and a stream that is merely noisy is
    // still flatter than one with rare long pauses mixed in.
    private void schedule(final boolean instant) {
        timer.reset();
        if (instant) {
            currentDelay = 0L;
            return;
        }
        double d = Randoms.gaussian(random, delay.getRangeMin(), delay.getRangeMax());
        if (random.nextDouble() < HESITATION_CHANCE) {
            d *= HESITATION_MIN_FACTOR + random.nextDouble() * (HESITATION_MAX_FACTOR - HESITATION_MIN_FACTOR);
            d = Math.min(d, delay.getRangeMax() * HESITATION_CAP);
        }
        currentDelay = (long) d;
    }

    // ChestStealer reads this to decide whether to auto-close looted chests
    public boolean shouldAutoCloseContainers() {
        return isEnabled() && inventory.getValue() && autoClose.getValue();
    }
}
