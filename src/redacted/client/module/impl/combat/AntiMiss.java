package redacted.client.module.impl.combat;

import net.minecraft.util.MovingObjectPosition;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;

public class AntiMiss extends Module {
    public AntiMiss() {
        super("AntiMiss", "Prevents left-clicking air", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @EventTarget
    public void onLeftClick(final LeftClickEvent event) {
        if (!isValidHit(event.getHitResult())) event.setCancelled(true);
    }

    private boolean isValidHit(final MovingObjectPosition hitResult) {
        return hitResult != null && hitResult.typeOfHit != MovingObjectPosition.MovingObjectType.MISS;
    }
}
