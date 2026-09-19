package redacted.client.event.impl;

import redacted.client.event.Event;

public class EventFrame extends Event {
    private final float partialTicks;

    public EventFrame(final float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }
}
