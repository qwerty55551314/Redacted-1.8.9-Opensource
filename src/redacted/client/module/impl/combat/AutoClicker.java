package redacted.client.module.impl.combat;

import net.minecraft.client.settings.GameSettings;
import org.lwjglx.input.Keyboard;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.RangeValue;
import redacted.util.CombatManager;
import java.util.Random;

public class AutoClicker extends Module {

    private final RangeValue cps = new RangeValue("CPS", this, 8.0, 12.0, 1.0, 20.0, 1.0).describe("Clicks-per-second range");
    // human error signals, both on by default: ~11% double-click beats and ~11% skipped beats.
    // they shape the interval stream so Intave's statistical checks have nothing regular to lock
    // onto; turning them off gives a cleaner (and more detectable) stream.
    private final BooleanValue doubleClicks = new BooleanValue("Double Click", this, true)
            .describe("Occasional ~50ms double-click beats (human signal, Intave scores them negatively)");
    private final BooleanValue missClicks = new BooleanValue("Miss Clicks", this, true)
            .describe("Occasional skipped beats / micro-pauses (keeps gap ticks in the stream)");

    private final StopWatch timer = new StopWatch();
    private final Random random = new Random();
    private long delay;
    private int ticksDown;   // consecutive ticks the attack key has been held

    // time-varying CPS process (Unfair DelayGenerator style): slow AR(1) wander terms + decaying
    // burst energy + fast noise. The VARIANCE of the interval stream changes over time, so Intave
    // Fluctuation's regular spike/drop detector has no steady cadence to lock onto (the old fixed
    // random-walk drift never changed its variance -- the one check that can bite at 8-12 CPS).
    private double tempo, drift, impulse, chaos;
    private int burstTicks, restTicks;   // remaining ticks of the current burst/rest phase

    public AutoClicker() {
        super("AutoClicker", "Automated clicking", Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public String getArraylistSuffix() {
        return " [ " + (int) cps.getRangeMin() + " - " + (int) cps.getRangeMax() + " ]";
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (event.isPost()) return;
        if (mc.thePlayer == null || mc.currentScreen != null || !mc.inGameHasFocus) return;
        if (mc.thePlayer.isUsingItem()) return;
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindAttack)) {
            ticksDown = 0;
            return;
        }

        // human startup: never click on the first tick of a key hold (Rise pattern) -- a clicker
        // that fires instantly on press is one of the most obvious tells there is
        ticksDown++;
        if (ticksDown <= 1) {
            delay = nextDelay();
            timer.reset();
            return;
        }

        if (!timer.finished(delay)) return;

        // occasional skipped beat (missed click / micro-pause) -- creates the gap ticks that keep
        // Intave Bursts streaks and NCP's 0.5s window under their limits
        if (missClicks.getValue() && random.nextInt(9) == 0) {
            delay = nextDelay();
            timer.reset();
            return;
        }

        // someone already attacked an entity this tick; don't add a second C02.
        // block hits don't stamp the epoch, so mining is unaffected.
        if (CombatManager.entityAttackedThisTick()) return;

        // vanilla does the rest: raytrace, entity/block/miss, LeftClickEvent, swing, C02.
        // zero the counter BEFORE every click (Tenacity/Pandaware pattern): vanilla's miss lockout
        // (a miss sets leftClickCounter=10 = 500ms) throttled high-CPS bands down hard -- at 18-20
        // the stream was delivering ~13-14. the counter only gates clickMouse, so zeroing it lets
        // the scheduled delay be the one true rate limiter.
        mc.leftClickCounter = 0;
        mc.clickMouse();

        delay = nextDelay();
        // ~11% of beats double-click: fire again 1-2 ticks out (a 50-100ms pair that reads as a
        // genuine double-click -- Intave's Bursts scores double-clicks negatively). the 1-vs-2 tick
        // roll spreads the pair interval so the histogram doesn't grow a single 50ms comb line.
        if (doubleClicks.getValue() && random.nextInt(9) == 1) delay = Math.min(delay, 45L + (random.nextBoolean() ? 0L : 50L));
        timer.reset();
    }

    // continuous-CPS interval with a time-varying distribution; keeps the configured band (defaults
    // 8-12) with a little overshoot on either side, exactly like the reference generators.
    private long nextDelay() {
        final double min = cps.getRangeMin();
        final double max = cps.getRangeMax();
        final double center = (min + max) / 2.0;
        final double range = Math.max(0.35, (max - min) / 2.0);
        final double span = Math.max(1.0, max - min);

        // probabilistic burst/rest phases -- spikes and drops arrive at IRREGULAR times
        if (burstTicks > 0) {
            burstTicks--;
        } else if (restTicks > 0) {
            restTicks--;
        } else if (random.nextInt(10) < 3) {
            if (random.nextBoolean()) {
                burstTicks = 2 + random.nextInt(5);                 // 2..6 ticks
                impulse += 0.12 + random.nextDouble() * 0.30;       // +0.12..0.42 CPS
            } else {
                restTicks = 1 + random.nextInt(4);                  // 1..4 ticks
                impulse -= 0.10 + random.nextDouble() * 0.28;       // -0.10..0.38 CPS
            }
        }

        // AR(1) wander terms + decaying burst energy + fast noise (Unfair DelayGenerator)
        tempo = tempo * 0.68 + random.nextGaussian() * 0.22;
        drift = drift * 0.82 + random.nextGaussian() * 0.16;
        impulse *= 0.55;
        chaos = chaos * 0.73 + random.nextGaussian() * 0.41 + (random.nextDouble() - 0.5) * 0.35;

        double cpsValue = center
                + random.nextGaussian() * Math.max(0.06, range * 0.1)          // micro jitter
                + tempo + drift + impulse + chaos * 0.12
                + (burstTicks > 0 ? range * (0.18 + random.nextDouble() * 0.24) : 0.0)
                + (restTicks > 0 ? -range * (0.22 + random.nextDouble() * 0.36) : 0.0);

        // occasional outlier keeps the distribution tailed (Intave Kurtosis)
        if (random.nextInt(12) == 0) cpsValue += random.nextGaussian() * Math.max(0.08, span * 0.12);

        // preserve the configured band (Unfair clamps the same way)
        final double lo = min - Math.max(0.75, range * 0.35);
        final double hi = max + Math.max(0.75, range * 0.45);
        cpsValue = Math.max(lo, Math.min(hi, cpsValue));

        final long d = (long) (1000.0 / cpsValue + random.nextGaussian() * 4.0);
        return Math.max(40L, Math.min(220L, d));
    }
}
