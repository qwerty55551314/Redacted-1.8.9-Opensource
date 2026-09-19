package redacted.client.module.impl.combat;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.PacketEvent;
import redacted.client.event.impl.player.EventMoveInput;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.player.MovementUtils;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;

public class Velocity extends Module {

    private static final String JUMP = "Jump";

    private final ModeValue mode = new ModeValue("Mode", this, JUMP, JUMP)
            .describe("Knockback reduction technique");
    private final NumberValue chance = new NumberValue("Chance", this, 80.0, 0.0, 100.0, 1.0, mode, JUMP)
            .describe("% of KB hits to jump-reset (below 85% keeps Hawk's AntiVelocityJump ratio check quiet)");

    // jump mode: armed from the packet thread, consumed on the SAME tick's move input (a late jump
    // is useless -- you're airborne -- and matches no predicted candidate)
    private boolean jumpQueued;

    public Velocity() {
        super("Velocity", "Reduces knockback taken", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @EventTarget
    public void onPacket(final PacketEvent event) {
        if (event.getDirection() != PacketEvent.Direction.INBOUND) return;
        if (mc.thePlayer == null) return;

        if (!(event.getPacket() instanceof S12PacketEntityVelocity)) return;

        final S12PacketEntityVelocity vel = (S12PacketEntityVelocity) event.getPacket();
        if (vel.getEntityID() != mc.thePlayer.getEntityId()) return;

        if (JUMP.equals(mode.getValue())) {
            // arm only while grounded at packet time (Rise LegitVelocity); motionY>0 keeps fall
            // damage / throwable KB from being jump-reset
            if (vel.getMotionY() > 0 && mc.thePlayer.onGround) {
                mc.addScheduledTask(() -> jumpQueued = true);
            }
        }
    }

    @EventTarget
    public void onMoveInput(final EventMoveInput event) {
        if (mc.thePlayer == null) return;

        if (JUMP.equals(mode.getValue())) {
            if (!jumpQueued) return;
            jumpQueued = false;   // single-shot: consume on this tick or drop it (no stale TTL)
            if (!MovementUtils.isOnGround() || !MovementUtils.isMoving()) return;
            if (chance.doubleValue() < 100.0 && Randoms.between(0.0, 100.0) >= chance.doubleValue()) return;
            event.setJump(true);
        }
    }

    @Override
    public void onDisable() {
        jumpQueued = false;
    }
}
