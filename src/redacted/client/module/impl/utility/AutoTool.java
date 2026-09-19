package redacted.client.module.impl.utility;

import net.minecraft.block.Block;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.entity.EntityUtil;
import redacted.client.util.player.InventoryUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.ItemGridValue;

public class AutoTool extends Module {

    // pickaxe cell = mining-tool swap, sword cell = combat-weapon swap (icons are just stand-ins for the category)
    private final ItemGridValue.Entry toolsEntry = new ItemGridValue.Entry("Tools", new ItemStack(Items.diamond_pickaxe), true);
    private final ItemGridValue.Entry swordEntry = new ItemGridValue.Entry("Sword", new ItemStack(Items.diamond_sword), true);
    private final ItemGridValue swaps = new ItemGridValue("Swaps", this, toolsEntry, swordEntry)
            .describe("Pickaxe = swap to the best tool for the block you mine, sword = swap to your best weapon when you hit a mob");

    // covers BedAura's 2-tick postBreakCooldown gap so the swap doesn't get yanked back to the
    // original slot between consecutive targets (adjacent -> bed transition)
    private static final int RESTORE_GRACE_TICKS = 3;

    // after swinging at a mob the mining path yields this long so it can't yank a pickaxe out
    // from under the sword swap (real crosshair grazes a block every other tick mid-fight)
    private static final long COMBAT_SUPPRESS_MS = 400L;
    private final StopWatch combatTimer = new StopWatch();

    private int previousSlot = -1;
    private int idleTicks = 0;

    public AutoTool() {
        super("AutoTool", "Best hotbar tool for the block you mine, best sword for the entity you hit", Keyboard.KEY_NONE, Category.UTILITY);
    }

    @Override
    public void onDisable() {
        restore();
        idleTicks = 0;
    }

    @EventTarget
    public void onTick(EventTick event) {
        // PRE so we swap before vanilla fires the dig packet this tick
        if (!event.isPre() || mc.thePlayer == null || mc.theWorld == null) return;
        // tools off -> flip back any tool swap we're holding, then stay out of the mining path
        if (!toolsEntry.isEnabled()) { restore(); idleTicks = 0; return; }

        // just swung at a living entity -> the sword path owns the slot this window, leave it alone
        if (!combatTimer.finished(COMBAT_SUPPRESS_MS)) { idleTicks = 0; return; }

        final boolean playerMining = mc.currentScreen == null
                && mc.inGameHasFocus
                && GameSettings.isKeyDown(mc.gameSettings.keyBindAttack)
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        // BedAura (and any future module that drives clickBlock) sets isHittingBlock without holding
        // the attack key. without this branch our restore() fires same-tick and rips the C09 back.
        final boolean externalMining = mc.playerController != null && mc.playerController.getIsHittingBlock();

        if (!playerMining && !externalMining) {
            // hold off restoring for a few ticks. BedAura sets postBreakCooldown=2 after every block
            // break and yields to SilentAura by setting it to 2 also; during those gaps isHittingBlock
            // is false and we'd otherwise rip the C09 back to the user's original slot.
            if (idleTicks < RESTORE_GRACE_TICKS) {
                idleTicks++;
                return;
            }
            restore();
            return;
        }
        idleTicks = 0;

        final BlockPos miningPos = externalMining
                ? mc.playerController.getCurrentBlock()
                : mc.objectMouseOver.getBlockPos();
        if (miningPos == null) {
            restore();
            return;
        }

        final Block block = mc.theWorld.getBlockState(miningPos).getBlock();
        if (block == Blocks.air) {
            restore();
            return;
        }

        swapIfBetter(block);
    }

    // BedAura calls this before clickBlock so the C09 lands ahead of the C07.
    // No-op when disabled, so AutoTool's own toggle is the user-facing gate.
    public void selectToolFor(Block block) {
        if (!isEnabled() || !toolsEntry.isEnabled() || mc.thePlayer == null || block == null || block == Blocks.air) return;
        swapIfBetter(block);
    }

    // LeftClickEvent fires right before the attack packet, so swapping here = we hit with the sword
    // equipped. one-way on purpose (no restore) so it doesn't fight the mining path's previousSlot.
    @EventTarget
    public void onLeftClick(final LeftClickEvent event) {
        if (event.isCancelled() || mc.thePlayer == null) return;

        final MovingObjectPosition hit = event.getHitResult();
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        if (!(hit.entityHit instanceof EntityLivingBase)) return;
        if (!EntityUtil.isValidTarget((EntityLivingBase) hit.entityHit)) return;

        // mark combat regardless of the Sword toggle -- even a tools-only user shouldn't end up
        // holding a pickaxe because the crosshair grazed a block while hitting a mob
        combatTimer.reset();

        if (!swordEntry.isEnabled()) return;

        // sword first, else axe -> pickaxe -> spade (findBestHotbarWeapon), never a non-weapon tool
        final int best = InventoryUtil.findBestHotbarWeapon(mc.thePlayer);
        if (best == -1 || best == mc.thePlayer.inventory.currentItem) return;

        // playerController does the C09 + currentPlayerItem so we don't double-send -> grim BadPacketsA
        mc.playerController.setCurrentItemAndSync(best);
    }

    private void swapIfBetter(Block block) {
        final int current = mc.thePlayer.inventory.currentItem;
        final int best = pickBestSlot(block, current);
        if (best == current) return;

        // stash the slot we started on so restore() can flip back when we stop mining
        if (previousSlot == -1) previousSlot = current;
        // straight to playerController so it bumps currentPlayerItem; otherwise vanilla's
        // per-tick resync fires a 2nd C09 -> grim BackPacketsA
        mc.playerController.setCurrentItemAndSync(best);
    }

    private int pickBestSlot(Block block, int currentSlot) {
        int best = currentSlot;
        float bestScore = InventoryUtil.scoreToolForBlock(mc.thePlayer.inventory.mainInventory[currentSlot], block);

        for (int slot = 0; slot < InventoryUtil.HOTBAR_SIZE; slot++) {
            final ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];

            final float s = InventoryUtil.scoreToolForBlock(stack, block);
            if (s > bestScore) {
                best = slot;
                bestScore = s;
            }
        }
        return best;
    }

    private void restore() {
        if (previousSlot != -1 && mc.thePlayer != null) {
            mc.playerController.setCurrentItemAndSync(previousSlot);
        }
        previousSlot = -1;
    }
}
