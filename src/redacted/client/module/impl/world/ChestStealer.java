package redacted.client.module.impl.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.module.impl.utility.InvManager;
import redacted.client.util.Randoms;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.RangeValue;

import java.util.Random;

public class ChestStealer extends Module {

    private final RangeValue delay = new RangeValue("Delay", this, 160.0, 280.0, 0.0, 1000.0, 10.0).describe("Ms range between grabbing each item");
    // close the chest GUI once there's nothing useful left to take -- works even when nothing was
    // stolen at all (an empty chest still gets closed), unlike InvManager's shared Auto Close which
    // requires a real loot first. off by default so manual chest use is never hijacked.
    private final BooleanValue autoClose = new BooleanValue("Auto Close", this, false)
            .describe("Close the chest once nothing useful is left (drained or full)");

    private final StopWatch timer = new StopWatch();
    private final Random random = new Random();

    private static final double REACT_MIN_MS = 300.0D;   // human reaction before the first grab
    private static final double REACT_MAX_MS = 600.0D;
    private long nextDelayMs;                             // gate threshold: reaction time on open, rolled Delay after
    private boolean closeScheduled;                       // waiting out the randomized close delay

    private static final long OPEN_WINDOW_MS = 5000L; // C08 -> S2DPacketOpenWindow round-trip budget
    private volatile BlockPos pendingClickPos;          // last block we right-clicked; shared between onPacket and onTick
    private volatile long pendingClickMs;
    private GuiChest evaluatedScreen;                    // main-thread only: the screen we've already judged
    private boolean currentChestGenuine;                // main-thread only: verdict for evaluatedScreen
    private boolean stoleSomethingThisScreen;           // gate Auto Close on having actually looted something
    private int scanStartSlot;                          // random start so we don't scan 0..n in order

    public ChestStealer() {
        super("ChestStealer", "Takes all items from manually opened chests", Keyboard.KEY_NONE, Category.WORLD);
    }

    // remember the last block we right-clicked; that's how we tell a real chest from a server menu
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return;
        if (!(event.getPacket() instanceof C08PacketPlayerBlockPlacement)) return;

        final C08PacketPlayerBlockPlacement pkt = (C08PacketPlayerBlockPlacement) event.getPacket();
        if (pkt.getPlacedBlockDirection() == 255) return; // right-click-in-air sentinel, not a block interaction

