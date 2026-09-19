package redacted.client.util.notification;

import redacted.client.util.animation.Animation;
import redacted.client.util.animation.Easing;

public class Notification {
    private final NotificationType type;
    private final String description;
    private final long durationMs;
    private final long startTime;
    private final Animation animation;

    // timer-text cache: the label is re-rendered per frame, but String.format is only
    // re-run when the displayed tenth-of-second actually changes
    private long lastTimerKey = Long.MIN_VALUE;
    private String lastTimerText;

    public Notification(NotificationType type, String description, float seconds) {
        this(type, description, seconds, 0.0F);
    }
    
    public Notification(NotificationType type, String description, float seconds, float delay) {
        this.type = type;
        this.description = description;
        this.durationMs = (long) (seconds * 1000F);
        this.startTime = System.currentTimeMillis() + (long) (delay * 1000F);
        this.animation = new Animation(Easing.EASE_OUT_EXPO, 300);
    }

    public NotificationType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getStartTime() {
        return startTime;
    }

    public Animation getAnimation() {
        return animation;
    }
    
    public double getRemainingRatio() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= durationMs) return 0.0;
        return 1.0 - (double) elapsed / durationMs;
    }

    /** Caches the formatted remaining-time label; only rebuilds on a new tenth tick. */
    public String getTimerText(double remainingSec, java.util.function.DoubleFunction<String> formatter) {
        final long key = Math.round(remainingSec * 10.0);
        if (lastTimerText == null || lastTimerKey != key) {
            lastTimerKey = key;
            lastTimerText = formatter.apply(remainingSec);
        }
        return lastTimerText;
    }
}
