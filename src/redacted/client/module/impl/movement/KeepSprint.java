package redacted.client.module.impl.movement;

import org.lwjglx.input.Keyboard;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.value.impl.NumberValue;

// settings holder -- the slowdown strip (and the why) lives in
// EntityPlayer.attackTargetEntityWithCurrentItem
public class KeepSprint extends Module {

    // percent of the post-hit slowdown removed
    private final NumberValue percent = new NumberValue("Percent", this, 50, 10, 100, 1).describe("How much post-hit slowdown to cancel");

    public KeepSprint() {
        super("KeepSprint", "Reduces post-attack slowdown", Keyboard.KEY_NONE, Category.MOVEMENT);
    }

    public int getPercent() {
        return percent.intValue();
    }
}
