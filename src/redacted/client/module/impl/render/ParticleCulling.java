package redacted.client.module.impl.render;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.particle.EntityPickupFX;
import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.optimization.ParticleCullingThread;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

/*
 * ParticleCulling port (bl4ckscor3, my-life-is-bad 1.8.9 port) -- async thread
 * culls particles outside the frustum and, optionally, particles hidden behind
 * opaque blocks (blockBuffer threshold). EntityPickupFX is always exempt (it is
 * a UI-ish pickup animation, not a world particle). New particles start culled
 * so they never flash in for one frame behind a wall.
 */
public class ParticleCulling extends Module {

    private static ParticleCulling instance;

    public static ParticleCulling get() {
        if (instance == null) {
            instance = Client.getInstance().getModuleManager().getModule(ParticleCulling.class);
        }
        return instance;
    }

    /* cheap main-thread gate used by the EffectRenderer hooks */
    public static boolean isActive() {
        ParticleCulling m = get();
        return m != null && m.isEnabled();
    }

    /* camera captured in RenderGlobal.setupTerrain (main thread), read by the
       culling thread */
    public static volatile ICamera camera;

    /* volatile snapshot refreshed every tick on the main thread */
    public static final Config config = new Config();

    public static final class Config {
        public volatile boolean cullBehindBlocks = true;
        public volatile boolean cullInSpectator = true;
        public volatile boolean cullBehindGlass = false;
        public volatile double blockBuffer = 1.0D;
    }

    private final BooleanValue cullBehindBlocks = new BooleanValue("Cull Behind Blocks", this, true)
        .describe("Cull particles that are in view but hidden behind opaque blocks");
    private final BooleanValue cullInSpectator = new BooleanValue("Cull in Spectator", this, true)
        .describe("Keep culling in spectator mode (off = screenshots show all particles)");
    private final BooleanValue cullBehindGlass = new BooleanValue("Cull Behind Glass", this, false)
        .describe("Treat transparent blocks (glass) as occluders too");
    private final NumberValue blockBuffer = new NumberValue("Block Buffer", this, 1.0, 1.0, 50.0, 1.0)
        .describe("Minimum blocks of occlusion before a particle gets culled");

    private ParticleCullingThread thread;

    public ParticleCulling() {
        super("Particle Culling", "Cull off-screen and wall-hidden particles (ParticleCulling port)", 0, Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (this.thread == null || !this.thread.isAlive()) {
            this.thread = new ParticleCullingThread(config);
            this.thread.start();
        }
        this.syncConfig();
    }

    @Override
    public void onDisable() {
        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (event.isPre()) {
            this.syncConfig();
        }
    }

    private void syncConfig() {
        config.cullBehindBlocks = this.cullBehindBlocks.getValue();
        config.cullInSpectator = this.cullInSpectator.getValue();
        config.cullBehindGlass = this.cullBehindGlass.getValue();
        config.blockBuffer = this.blockBuffer.getValue();
    }

    /* EntityPickupFX renders as a pickup animation rather than a world particle;
       culling it looks wrong, so it is always exempt (reference default) */
    public static boolean isIgnored(Class<?> particleClass) {
        return EntityPickupFX.class.isAssignableFrom(particleClass);
    }

}