        // just packet fields here; the chest-block check runs in onTick
        pendingClickPos = pkt.getPosition();
        pendingClickMs = System.currentTimeMillis();
    }

    // PRE: windowClick is still an outbound packet (invariant 5)
    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // server NPC menus / lobby compass / kit selectors all arrive as GuiChest+ContainerChest too,
        // so the class check alone isn't enough - gate on a real chest-block right-click instead
        if (!(mc.currentScreen instanceof GuiChest) || !(mc.thePlayer.openContainer instanceof ContainerChest)) {
            evaluatedScreen = null;
            currentChestGenuine = false;
            closeScheduled = false;
            return;
        }

        if (mc.currentScreen != evaluatedScreen) {   // a new window opened -> judge it once, keep the verdict
            evaluatedScreen = (GuiChest) mc.currentScreen;
            currentChestGenuine = openedFromRealChest();
            pendingClickPos = null;                  // consume so it can't validate a later menu
            timer.reset();                           // first grab waits a human reaction, not the instant it opens
            nextDelayMs = (long) Randoms.between(REACT_MIN_MS, REACT_MAX_MS);
            stoleSomethingThisScreen = false;
            closeScheduled = false;
            // random scan start: Intave's RegrDelayAnalyzer flags sequential 0..n slot scanning
            // ("indifferent to distance"), so each chest is drained from a different offset
            final ContainerChest judged = (ContainerChest) mc.thePlayer.openContainer;
            scanStartSlot = random.nextInt(Math.max(1, judged.getLowerChestInventory().getSizeInventory()));
        }
        if (!currentChestGenuine) return;

        // unconditional guard: CLICK_WINDOW while moving is exactly what Grim's MultiActionsC flags
        if (InventoryUtil.isPlayerMoving(mc.thePlayer)) return;

        if (!timer.finished(nextDelayMs)) return;

        final ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
        final int chestSize = container.getLowerChestInventory().getSizeInventory();

        for (int step = 0; step < chestSize; step++) {
            final int i = (scanStartSlot + step) % chestSize;
            final Slot slot = container.getSlot(i);
            if (!slot.getHasStack()) continue;

            final ItemStack stack = slot.getStack();
            if (!hasInventorySpace(stack)) continue;   // skip what won't fit, keep draining the rest

            mc.playerController.windowClick(container.windowId, i, 0, 1, mc.thePlayer);
            timer.reset();
            // bell-curve roll inside the min<->max range: Intave's RegrDelayAnalyzer nerfs
            // "taking items at constant speed", and NCP's FastClick expects >=155ms between
            // inventory interactions -- the gaussian stream clusters around the range midpoint
            // instead of spreading flat-uniform and never repeats a constant gap.
            nextDelayMs = (long) Randoms.gaussian(random, delay.getRangeMin(), delay.getRangeMax());
            stoleSomethingThisScreen = true;
            closeScheduled = false;   // the server restocked mid-countdown: re-roll the close delay after this
            return;
        }

        // reached here = nothing left to grab (chest drained or the rest won't fit). close only
        // when the user opted in:
        //   - ChestStealer's own Auto Close fires regardless of whether we actually took anything
        //     (an empty / junk-only chest still gets closed, after the human reaction delay above)
        //   - InvManager's shared Auto Close still requires a real loot to have happened
        final InvManager inv = Client.getInstance().getModuleManager().getModule(InvManager.class);
        final boolean invWantsClose = inv != null && inv.shouldAutoCloseContainers();
        if (autoClose.getValue() || (invWantsClose && stoleSomethingThisScreen)) {
            // never close on the same tick as the last click, and never at a constant offset:
            // wait out a randomized 100-250ms first (AtField's chest-stealer does the same)
            if (!closeScheduled) {
                closeScheduled = true;
                timer.reset();
                nextDelayMs = (long) Randoms.between(100.0, 250.0);
                return;
            }
            closeScheduled = false;
            stoleSomethingThisScreen = false;
            mc.thePlayer.closeScreen();
        } else {
            // nothing grabbable and not closing: without this the timer stays finished and the whole
            // scan re-runs every tick for as long as the GUI sits open (full inventory + full double
            // chest = ~1.9k slot reads a tick, forever). re-check at Delay cadence instead.
            timer.reset();
        }
    }

    private boolean hasInventorySpace(ItemStack stack) {
        for (int i = InventoryUtil.MAIN_START; i <= InventoryUtil.MAIN_END; i++) {
            final ItemStack invStack = InventoryUtil.getContainerStack(mc.thePlayer, i);
            if (invStack == null) return true;
            if (invStack.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(invStack, stack)
                    && invStack.stackSize < invStack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    // the chest GUI only opens after we right-click a real chest/ender-chest block;
    // server menus have no such interaction (NPC = C02, compass/kit = air-use C08).
    // no "is a chest nearby" fallback: after looting one, pendingClickPos is consumed, so the very
    // next menu opened while still standing on that chest would pass and get shift-clicked.
    // custom/server-named real chests need no fallback -- isRealChestBlock reads the block, not the name.
    private boolean openedFromRealChest() {
        final BlockPos pos = pendingClickPos;
        return pos != null
                && System.currentTimeMillis() - pendingClickMs <= OPEN_WINDOW_MS
                && isRealChestBlock(pos);
    }

    private boolean isRealChestBlock(final BlockPos pos) {
        if (pos == null || mc.theWorld == null) return false;
        if (!mc.theWorld.isBlockLoaded(pos)) return false;

        final Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block instanceof BlockChest || block instanceof BlockEnderChest; // BlockChest = normal + trapped
    }
}
