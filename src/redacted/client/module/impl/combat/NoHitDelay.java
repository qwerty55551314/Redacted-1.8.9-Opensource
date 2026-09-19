package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;

public class NoHitDelay extends Module {

    public NoHitDelay() {
        super("NoHitDelay", "Strips the client-side hit delay so every swing registers.", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @EventTarget
    public void onLeftClick(final LeftClickEvent e) {
        if (e.getHitResult() == null || !(e.getHitResult().entityHit instanceof EntityLivingBase)) return;

        // wipe the target's invuln window before attackEntityFrom runs -> the local hit lands + re-flashes every swing
        final EntityLivingBase target = (EntityLivingBase) e.getHitResult().entityHit;
        target.hurtResistantTime = 0;
        target.hurtTime = 0;
    }
}
