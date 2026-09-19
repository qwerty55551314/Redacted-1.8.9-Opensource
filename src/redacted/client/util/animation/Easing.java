package redacted.client.util.animation;

public enum Easing {
    LINEAR,
    EASE_OUT_QUAD,
    EASE_OUT_EXPO,
    EASE_OUT_QUINT,
    EASE_IN_EXPO;

    public double apply(double progress) {
        switch (this) {
            case EASE_OUT_QUAD:
                return progress * (2 - progress);
            case EASE_OUT_QUINT:
                return 1 + (--progress) * progress * progress * progress * progress;
            case EASE_OUT_EXPO:
                return progress == 1 ? 1 : 1 - Math.pow(2, -10 * progress);
            case EASE_IN_EXPO:
                return progress <= 0 ? 0 : Math.pow(2, 10 * (progress - 1));
            case LINEAR:
            default:
                return progress;
        }
    }
}
