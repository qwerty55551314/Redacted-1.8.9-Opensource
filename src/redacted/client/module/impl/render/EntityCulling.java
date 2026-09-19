package redacted.client.module.impl.render;

import redacted.client.Client;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;
import redacted.client.module.Category;
import redacted.client.module.Module;
import redacted.client.util.optimization.EntityCullingThread;
import redacted.client.value.impl.BooleanValue;
import redacted.client.value.impl.NumberValue;

/*
 * EntityCulling port (meldexun.entityculling, tr7zw async raytrace) -- CPU path.
 * Async daemon ray-traces entity + tile entity AABBs against the opaque-block
 * grid; hidden ones are skipped by RenderManager / TileEntityRendererDispatcher.
 * OptiFine shadow pass gets its own less-aggressive cull (shadowCulled) so
 * shader shadow maps skip hidden entities without popping. PvP-safe by default:
 * players, bosses, named entities and the view entity always render.
 * Defaults follow the reference mod (everything ON when the module is enabled).
 */
public class EntityCulling extends Module {

    private static EntityCulling instance;

    public static EntityCulling get() {
        if (instance == null) {
            instance = Client.getInstance().getModuleManager().getModule(EntityCulling.class);
        }
        return instance;
    }

    /* cheap main-thread gate used by the vanilla render hooks */
    public static boolean isActive() {
        EntityCulling m = get();
        return m != null && m.isEnabled();
    }

    /* volatile snapshot refreshed every tick on the main thread; the async
       culling thread reads these cross-thread */
    public static final Config config = new Config();

    public static final class Config {
        public volatile boolean disabledInSpectator = true;
        public volatile boolean alwaysRenderPlayers = true;
        public volatile boolean alwaysRenderBosses = true;
        public volatile boolean alwaysRenderNamed = true;
        public volatile boolean cullTileEntities = true;
        public volatile boolean cullShadows = true;
        public volatile boolean enableRaytraceCache = false;
        public volatile double sizeLimit = 16.0D;
        public volatile double raytraceThreshold = 1.0D;
        public volatile double shadowDiff = 4.0D;
        public volatile double distanceLimitAdder = 16.0D;
    }

    private final BooleanValue disabledInSpectator = new BooleanValue("Disabled in Spectator", this, true)
        .describe("Disable culling in spectator mode (screenshots render everything)");
    private final BooleanValue alwaysRenderPlayers = new BooleanValue("Always Render Players", this, true)
        .describe("Never cull players, even behind walls");
    private final BooleanValue alwaysRenderBosses = new BooleanValue("Always Render Bosses", this, true)
        .describe("Never cull the Ender Dragon or Wither");
    private final BooleanValue alwaysRenderNamed = new BooleanValue("Always Render Named", this, true)
        .describe("Never cull entities that have a name tag");
    private final BooleanValue cullTileEntities = new BooleanValue("Cull Tile Entities", this, true)
        .describe("Also cull hidden tile entities (chests, signs, beacons, ...)");
    private final BooleanValue cullShadows = new BooleanValue("Cull Shader Shadows", this, true)
        .describe("Skip hidden entities in the OptiFine shadow pass (shaders only)");
    private final BooleanValue enableRaytraceCache = new BooleanValue("Raytrace Cache", this, false)
        .describe("Cache per-block raytrace results (saves CPU on weak machines)");
    private final NumberValue sizeLimit = new NumberValue("Cull Size Limit", this, 16.0, 1.0, 64.0, 1.0)
        .describe("Entities/tile entities with a bigger bounding box are never culled");
    private final NumberValue raytraceThreshold = new NumberValue("Raytrace Threshold", this, 1.0, 0.0, 16.0, 0.5)
        .describe("Blocks closer than this to the camera are never culled (anti-flicker)");
    private final NumberValue shadowDiff = new NumberValue("Shadow Threshold", this, 4.0, 0.0, 64.0, 1.0)
        .describe("Shadow-pass culling only kicks in beyond this distance (anti-popping)");
    private final NumberValue distanceLimitAdder = new NumberValue("Distance Limit", this, 16.0, 0.0, 256.0, 16.0)
        .describe("Extra blocks beyond render distance that still get culled");

    private EntityCullingThread thread;

    public EntityCulling() {
        super("Entity Culling", "Async occlusion culling for entities and tile entities (EntityCulling port)", 0, Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (this.thread == null || !this.thread.isAlive()) {
            this.thread = new EntityCullingThread(config);
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
        config.disabledInSpectator = this.disabledInSpectator.getValue();
        config.alwaysRenderPlayers = this.alwaysRenderPlayers.getValue();
        config.alwaysRenderBosses = this.alwaysRenderBosses.getValue();
        config.alwaysRenderNamed = this.alwaysRenderNamed.getValue();
        config.cullTileEntities = this.cullTileEntities.getValue();
        config.cullShadows = this.cullShadows.getValue();
        config.enableRaytraceCache = this.enableRaytraceCache.getValue();
        config.sizeLimit = this.sizeLimit.getValue();
        config.raytraceThreshold = this.raytraceThreshold.getValue();
        config.shadowDiff = this.shadowDiff.getValue();
        config.distanceLimitAdder = this.distanceLimitAdder.getValue();
    }

}
