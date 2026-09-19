package redacted.client.util.notification;

public enum NotificationType {
    INFO(0xFF4794FD),
    WARNING(0xFFFFAA00),
    ERROR(0xFFFF4444),
    TIMER(0xFF1cff03);

    private final int color;

    NotificationType(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
