package redacted.client.event.impl.player;

import redacted.client.event.Event;

public final class MotionEvent extends Event {
    // prev* are taken at construction so callers can subclass behaviour later without
    // a signature change at the EntityPlayerSP mixin site -- keep them even though
    // nothing reads them today.
    @SuppressWarnings("unused") private final float prevYaw, prevPitch;
    @SuppressWarnings("unused") private final double prevPosX, prevPosY, prevPosZ;

    private double posX, posY, posZ;
    private float yaw, pitch;
    private boolean ground;
    private boolean sprinting;
    private boolean pre;

    public MotionEvent(final float prevYaw, final float prevPitch,
                       final double posX, final double posY, final double posZ,
                       final double prevPosX, final double prevPosY, final double prevPosZ,
                       final float yaw, final float pitch,
                       final boolean sprinting,
                       final boolean ground) {
        this.prevYaw = prevYaw;
        this.prevPitch = prevPitch;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.prevPosX = prevPosX;
        this.prevPosY = prevPosY;
        this.prevPosZ = prevPosZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.sprinting = sprinting;
        this.ground = ground;
        this.pre = true;
    }

    public boolean isPre() {
        return pre;
    }

    public void setPost() {
        this.pre = false;
    }

    public double getPosX() {
        return posX;
    }

    public void setPosX(final double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(final double posY) {
        this.posY = posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public void setPosZ(final double posZ) {
        this.posZ = posZ;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(final float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(final float pitch) {
        this.pitch = pitch;
    }

    public boolean isOnGround() {
        return ground;
    }

    public void setOnGround(final boolean ground) {
        this.ground = ground;
    }

    public boolean isSprinting() {
        return this.sprinting;
    }

    public void setSprinting(final boolean sprinting) {
        this.sprinting = sprinting;
    }
}
