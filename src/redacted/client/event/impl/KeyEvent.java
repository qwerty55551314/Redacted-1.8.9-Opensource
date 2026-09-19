package redacted.client.event.impl;

import redacted.client.event.Event;

public class KeyEvent extends Event {
    private final int key;
    private final boolean pressed;

    public KeyEvent(final int key, final boolean pressed) {
        this.key = key;
        this.pressed = pressed;
    }

    public int getKey() {
        return this.key;
    }

    public boolean isPressed() {
        return this.pressed;
    }
}
