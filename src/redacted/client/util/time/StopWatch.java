package redacted.client.util.time;

public class StopWatch {
    private long lastMillis = System.currentTimeMillis();

    public void reset() {
        this.lastMillis = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - this.lastMillis;
    }

    public boolean finished(long target) {
        return getElapsedTime() >= target;
    }
}
