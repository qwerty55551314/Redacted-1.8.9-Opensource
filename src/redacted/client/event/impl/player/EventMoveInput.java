package redacted.client.event.impl.player;

import redacted.client.event.Event;

public final class EventMoveInput extends Event {
    private final double sneakMultiplier;
    private float moveForward;
    private float moveStrafe;
    private boolean jump;
    private boolean sneak;

    public EventMoveInput(final float moveForward, final float moveStrafe,
                          final boolean jump, final boolean sneak,
                          final double sneakMultiplier) {
        this.moveForward = moveForward;
        this.moveStrafe = moveStrafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sneakMultiplier = sneakMultiplier;
    }

    public float getMoveForward() {
        return this.moveForward;
    }

    public void setMoveForward(final float moveForward) {
        this.moveForward = moveForward;
    }

    public float getMoveStrafe() {
        return this.moveStrafe;
    }

    public void setMoveStrafe(final float moveStrafe) {
        this.moveStrafe = moveStrafe;
    }

    public boolean isJump() {
        return this.jump;
    }

    public void setJump(final boolean jump) {
        this.jump = jump;
    }

    public boolean isSneak() {
        return this.sneak;
    }

    public void setSneak(final boolean sneak) {
        this.sneak = sneak;
    }

    public double getSneakMultiplier() {
        return this.sneakMultiplier;
    }

    // slowdown already ran in updatePlayerMoveState before this event fires, so when we force the
    // sneak ourselves we have to apply the 0.3 by hand
    public void applyForcedSneakSlowdown() {
        final boolean wasSneaking = this.sneak;
        this.sneak = true;
        if (!wasSneaking) {
            final float m = (float) this.sneakMultiplier;
            this.moveForward *= m;
            this.moveStrafe *= m;
        }
    }
}
