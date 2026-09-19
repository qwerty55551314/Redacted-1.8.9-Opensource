package redacted.client.module.impl.combat;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.PacketEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

public class WTap extends Module {

    private final NumberValue chance = new NumberValue("Chance", this, 100.0D, 0.0D, 100.0D, 1.0D).describe("% of sprint-hits to tap on");
    private final BooleanValue stap = new BooleanValue("STap", this, false).describe("Tap back instead of forward for a harder reset");
    private final NumberValue distance = new NumberValue("Distance", this, 3.0D, 0.0D, 6.0D, 0.1D, stap, true).describe("Range under which to S-tap instead");

    private int pendingTicks;    // human reaction delay: ticks until the tap lands
    private int suppressTicks;
    private boolean backTap;

    public WTap() {
        super("WTap", "W-taps to reset sprint for full knockback", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onDisable() {
        this.restoreKeys();
        this.suppressTicks = 0;
        this.pendingTicks = 0;
    }

    @EventTarget
    public void onPacket(final PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.OUTBOUND) return;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;

        final C02PacketUseEntity useEntity = (C02PacketUseEntity) event.getPacket();
        if (useEntity.getAction() != C02PacketUseEntity.Action.ATTACK) return;

        // already mid-tap -> let the current reset finish, don't restart the cycle
        if (this.suppressTicks > 0 || this.pendingTicks > 0) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // only sprint-hits carry the bonus knockback worth resetting for
        if (!mc.thePlayer.isSprinting()) return;

        // entity hits only
        final Entity target = useEntity.getEntityFromWorld(mc.theWorld);
        if (!(target instanceof EntityLivingBase)) return;

        if (Randoms.between(0.0D, 100.0D) >= this.chance.doubleValue()) return;

        // close range -> S-tap (press back) for a harder reset, otherwise a plain forward tap
        this.backTap = this.stap.getValue() && mc.thePlayer.getDistanceToEntity(target) <= this.distance.doubleValue();
        // land the tap 1-3 ticks after the hit: a constant same-tick reset is a timing fingerprint
        // (human w-tap reaction jitters 50-200ms), and Polar-class ACs profile exactly that offset
        this.pendingTicks = 1 + (int) Randoms.between(0.0D, 3.0D);
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre() || mc.thePlayer == null) return;

        if (this.pendingTicks > 0 && --this.pendingTicks == 0) {
            this.suppressTicks = 1;
            this.applyTap();
            return;
        }

        // one movement tick of dropped input was enough for vanilla to clear sprint -- give the keys back
        if (this.suppressTicks > 0 && --this.suppressTicks == 0) {
            this.restoreKeys();
        }
    }

    // Sprint reads this so auto-sprint won't fight the tap on the tick we're holding forward released
    public boolean isSuppressing() {
        return this.suppressTicks > 0;
    }

    // drop forward (and drive back on an S-tap) so vanilla derives setSprinting(false) from moveForward < 0.8
    private void applyTap() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        if (this.backTap) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), true);
        }
    }

    private void restoreKeys() {
        final GameSettings settings = mc.gameSettings;
        KeyBinding.setKeyBindState(settings.keyBindForward.getKeyCode(), GameSettings.isKeyDown(settings.keyBindForward));
        KeyBinding.setKeyBindState(settings.keyBindBack.getKeyCode(), GameSettings.isKeyDown(settings.keyBindBack));
        this.backTap = false;
    }
}
