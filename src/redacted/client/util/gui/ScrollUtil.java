package redacted.client.util.gui;

import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;

/**
 * Smooth-scroll animator only. Wheel input is deliberately NOT polled here:
 * polling the global wheel from a per-pane animator both double-applies
 * scrolls (the owning GUI already polls bounded to its list rect) and made
 * the pane scroll with the pointer anywhere on screen. GUIs own their input;
 * they call {@link #setTarget} and then {@link #onRender()} each frame.
 */
public class ScrollUtil {
    private double scroll, targetScroll, maxScroll;
    private final Animation animation = new Animation(Easing.EASE_OUT_QUINT, 300);

    public void onRender() {
        targetScroll = clamp(targetScroll);
        animation.run(targetScroll);
        scroll = clamp(animation.getValue());
    }

    public double getScroll() { return scroll; }
    public double getTarget() { return targetScroll; }

    public void setMax(double max) {
        this.maxScroll = Math.min(0, max);
        this.targetScroll = clamp(this.targetScroll);
        this.scroll = clamp(this.scroll);
    }

    public void setTarget(double target) {
        this.targetScroll = clamp(target);
        animation.reset();
    }

    private double clamp(double value) {
        return Math.max(maxScroll, Math.min(0, value));
    }
}
