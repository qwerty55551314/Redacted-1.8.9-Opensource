package redacted.client.event.impl;

import redacted.client.event.Event;

public class EventTick extends Event {
    private final State state;

    public EventTick(final State state) {
        this.state = state;
    }

    public boolean isPre() {
        return this.state == State.PRE;
    }

    public boolean isPost() {
        return this.state == State.POST;
    }

    public enum State {
        PRE,
        POST
    }
}
