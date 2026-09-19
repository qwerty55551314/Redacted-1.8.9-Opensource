package redacted.client.module.impl.world;

import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.InventoryUtil;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

public class FastPlace extends Module {
    private final NumberValue delay = new NumberValue("Delay", this, 0.0D, 0.0D, 4.0D, 1.0D).describe("Right-click delay in ticks (0 = none)");
    private final BooleanValue blocksOnly = new BooleanValue("Blocks Only", this, true).describe("Only speed up placing blocks, not item use");

    public FastPlace() {
        super("FastPlace", "Removes right click placement delay", Keyboard.KEY_NONE, Category.WORLD);
    }

    // PRE: the cap lands before this tick's rightClickDelayTimer decrement (runTick decrements
    // right after the PRE event fires), so a 0-cap lets the same tick's use-item check see an
    // empty timer. the old POST hook applied one full tick late.
    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre() || mc.thePlayer == null || mc.theWorld == null) return;
        if (blocksOnly.getValue() && !InventoryUtil.isHoldingBlock()) return;

        mc.capRightClickDelay(delay.intValue());
    }
}
