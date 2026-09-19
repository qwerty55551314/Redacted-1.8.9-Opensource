package redacted.client.util.player;

import net.minecraft.client.Minecraft;
import redacted.client.util.Randoms;

// burst/recover timer engine used by Criticals' Timer mode.
// drift = how many ms ahead of real time we've run; each burst tick accrues
// (power-1)*50ms and recovery bleeds it back below 1.0x so the server sees a
// wash instead of a permanent speedup.
public final class TimerDriftController {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private int sleepTicks;
    private double unresolvedDrift;

    // doesn't touch timerSpeed -- callers reset that themselves on disable
    public void reset() {
        sleepTicks = 0;
        unresolvedDrift = 0.0;
    }

    // true while we're in the rest window after a burst (eats one tick per call)
    public boolean sleeping() {
        if (sleepTicks > 0) {
            sleepTicks--;
            mc.timer.timerSpeed = 1.0F;
            return true;
        }
        return false;
    }

    public boolean overBudget(final double budget) {
        return unresolvedDrift >= budget;
    }

    public void burst(final float power, final int restTicks) {
        unresolvedDrift += (power - 1.0) * 50.0;
        mc.timer.timerSpeed = power;
        sleepTicks = restTicks;
    }

    public void recover(final float slowPower) {
        if (unresolvedDrift > 1.0) {
            final float recoverySpeed = (float) Randoms.between(slowPower, Math.min(slowPower + 0.05, 0.99));
            mc.timer.timerSpeed = recoverySpeed;
            unresolvedDrift -= (1.0 - recoverySpeed) * 50.0;
            if (unresolvedDrift < 1.0) unresolvedDrift = 0.0;
        } else {
            mc.timer.timerSpeed = 1.0F;
        }
    }
}
