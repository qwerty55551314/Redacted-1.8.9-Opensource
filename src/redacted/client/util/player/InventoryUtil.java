package redacted.client.util.player;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import redacted.client.module.impl.combat.AutoBlock;
import java.util.HashSet;
import java.util.Set;

public class InventoryUtil {
    public static final Set<Block> blacklist = new HashSet<>();
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_START = 9;
    public static final int MAIN_END = 44;

    public static boolean isBlockStack(final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    public static boolean isHoldingBlock() {
        final EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        return p != null && isBlockStack(p.getHeldItem());
    }

    public static boolean isHoldingSword() {
        final EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        return p != null && p.getHeldItem() != null && p.getHeldItem().getItem() instanceof ItemSword;
    }

    // raw physical poll only, never isUsingItem() -- AutoBlock owns that state and would read itself.
    // while AutoBlock holds the use keybind, its synthetic press lives in keyBindUseItem.isKeyDown()
    // (the pressed field setKeyBindState flips), so drop that term and report the physical key alone
    // else its own hold reads as a manual right-click and freezes the cycle / stops every swing.
    public static boolean isUseKeyDown() {
        final GameSettings settings = Minecraft.getMinecraft().gameSettings;
        final boolean physical = GameSettings.isKeyDown(settings.keyBindUseItem);
        if (AutoBlock.ownsUseKey()) return physical;
        return physical || settings.keyBindUseItem.isKeyDown();
    }

    // any of these in the same tick as a CLICK_WINDOW -> Grim MultiActionsC
    public static boolean isPlayerMoving(final EntityPlayerSP p) {
        return p.isSprinting()
                || p.isSneaking()
                || p.movementInput.moveForward != 0.0F
                || p.movementInput.moveStrafe != 0.0F
                || p.movementInput.jump;
    }

    // the player-inventory 2x2 crafting grid (container slots 1..4) holds items -- moving
    // anything around would wreck the player's crafting setup, so the manager stands down
    public static boolean isCraftingBusy(final EntityPlayerSP p) {
        for (int i = 1; i <= 4; i++) {
            if (getContainerStack(p, i) != null) return true;
        }
        return false;
    }

    public static ItemStack getContainerStack(final EntityPlayerSP p, final int slotId) {
        final Slot slot = p.inventoryContainer.getSlot(slotId);
        return slot == null ? null : slot.getStack();
    }

    public static double scoreArmor(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) return -1.0;
        final ItemArmor armor = (ItemArmor) stack.getItem();
        int materialStrength = 0;
        for (int t = 0; t < 4; t++) {
            materialStrength += armor.getArmorMaterial().getDamageReductionAmount(t);
        }
        final int protection = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
        // remaining-durability fraction breaks ties between identical pieces so a fresh copy
        // outranks a nearly-broken one (gold-standard check: Unfair's InvManager scores the same)
        return materialStrength * 1000.0 + protection + durabilityFraction(stack);
    }

    public static double scoreItem(final ItemStack stack) {
        if (stack == null) return 0.0;
        final Item item = stack.getItem();
        if (item instanceof ItemSword) return scoreSword(stack);
        if (item instanceof ItemBow) return scoreBow(stack);
        if (item instanceof ItemTool) return scoreTool(stack);
        return 0.0;
    }

