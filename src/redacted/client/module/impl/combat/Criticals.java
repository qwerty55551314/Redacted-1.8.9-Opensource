package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.event.impl.player.EventMoveInput;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.CriticalsUtil;
import redacted.client.util.player.TimerDriftController;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

public class Criticals extends Module {

    private final ModeValue mode = new ModeValue("Mode", this, "Packet", "Packet", "Jump", "Timer").describe("Crit method: mini-hop packets, real jump, or timer");

    private final NumberValue power = new NumberValue("Burst Speed", this, 1.5, 1.01, 5.0, 0.05, mode, "Timer").describe("Timer speed during the boost burst");
    private final NumberValue slowPower = new NumberValue("Slow Speed", this, 0.8, 0.3, 0.99, 0.05, mode, "Timer").describe("Timer speed while recovering drift");
    private final NumberValue rest = new NumberValue("Rest Ticks", this, 30.0, 10.0, 200.0, 1.0, mode, "Timer").describe("Ticks to burst before resting");
    private final NumberValue driftBudget = new NumberValue("Boost Cap", this, 400.0, 50.0, 1100.0, 50.0, mode, "Timer").describe("Max accumulated timer drift before backing off");
    private final BooleanValue onlyAura = new BooleanValue("Only On Kill Aura", this, false, mode, "Timer").describe("Only boost while Silent Aura has a target");

    private boolean shouldJump;

    private SilentAura silentAura;
    private final TimerDriftController drift = new TimerDriftController();

    public Criticals() {
        super("Criticals", "Legit criticals on attack.", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        silentAura = Client.getInstance().getModuleManager().getModule(SilentAura.class);
        drift.reset();
    }

    @Override
    public void onDisable() {
        shouldJump = false;
        mc.timer.timerSpeed = 1.0F;
        drift.reset();
    }

    @EventTarget
    public void onLeftClick(LeftClickEvent e) {
        if (!(CriticalsUtil.canCrit() && e.getHitResult() != null
                && e.getHitResult().entityHit instanceof EntityLivingBase)) return;

        // real vanilla jump on the next move input -> crits land on the descent, motion looks normal
        shouldJump = true;
    }

    @EventTarget
    public void onMoveInput(EventMoveInput e) {
        if (shouldJump) {
            shouldJump = false;
            e.setJump(true);
        }
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!"Timer".equals(mode.getValue())) return;
        if (event.isPost()) return;
        if (mc.thePlayer == null || mc.theWorld == null) {
            mc.timer.timerSpeed = 1.0F;
            return;
        }

        if (onlyAura.getValue() && (silentAura == null || !silentAura.isEnabled())) {
            drift.recover(slowPower.floatValue());
            return;
        }

        if (drift.sleeping()) return;

        if (drift.overBudget(driftBudget.getValue())) {
            drift.recover(slowPower.floatValue());
            return;
        }

        final EntityLivingBase target = silentAura != null ? silentAura.getTarget() : null;
        if (target == null) {
            drift.recover(slowPower.floatValue());
            return;
        }

        drift.burst(power.floatValue(), rest.intValue());
    }
}