package redacted.client.module.impl.movement;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;

// Full-speed eating/drinking tuned against the Vulcan 2.6.7 source directly:
//
//  * SpeedB/C/D enforce pure sprint baselines (0.2975 ground / 0.36 air) with NO item-use
//    reduction anywhere, so stripping the client-side 0.2x use-slow cannot trip them.
//  * FastUseA needs >=1000ms between the use-start C08 and the consume event -- we eat at vanilla
//    duration, so it's satisfied naturally.
//  * NoSlowA flags C07-while-placing and AutoBlockB flags attacking in a C07 tick (we send neither).
//
// Real packets flow untouched -> food applies instantly on any server running Vulcan 2.6.7.
public class NoSlow extends Module {

    private static NoSlow instance;

    public NoSlow() {
        super("NoSlow", "Full speed while eating/drinking", Keyboard.KEY_NONE, Category.MOVEMENT);
        instance = this;
    }

    // read by EntityPlayerSP's movement path (and Sprint's keybind driver): cancels the vanilla
    // 0.2x use-slow while consuming. Safe on Vulcan -- verified against its source.
    public static boolean isStrippingSlow() {
        final NoSlow m = instance;
        if (m == null || !m.isEnabled()) return false;
        return m.isConsuming();
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;
        // Vulcan needs no per-tick work: real packets flow untouched, the strip above handles it.
    }

    // consumables only (food/potion/milk)
    private boolean isConsuming() {
        if (mc.thePlayer == null || !mc.thePlayer.isUsingItem()) return false;
        final ItemStack stack = mc.thePlayer.getItemInUse();
        if (stack == null) return false;
        final Item item = stack.getItem();
        return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
    }
}
