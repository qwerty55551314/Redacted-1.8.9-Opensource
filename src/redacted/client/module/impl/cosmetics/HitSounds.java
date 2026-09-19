package redacted.client.module.impl.cosmetics;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

public class HitSounds extends Module {

    private final ModeValue sound = new ModeValue("Sound", this, "Bow",
            "Bow", "Click", "Orb", "Note", "LevelUp", "Anvil").describe("Which sound to play on a hit");
    private final NumberValue volume = new NumberValue("Volume", this, 1.0, 0.1, 2.0, 0.1).describe("How loud the hit sound is");
    private final NumberValue pitch = new NumberValue("Pitch", this, 1.0, 0.5, 2.0, 0.05).describe("Pitch of the hit sound");
    private final BooleanValue randomPitch = new BooleanValue("Random Pitch", this, false).describe("Jitter the pitch slightly each hit");

    public HitSounds() {
        super("HitSounds", "Plays a sound when you land a hit.", Keyboard.KEY_NONE, Category.COSMETICS);
    }

    @EventTarget
    public void onLeftClick(final LeftClickEvent event) {
        if (event.isCancelled() || mc.thePlayer == null) return;

        // only on a real landed hit on something alive, never on swings/misses
        final MovingObjectPosition hit = event.getHitResult();
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        if (!(hit.entityHit instanceof EntityLivingBase)) return;

        float p = pitch.floatValue();
        if (randomPitch.getValue()) p += (float) Randoms.between(-0.15, 0.15);

        mc.getSoundHandler().playSound(new PositionedSoundRecord(
                new ResourceLocation(resolveSound()), volume.floatValue(), p,
                (float) mc.thePlayer.posX, (float) mc.thePlayer.posY, (float) mc.thePlayer.posZ));
    }

    private String resolveSound() {
        switch (sound.getValue()) {
            case "Click":   return "random.click";
            case "Orb":     return "random.orb";
            case "Note":    return "note.pling";
            case "LevelUp": return "random.levelup";
            case "Anvil":   return "random.anvil_land";
            default:        return "random.bow";
        }
    }
}
