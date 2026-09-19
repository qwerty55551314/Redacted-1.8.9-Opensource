package redacted.client.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.event.impl.LeftClickEvent;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.Randoms;
import redacted.client.util.entity.EntityUtil;
import redacted.client.util.time.StopWatch;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.RangeValue;
import redacted.util.CombatManager;

import java.util.Random;

public class Triggerbot extends Module {
    // gaussian roll, not uniform: uniform intervals sliced by the tick boundary collapse into
    // a couple of discrete delay values, which stands out badly in any distribution analysis
    private final RangeValue delay = new RangeValue("Delay (ms)", this, 100.0, 250.0, 40.0, 500.0, 5.0)
            .describe("Ms range before auto-attacking what you look at");

    // entity types come from the shared Target Filter module (disabled filter = target everything)
    private final BooleanValue missedBeats = new BooleanValue("Missed Beats", this, true)
            .describe("Occasional skipped beat");
    private final BooleanValue doubleClicks = new BooleanValue("Double Click", this, true)
            .describe("Occasional follow-up click on the next tick");
    private final BooleanValue respectHurtTime = new BooleanValue("Respect Hurt Time", this, true)
            .describe("Hold fire while the target shakes off hits");

    // hurtResistantTime runs 20 ticks but only blocks damage for the first half
    private static final int HURT_WINDOW_TICKS = 10;

    private final StopWatch timer = new StopWatch();
    private final Random random = new Random();
    private long nextDelay = 200L;
    private int acquiredEntityId = -1;      // entity the reaction clock is running against
    private int lastAttackedEntityId = -1;
    private long lastAttackWorldTime = -1000L;
    private boolean pendingFollowup;        // armed double-click beat; immune targets don't swallow it

    public Triggerbot() {
        super("TriggerBot",
                "Automatically attacks when an enemy enters your crosshair",
                Keyboard.KEY_NONE, Category.COMBAT);
    }

    @Override
    public void onEnable() {
        acquiredEntityId = -1;
        lastAttackedEntityId = -1;
        pendingFollowup = false;
        nextDelay = (long) Randoms.gaussian(random, delay.getRangeMin(), delay.getRangeMax());
        timer.reset();
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;

        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null || !mc.inGameHasFocus) {
            dropAcquisition();
            return;
        }
        if (mc.thePlayer.isUsingItem()) {
            dropAcquisition();
            return;
        }
        if (CombatManager.entityAttackedThisTick()) return;

        // interaction pick == objectMouseOver normally; follows SilentAim's redirect when active.
        // PRE runs before getMouseOver(1.0F) so this frame is end-of-last-tick, which matches the
        // rotation the server has on file anyway.
        final MovingObjectPosition hit = mc.interactObjectMouseOver;
        EntityLivingBase target = null;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityLivingBase) {
            final EntityLivingBase candidate = (EntityLivingBase) hit.entityHit;
            if (candidate.isEntityAlive()
                    && EntityUtil.isValidTarget(candidate)
                    && TargetFilter.allows(candidate)) {
                target = candidate;
            }
        }

        // the reaction clock starts when the crosshair lands on a new enemy. letting a stale timer
        // fire on the entry tick produces sub-50ms reactions, which is the easiest thing there is
        // to correlate attacks against.
        if (target == null || target.getEntityId() != acquiredEntityId) {
            dropAcquisition();
            if (target != null) {
                acquiredEntityId = target.getEntityId();
                nextDelay = (long) Randoms.gaussian(random, delay.getRangeMin(), delay.getRangeMax());
                timer.reset();
            }
            return;
        }

        // hold fire through the immunity window but keep the clock running, so the hit lands as
        // soon as the window opens. an armed double-click beat goes out regardless -- vanilla
        // doesn't check immunity before swinging either.
        if (!pendingFollowup && inHurtWindow(target)) return;

        if (!timer.finished(nextDelay)) return;

        if (missedBeats.getValue() && random.nextInt(9) == 0) {
            pendingFollowup = false;
            nextDelay = (long) Randoms.gaussian(random, delay.getRangeMin(), delay.getRangeMax());
            timer.reset();
            return;
        }

        LeftClickEvent click = Client.getInstance().getEventManager().call(new LeftClickEvent(hit));
        pendingFollowup = false;
        if (!click.isCancelled()) {
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, target);
            lastAttackedEntityId = target.getEntityId();
            lastAttackWorldTime = mc.theWorld.getTotalWorldTime();
            CombatManager.markEntityAttack();
        }

        nextDelay = (long) Randoms.gaussian(random, delay.getRangeMin(), delay.getRangeMax());
        if (doubleClicks.getValue() && random.nextInt(9) == 1) {
            nextDelay = Math.min(nextDelay, 45L);
            pendingFollowup = true;
        }
        timer.reset();
    }

    private void dropAcquisition() {
        if (acquiredEntityId != -1) pendingFollowup = false;
        acquiredEntityId = -1;
    }

    private boolean inHurtWindow(final EntityLivingBase target) {
        if (!respectHurtTime.getValue()) return false;
        return target.getEntityId() == lastAttackedEntityId
                && mc.theWorld.getTotalWorldTime() - lastAttackWorldTime < HURT_WINDOW_TICKS;
    }

}