    public static double scoreSword(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemSword)) return 0.0;
        final ItemSword sword = (ItemSword) stack.getItem();
        // effective per-hit damage: 4 base + material, plus the real Sharpness bonus (1.25/level)
        return (4.0 + sword.getDamageVsEntity())
                + EnchantmentHelper.getModifierForCreature(stack, EnumCreatureAttribute.UNDEFINED)
                + durabilityFraction(stack);
    }

    public static double scoreBow(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBow)) return 0.0;
        final int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
        final int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
        final int flame = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack);
        final int infinity = EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack);
        return power * 100.0 + punch * 10.0 + flame * 5.0 + infinity * 3.0 + durabilityFraction(stack);
    }

    public static double scoreTool(final ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemTool)) return 0.0;
        final ItemTool tool = (ItemTool) stack.getItem();
        // real break speed: material efficiency + the Efficiency-enchant term (same math as scoreToolForBlock)
        float speed = tool.getToolMaterial().getEfficiencyOnProperMaterial();
        final int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
        if (speed > 1.0F && efficiency > 0) speed += efficiency * efficiency + 1;
        // harvest tier wins first so a diamond pick outranks a faster gold one that can't mine high-tier blocks
        return tool.getToolMaterial().getHarvestLevel() * 1000.0 + speed + durabilityFraction(stack);
    }

    // remaining-durability fraction in [0,1]; 1.0 for non-damageable items. ties identical gear
    // by how much life is left so the cleaner never keeps a broken copy over a fresh one.
    private static double durabilityFraction(final ItemStack stack) {
        if (stack == null || !stack.isItemStackDamageable()) return 1.0;
        return (double) (stack.getMaxDamage() - stack.getItemDamage()) / Math.max(1, stack.getMaxDamage());
    }

    public static float scoreToolForBlock(final ItemStack stack, final Block block) {
        if (!isMiningTool(stack) || block == null) return 0.0F;
        float score = stack.getStrVsBlock(block);
        final int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
        if (efficiency > 0 && score > 1.0F) score += efficiency * efficiency + 1;
        return score;
    }

    public static boolean isMiningTool(final ItemStack stack) {
        if (stack == null) return false;
        final Item item = stack.getItem();
        return item instanceof ItemPickaxe
                || item instanceof ItemAxe
                || item instanceof ItemSpade
                || item instanceof ItemShears;
    }

    // "useless gear" detection (InvManager's Drop Junk sweep)
    // a stack is junk when we already carry a better-or-equal same-type copy somewhere -- main
    // inventory + hotbar (9..44), and for armor the equipped piece too (5..8), so a worse pair of
    // boots is junk while a better pair sits on your feet. `slot` is the container slot of the
    // stack being judged; it's exempted from the best-copy scan so the best copy is never junked.
    // stackables (blocks, food, pearls...) are never junk -- only one-per-slot equipment (armor
    // per type, swords, bows, mining tools per class) gets the keep-the-best treatment.
    public static boolean isJunkGear(final EntityPlayerSP p, final int slot, final ItemStack stack) {
        if (stack == null) return false;
        final Item item = stack.getItem();
        if (item instanceof ItemArmor) return worseDuplicate(p, slot, stack, ((ItemArmor) item).armorType);
        if (item instanceof ItemSword) return worseDuplicate(p, slot, stack, ItemSword.class);
        if (item instanceof ItemBow) return worseDuplicate(p, slot, stack, ItemBow.class);
        if (isMiningTool(stack)) return worseDuplicate(p, slot, stack, item.getClass());
        return false;
    }

    private static boolean worseDuplicate(final EntityPlayerSP p, final int slot, final ItemStack stack, final Object typeKey) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        final int start = typeKey instanceof Integer ? 5 : MAIN_START;   // 5..8 = equipped armor
        for (int i = start; i <= MAIN_END; i++) {
            final ItemStack other = getContainerStack(p, i);
            if (other == null || !sameGearType(other, typeKey)) continue;
            final double s = gearScore(other);
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        if (best == -1 || best == slot) return false;   // nothing to beat, or this IS the best copy
        return gearScore(stack) <= bestScore;           // ties count as junk too (equal = useless duplicate)
    }

    private static boolean sameGearType(final ItemStack other, final Object typeKey) {
        if (typeKey instanceof Integer) {
            return other.getItem() instanceof ItemArmor
                    && ((ItemArmor) other.getItem()).armorType == (Integer) typeKey;
        }
        return other.getItem().getClass() == typeKey;
    }

    private static double gearScore(final ItemStack stack) {
        return stack.getItem() instanceof ItemArmor ? scoreArmor(stack) : scoreItem(stack);
    }

    public static int findBestHotbarSword(final EntityPlayerSP player) {
        int bestSlot = -1;
        // -1 seed so a wood/gold sword (scoreSword == 0) still beats "nothing"
        double bestScore = -1.0;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            final ItemStack stack = player.inventory.mainInventory[slot];
            if (!(stack != null && stack.getItem() instanceof ItemSword)) continue;

            final double score = scoreSword(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    // sword first (best by scoreSword). no sword -> best tool-weapon by class priority
    // axe > pickaxe > spade, ties broken by material tier. -1 if the hotbar has none.
    // NOT scoreItem/scoreTool -- those rank by harvest power and would pick a pickaxe over a sword.
    public static int findBestHotbarWeapon(final EntityPlayerSP player) {
        final int swordSlot = findBestHotbarSword(player);
        if (swordSlot != -1) return swordSlot;

        int bestSlot = -1, bestRank = 0, bestTier = -1;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            final ItemStack stack = player.inventory.mainInventory[slot];
            if (stack == null) continue;
            final Item item = stack.getItem();
            final int rank = item instanceof ItemAxe ? 3
                    : item instanceof ItemPickaxe ? 2
                    : item instanceof ItemSpade ? 1 : 0;
            if (rank == 0) continue; // hoes/shears are fist-tier, not a weapon we fall back to
            final int tier = ((ItemTool) item).getToolMaterial().getHarvestLevel();
            if (rank > bestRank || (rank == bestRank && tier > bestTier)) {
                bestRank = rank;
                bestTier = tier;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    // best-scoring armor piece of this type in the main inventory, above minScore. -1 if none.
    // seed minScore with the equipped piece's score so "nothing better" comes back as -1.
    public static int findBestMainArmorSlot(final EntityPlayerSP player, final int armorType, final double minScore) {
        int bestSlot = -1;
        double bestScore = minScore;
        for (int i = MAIN_START; i <= MAIN_END; i++) {
            final ItemStack stack = getContainerStack(player, i);
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;
            if (((ItemArmor) stack.getItem()).armorType != armorType) continue;

            final double score = scoreArmor(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static void restoreHotbarSlot(final int previousSlot) {
        restoreHotbarSlot(previousSlot, -1);
    }

    // expectedCurrentSlot lets the caller skip the restore when the user scrolled off the
    // slot we switched them to -- pass -1 to restore unconditionally.
    public static void restoreHotbarSlot(final int previousSlot, final int expectedCurrentSlot) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null) return; // onDisable paths can fire post-disconnect
        if (previousSlot < 0 || previousSlot >= HOTBAR_SIZE) return;
        if (expectedCurrentSlot != -1 && mc.thePlayer.inventory.currentItem != expectedCurrentSlot) return;
        if (mc.thePlayer.inventory.currentItem == previousSlot) return;
        mc.playerController.setCurrentItemAndSync(previousSlot);
    }

    // main inventory + hotbar (container slot ids 9..44)
    public static boolean hasFreeMainSlot(final EntityPlayerSP p) {
        for (int i = MAIN_START; i <= MAIN_END; i++) {
            if (getContainerStack(p, i) == null) return true;
        }
        return false;
    }

    // throw a stack out of the survival inventory (windowId 0, mode 4 = drop). callers pass main-inventory
    // ids (9..35) so the hotbar (36..44), armor (5..8) and crafting (0..4) are never touched. mode 4 no-ops
    // unless the cursor is empty, so guard it. button 1 drops the whole stack, button 0 drops a single item.
    public static boolean dropContainerSlot(final EntityPlayerSP player, final int slotId, final boolean wholeStack) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (player == null || mc.playerController == null) return false;
        if (slotId < MAIN_START || slotId > 35) return false;
        if (player.inventory.getItemStack() != null) return false;   // cursor's holding something -> mode 4 won't fire
        mc.playerController.windowClick(player.inventoryContainer.windowId, slotId, wholeStack ? 1 : 0, 4, player);
        return true;
    }

    // move whatever's in container slot srcContainerSlot onto hotbar index hotbarIndex (0-8).
    // mode 2 = swap-with-hotbar; the button arg is the hotbar index, NOT a slot id (Container.slotClick).
    public static void swapToHotbar(final EntityPlayerSP player, final int srcContainerSlot, final int hotbarIndex) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (player == null || mc.playerController == null) return;
        if (hotbarIndex < 0 || hotbarIndex >= HOTBAR_SIZE) return;
        mc.playerController.windowClick(player.inventoryContainer.windowId, srcContainerSlot, hotbarIndex, 2, player);
    }

    // first container slot holding the wanted item -- main inventory (9..35) first so we don't pull
    // out of an already-correct hotbar slot, then the hotbar (36..44). subtypes=true requires the
    // metadata to match (apple/wool variants); false means meta is durability and is ignored so a
    // chipped tool still matches. returns the container slot id, or -1 if absent.
    public static int findContainerSlotMatching(final EntityPlayerSP player, final Item item, final int meta, final boolean subtypes) {
        if (player == null || item == null) return -1;
        for (int i = MAIN_START; i <= 35; i++) {
            if (matchesItem(getContainerStack(player, i), item, meta, subtypes)) return i;
        }
        for (int i = 36; i <= MAIN_END; i++) {
            if (matchesItem(getContainerStack(player, i), item, meta, subtypes)) return i;
        }
        return -1;
    }

    private static boolean matchesItem(final ItemStack stack, final Item item, final int meta, final boolean subtypes) {
        if (stack == null || stack.getItem() != item) return false;
        return !subtypes || stack.getMetadata() == meta;
    }
}
