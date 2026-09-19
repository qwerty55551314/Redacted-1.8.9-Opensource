package redacted.client.module.impl.cosmetics;

import net.minecraft.util.EnumParticleTypes;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.player.MovementUtils;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

public class Particles extends Module {

    private final ModeValue type = new ModeValue("Type", this, "Flame",
            "Flame", "Heart", "Crit", "Smoke", "Portal", "Cloud", "Note", "Lava", "Enchant").describe("Which particle to trail");
    private final NumberValue amount = new NumberValue("Amount", this, 2.0, 1.0, 8.0, 1.0).describe("Particles spawned per tick");
    private final BooleanValue onlyWhenMoving = new BooleanValue("Only When Moving", this, true).describe("Only trail particles while you're moving");

    public Particles() {
        super("Particles", "Trails vanilla particles at your feet.", Keyboard.KEY_NONE, Category.COSMETICS);
    }

    @EventTarget
    public void onTick(final EventTick event) {
        // one phase only, else every spawn doubles
        if (event.isPost()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (onlyWhenMoving.getValue() && !MovementUtils.isMoving()) return;

        final EnumParticleTypes particle = resolve();
        final double half = mc.thePlayer.width / 2.0;

        for (int i = 0; i < amount.intValue(); i++) {
            final double x = mc.thePlayer.posX + Randoms.between(-half, half);
            final double z = mc.thePlayer.posZ + Randoms.between(-half, half);
            mc.theWorld.spawnParticle(particle, x, mc.thePlayer.posY, z, 0, 0, 0);
        }
    }

    private EnumParticleTypes resolve() {
        switch (type.getValue()) {
            case "Heart":   return EnumParticleTypes.HEART;
            case "Crit":    return EnumParticleTypes.CRIT;
            case "Smoke":   return EnumParticleTypes.SMOKE_NORMAL;
            case "Portal":  return EnumParticleTypes.PORTAL;
            case "Cloud":   return EnumParticleTypes.CLOUD;
            case "Note":    return EnumParticleTypes.NOTE;
            case "Lava":    return EnumParticleTypes.LAVA;
            case "Enchant": return EnumParticleTypes.ENCHANTMENT_TABLE;
            default:        return EnumParticleTypes.FLAME;
        }
    }
}
