package redacted.client.util.animation;

public class Animation {
    private Easing easing;
    private long duration;
    private long startTime;

    private double startValue;
    private double destinationValue;
    private double value;
    private boolean finished;

    public Animation(Easing easing, long duration) {
        this.easing = easing;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
    }

    public void run(double destinationValue) {
        long millis = System.currentTimeMillis();
        if (this.destinationValue != destinationValue) {
            this.destinationValue = destinationValue;
            this.reset();
        } else {
            this.finished = millis - this.duration > this.startTime;
            if (this.finished) {
                this.value = destinationValue;
                return;
            }
        }

        double progress = this.getProgress();
        double result = applyEasing(progress);

        if (this.value > destinationValue) {
            this.value = this.startValue - (this.startValue - destinationValue) * result;
        } else {
            this.value = this.startValue + (destinationValue - this.startValue) * result;
        }
    }

    private double applyEasing(double progress) {
        return easing.apply(progress);
    }

    public double getProgress() {
        return (double) (System.currentTimeMillis() - this.startTime) / (double) this.duration;
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.startValue = value;
        this.finished = false;
    }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public void setDuration(long duration) { this.duration = duration; }
    public void setEasing(Easing easing) { this.easing = easing; }
    public boolean isFinished() { return finished; }
}
