package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.entity.EntityUtil;
import redacted.client.value.impl.BooleanValue;

public class TargetFilter extends Module {
    private final BooleanValue players = new BooleanValue("Players", this, true).describe("Target players");
    private final BooleanValue mobs    = new BooleanValue("Mobs",    this, false).describe("Target hostile mobs");
    private final BooleanValue animals = new BooleanValue("Animals", this, false).describe("Target animals");
    private final BooleanValue npcs    = new BooleanValue("NPCs",    this, false).describe("Target NPCs");

    public TargetFilter() {
        super("Target Filter", "Shared entity-type filter for combat modules; disabled = target everything",
                Keyboard.KEY_NONE, Category.COMBAT);
    }

    public static TargetFilter get() {
        return Client.getInstance().getModuleManager().getModule(TargetFilter.class);
    }

    // single entry point for combat modules. disabled module = every type allowed
    public static boolean allows(final EntityLivingBase entity) {
        final TargetFilter module = get();
        if (module == null || !module.isEnabled()) return true;
        return EntityUtil.isTypeEnabled(entity, module.players.getValue(), module.mobs.getValue(),
                module.animals.getValue(), module.npcs.getValue());
    }
}
