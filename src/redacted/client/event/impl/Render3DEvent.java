package redacted.client.event.impl;

import redacted.client.event.Event;

public class Render3DEvent extends Event {
    private final float partialTicks;
    private final int pass;

    public Render3DEvent(final float partialTicks, final int pass) {
        this.partialTicks = partialTicks;
        this.pass = pass;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }

    public int getPass() {
        return this.pass;
    }
}
