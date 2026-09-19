package redacted.client.module.impl.combat;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import org.lwjglx.input.Keyboard;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.player.MovementUtils;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.ModeValue;
import redacted.client.value.impl.NumberValue;
import redacted.util.RaycastUtil;

import java.util.Random;

public class Reach extends Module {
    private static final String POLAR = "Polar";

    /**
     * Eye-to-hitbox ceiling that stays UNDER Grim/Polar's 3.0 budget rather than sitting on it.
     * We measure at EventTick.PRE (before this tick's movement) against `loadedEntityList`
     * positions, which for other players are EntityOtherPlayerMP's 3-tick smoothed interpolation --
     * both lag the frame the server reconstructs, so its number can read ~0.1 higher than ours.
     * Sitting on 3.0 means those ticks get the C02 silently cancelled: the swing plays, nothing
     * lands. Shared with SilentAura's fallback gate so both paths use one number.
     */
    public static final double GRIM_SAFE_REACH = 2.90D;
    private final ModeValue mode = new ModeValue("Mode", this, "Grim", "Grim", "Polar", "Vulcan").describe("Grim/Polar eye-to-box gate vs the Vulcan budget gate");

    // Grim: extends the entity SEARCH range; the gate in canHit() decides what actually
    // lands, so raising this never pushes a hit past the anticheat budget.
    private final NumberValue extension = new NumberValue("Extension", this, 0.3D, 0.0D, 0.4D, 0.1D, mode, "Grim").describe("Blocks added to the 3.0 entity search (the safe gate still caps what lands)");

    // Vulcan: 3D raytrace/target budget. The gate decides what actually lands. Capped at 4.5 - the
    // realistic Vulcan-safe ceiling (KB + vertical/facing); the gate still withholds over-budget hits.
    private final NumberValue reach = new NumberValue("Reach", this, 4.5D, 3.0D, 4.5D, 0.1D, mode, "Vulcan").describe("Max reach the Vulcan gate allows");
    // on = stay inside ReachB's own budget (never flags). off = raw reach (flags Vulcan; non-Vulcan servers only).
    private final BooleanValue safeGate = new BooleanValue("Safe Gate", this, true, mode, "Vulcan").describe("Withhold hits that exceed Vulcan's budget");

    // per-attack budget roll so hits don't sit on a constant edge (the tell NCP's dynamic shrink /
    // Truthful's buffer / FlopAC's average all catch)
    private final Random gateRandom = new Random();

    public Reach() {
        super("Reach", "Extends entity attack reach", Keyboard.KEY_NONE, Category.COMBAT);
    }

    public boolean isVulcan() {
        return mode.getValue().equals("Vulcan");
    }

    // Grim/Polar: 3.0 + search extension (Polar uses a fixed search extension). Vulcan: the configured 3D budget.
    public double getEffectiveReach() {
        return isVulcan() ? reach.getValue() : 3.0D + getExtension();
    }

    public double getExtension() {
        return POLAR.equals(mode.getValue()) ? 0.2D : extension.getValue();
    }

    // Gate what actually lands. Grim/Polar: eye-to-hitbox 3D distance vs the real 1.8 ceiling
    // (modern ACs measure eye -> box, grant a ~0.1-0.13 hitbox margin for 1.8 clients, and have no
    // knockback exemption or yaw bonus -- that was the old Vulcan reconstruction leaking into the
    // default path and making the gate swing between below-vanilla and flag-guaranteed).
    public boolean canHit(final EntityLivingBase target) {
        if (target == null || mc.thePlayer == null) return true;
        if (isVulcan()) return vulcanGate(target);
        // Polar's own reach parameters aren't public; it's a modern strict AC like Grim, so ride the
        // same eye-to-box metric a touch tighter.
        return grimGate(target, POLAR.equals(mode.getValue()) ? GRIM_SAFE_REACH - 0.02D : GRIM_SAFE_REACH);
    }

    // eye -> unexpanded hitbox, 3D -- Grim's own metric. The ceiling is rolled down per attack so
    // hits don't sit on a constant edge (the tell NCP's dynamic shrink / Truthful's buffer /
    // FlopAC's average all catch). Previously this added a margin ON TOP of 3.0, which put the gate
    // at 2.98-3.08 -- on or over Grim's line, with nothing left for the staleness noted above.
    private boolean grimGate(final EntityLivingBase target, final double ceiling) {
        final double eyeReach = RaycastUtil.reach(target, mc.thePlayer.getPositionEyes(1.0F));
        return eyeReach <= ceiling - gateRandom.nextDouble() * 0.05D;
    }

    // Vulcan reach is a 2D horizontal distance vs the target's LIVE server position (Y is zeroed by the
    // check, so vertical reach is free). Only allow the hit when that horizontal distance stays inside
    // ReachB's own server-frame budget. A Knockback weapon exempts ReachA entirely, so we ride the looser
    // ReachB up toward its 6.0 cap; without it the flat ReachA wall (~3.0) binds.
    private boolean vulcanGate(final EntityLivingBase target) {
        if (!safeGate.getValue() || target == null || mc.thePlayer == null) return true;

        final double dx = mc.thePlayer.posX - target.posX;
        final double dz = mc.thePlayer.posZ - target.posZ;
        final double horiz = Math.sqrt(dx * dx + dz * dz);          // center-to-center, Y ignored
        final double dy = Math.abs(mc.thePlayer.posY - target.posY);

        // ReachB maxReach, reconstructed conservatively. We intentionally do NOT claim the target-speed
        // term (+spd*0.66): it desyncs with the target's live-vs-server position, so we cover that lag as
        // margin below instead. dY (vertical) and the yaw-alignment bonus are stable reads.
        double bBudget = 3.0D + 0.05D
                + dy * 0.9D                                          // free vertical (Y is excluded from the check's distance)
                + (yawAligned(target) ? 1.25D : 0.0D);               // both bodies roughly facing each other
        if (bBudget > 6.0D) bBudget = 6.0D;

        final boolean knockback = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        // ReachB subtracts a 0.565 pad; ReachA (no KB exemption) is a flat 3.0 + 0.52 pad with no leniency.
        final double limit = knockback ? bBudget + 0.565D
                : Math.min(3.0D + 0.52D, bBudget + 0.565D);

        // margin covers both ends' live-vs-server-frame lag: our last-reported pos and the target's own
        // ~1-tick-old tracked pos. Subtracting both keeps us inside the budget even when someone rushes us.
        final double margin = 0.15D + MovementUtils.horizontalSpeed(mc.thePlayer) + MovementUtils.horizontalSpeed(target);
        return horiz <= limit - margin;
    }

    private boolean yawAligned(final EntityLivingBase target) {
        // ReachB's +1.25: yaws roughly opposed (you and the target facing each other).
        final float diff = Math.abs(180.0F - Math.abs(mc.thePlayer.getLastReportedYaw() - target.rotationYaw));
        return diff > 100.0F;
    }
}