package redacted.client.event.impl;

import net.minecraft.util.MovingObjectPosition;
import redacted.client.event.CancellableEvent;

public class LeftClickEvent extends CancellableEvent {
    private final MovingObjectPosition hitResult;
    private final boolean fromAura;

    public LeftClickEvent(final MovingObjectPosition hitResult) {
        this(hitResult, false);
    }

    public LeftClickEvent(final MovingObjectPosition hitResult, final boolean fromAura) {
        this.hitResult = hitResult;
        this.fromAura = fromAura;
    }

    public MovingObjectPosition getHitResult() {
        return this.hitResult;
    }

    // aura-fired clicks already passed their own server-rotation raytrace; flagging this lets
    // RotationUtil's manual-tolerance guard sit it out instead of cancelling a valid hit.
    public boolean isFromAura() {
        return this.fromAura;
    }
}
