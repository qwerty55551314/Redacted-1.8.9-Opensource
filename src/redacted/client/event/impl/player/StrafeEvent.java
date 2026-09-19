package redacted.client.event.impl.player;

import redacted.client.event.CancellableEvent;

public final class StrafeEvent extends CancellableEvent {

    private float forward;
    private float strafe;
    private float friction;
    private float yaw;

    public StrafeEvent(final float forward, final float strafe, final float friction, final float yaw) {
        this.forward = forward;
        this.strafe = strafe;
        this.friction = friction;
        this.yaw = yaw;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(final float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(final float strafe) {
        this.strafe = strafe;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(final float friction) {
        this.friction = friction;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(final float yaw) {
        this.yaw = yaw;
    }
}
