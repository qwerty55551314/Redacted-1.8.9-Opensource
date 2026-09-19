package redacted.client.module.impl.render;

import net.minecraft.util.MathHelper;
import org.lwjglx.input.Keyboard;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;

// Look around in third person without turning your body. The mouse drives a separate
// camera yaw/pitch; the player's real rotation is left alone so placement, clicks and
// packets all keep aiming forward. EntityRenderer swaps in these camera angles only
// around orientCamera (see setupCameraTransform), so it's purely visual.
public class FreeLook extends Module {

    public static FreeLook INSTANCE;

    private final Freecam freecam; // don't fight Freecam over the mouse if both are on

    private float cameraYaw, cameraPitch;
    private boolean initialized;

    public FreeLook() {
        super("Free Look", "Look around in third person without turning your body",
                Keyboard.KEY_NONE, Category.RENDER);
        INSTANCE = this;
        freecam = Client.getInstance().getModuleManager().getModule(Freecam.class);
    }

    @Override
    public void onDisable() {
        initialized = false;
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!event.isPre()) return;
        boolean run = shouldRun();
        if (run && !initialized) {        // just entered 3rd person while enabled -> seed from real look
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            initialized = true;
        } else if (!run) {
            initialized = false;          // reseed fresh next time we re-enter
        }
    }

    private boolean shouldRun() {
        return isEnabled() && mc.thePlayer != null
                && mc.gameSettings.thirdPersonView > 0
                && !(freecam != null && freecam.isEnabled());
    }

    // read from EntityRenderer on the render thread; keep side-effect free

    public static boolean isActive() {
        FreeLook fl = INSTANCE;
        return fl != null && fl.initialized && fl.shouldRun();
    }

    public static float getCameraYaw()   { return INSTANCE.cameraYaw; }
    public static float getCameraPitch() { return INSTANCE.cameraPitch; }

    // mirrors vanilla Entity.setAngles math (0.15 factor, pitch subtracts, clamp)
    public static void addRotation(float yaw, float pitch) {
        FreeLook fl = INSTANCE;
        fl.cameraYaw += yaw * 0.15F;
        fl.cameraPitch -= pitch * 0.15F;
        fl.cameraPitch = MathHelper.clamp_float(fl.cameraPitch, -90.0F, 90.0F);
    }
}
