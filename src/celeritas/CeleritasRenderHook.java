package celeritas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumWorldBlockLayer;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;

/**
 * Bridge entry point: called directly from the vanilla source hooks in RenderGlobal.
 * This is the non-mixin counterpart of Natron's {@code MixinRenderGlobalCeleritas}. The chunk
 * tracker is provided by {@code WorldClient} implementing {@code ChunkTrackerHolder}.
 */
public final class CeleritasRenderHook {

    private CeleritasRenderHook() {
    }

    /**
     * Monotonic frame counter passed to Celeritas's occlusion walk. The walk uses
     * {@code section.getLastVisibleFrame() != frame} to decide whether a neighbor has already been
     * visited this frame; passing a constant frame (0) makes that comparison always false after the
     * first visit, so neighbors are never enqueued and only the camera's own section renders. It
     * MUST increase every frame.
     */
    private static int frameCounter;

    /**
     * Whether Celeritas terrain rendering is active right now. It is only active when the renderer
     * is supported AND OptiFine shaders are off. When shaders are on, OptiFine's own shader
     * programs take over the whole terrain pass (they expect the fixed-function/SVertexFormat block
     * vertex layout, not Celeritas's compact format), so we let vanilla render terrain and OptiFine
     * shade it exactly as before.
     */
    public static boolean isActive() {
        if (!CeleritasAvailability.isSupported()) {
            return false;
        }
        if (net.minecraft.src.Config.isShaders()) {
            return false;
        }
        // Video Settings -> Other -> "Celeritas Renderer" toggle lets you force the vanilla/OptiFine
        // terrain renderer for easy A/B comparison.
        if (!Minecraft.getMinecraft().gameSettings.ofCeleritasRenderer) {
            return false;
        }
        return CeleritasWorldRenderer.getInstanceOrNull() != null;
    }

    /** Tracks the previous active state so we only log on enable/disable transitions. */
    private static boolean lastActive;

    /** Called once per frame after hooks run; logs one line on renderer on/off transitions. */
    public static void onFrameEnd() {
        final boolean active = isActive();
        if (active != lastActive) {
            final boolean wasActive = lastActive;
            lastActive = active;

            if (redacted.client.util.debug.DebugSystem.isEnabled(redacted.client.util.debug.DebugSystem.CELERITAS)) {
                redacted.client.util.debug.DebugSystem.log(redacted.client.util.debug.DebugSystem.CELERITAS,
                    "Celeritas renderer " + (active ? "ACTIVE" : "inactive (using vanilla/OptiFine terrain)"));
            }

            // When Celeritas is (re)enabled, rebuild everything so its meshes catch up with any
            // vanilla-world changes that happened while it was off (block edits etc.). This also
            // clears any stale section mesh that lingered from before the toggle.
            if (active && !wasActive) {
                final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
                if (renderer != null) {
                    try (CeleritasGlStateGuard guard = CeleritasGlStateGuard.open()) {
                        renderer.reload();
                    }
                }
            }
        }
    }

    /** Called from {@code RenderGlobal.setWorldAndLoadRenderers}. */
    public static void onSetWorld(WorldClient world) {
        // The one gate: if this GPU cannot run Celeritas, never construct the renderer.
        if (!CeleritasAvailability.isSupported()) {
            return;
        }

        CeleritasSetup.init();

        try (CeleritasGlStateGuard guard = CeleritasGlStateGuard.open()) {
            CeleritasWorldRenderer.getInstance().setWorld(world);
        }

        if (redacted.client.util.debug.DebugSystem.isEnabled(redacted.client.util.debug.DebugSystem.CELERITAS)) {
            redacted.client.util.debug.DebugSystem.log(redacted.client.util.debug.DebugSystem.CELERITAS,
                "Renderer created for world (shaders=" + net.minecraft.src.Config.isShaders()
                    + ", toggle=" + Minecraft.getMinecraft().gameSettings.ofCeleritasRenderer + ")");
        }
    }

    /** Called from {@code RenderGlobal.loadRenderers}. */
    public static void onReload() {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null) {
            return;
        }

        try (CeleritasGlStateGuard guard = CeleritasGlStateGuard.open()) {
            renderer.reload();
        }
    }

    /**
     * Called from {@code RenderGlobal.setupTerrain}. Full takeover: drives Celeritas's own mesh
     * building, occlusion and upload, and returns {@code true} so vanilla's own setupTerrain is
     * cancelled entirely (no double terrain work, no GL state conflicts). The entity and tile-entity
     * render lists are populated separately by {@link #populateEntityLists} so vanilla's
     * {@code renderEntities} still renders them unchanged.
     */
    public static boolean onSetupTerrain(Entity viewEntity, double partialTicks) {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        onFrameEnd();
        if (renderer == null || !isActive()) {
            return false;
        }

        renderer.refreshCameraMatrices();
        CeleritasRenderPassConfiguration.refreshAlphaBaseline();
        CeleritasFogService.refresh();

        final double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        final double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        final double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;

        final org.embeddedt.embeddium.impl.render.viewport.Viewport viewport = new ViewportBuilder().create(
            renderer.getProjectionBuffer(), renderer.getModelViewBuffer(), x, y, z);
        final SimpleWorldRenderer.CameraState cameraState = new SimpleWorldRenderer.CameraState(
            x, y, z, viewEntity.rotationPitch, viewEntity.rotationYaw,
            Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0F);

        // Translucent geometry (water, glass, ice) is sorted against this camera position when a section is
        // (re)meshed. It defaults to (0,0,0), which sorts every translucent section against the world origin
        // instead of the player -> water placed far from spawn renders in the wrong order or looks missing.
        renderer.getRenderSectionManager().setCameraPosition(x, y, z);

        final int frame = frameCounter++;
        try (CeleritasGlStateGuard guard = CeleritasGlStateGuard.open()) {
            renderer.setupTerrain(viewport, cameraState, frame, false, false);
        }

        // Full takeover: cancel vanilla's setupTerrain so it never runs its cull/BFS or touches GL.
        return true;
    }

    /**
     * Populates the vanilla entity / tile-entity render lists from Celeritas's visible sections, so
     * {@code RenderGlobal.renderEntities} still renders entities and tile entities unchanged even
     * though vanilla's {@code setupTerrain} (which normally builds these lists) is cancelled.
     *
     * <p>Mirrors what vanilla's setupTerrain does for each visible render chunk: if the chunk has
     * entities it goes into {@code renderInfosEntities}, and if its compiled chunk has tile entities
     * it goes into {@code renderInfosTileEntities}.
     */
    public static void populateEntityLists() {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || !isActive()) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        final net.minecraft.client.renderer.RenderGlobal rg = mc.renderGlobal;
        if (rg == null) {
            return;
        }
        rg.celeritasPopulateEntityLists(renderer);
    }

    /**
     * Called from {@code RenderGlobal.renderBlockLayer}; returns true if the layer was drawn by
     * Celeritas.
     */
    public static boolean onRenderLayer(EnumWorldBlockLayer layer, double partialTicks, Entity entity) {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || !isActive()) {
            return false;
        }

        final double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        try (CeleritasGlStateGuard guard = CeleritasGlStateGuard.open()) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            renderer.drawChunkLayer(layer, x, y, z);
        }

        return true;
    }

    /** Called from {@code RenderGlobal.updateChunks}; true if Celeritas pumps its own queue. */
    public static boolean onUpdateChunks() {
        return isActive();
    }

    /** Called from {@code RenderGlobal.stopChunkUpdates}; true if handled by Celeritas. */
    public static boolean onStopChunkUpdates() {
        return isActive();
    }

    /** Called from {@code RenderGlobal.markBlocksForUpdate}; true if handled by Celeritas. */
    public static boolean onMarkBlocksForUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!isActive()) {
            return false;
        }

        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null) {
            return false;
        }

        if (redacted.client.util.debug.DebugSystem.isEnabled(redacted.client.util.debug.DebugSystem.CELERITAS)) {
            redacted.client.util.debug.DebugSystem.log(redacted.client.util.debug.DebugSystem.CELERITAS,
                "markBlockForUpdate area=(" + x1 + "," + y1 + "," + z1 + ")-(" + x2 + "," + y2 + "," + z2 + ")");
        }

        renderer.invalidateAndScheduleRebuild(x1, y1, z1, x2, y2, z2);
        return true;
    }

    /** Called from {@code RenderGlobal.getDebugInfoRenders}. */
    public static String debugInfo() {
        if (!isActive()) {
            return null;
        }
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        return renderer != null ? renderer.getChunksDebugString() : null;
    }

    /**
     * The active terrain renderer name for the F3 overlay (left side). Returns {@code null} when the
     * Celeritas renderer is not active so the F3 HUD is left unchanged in that case (vanilla/OptiFine
     * terrain).
     */
    public static String f3RendererLabel() {
        if (!isActive()) {
            return null;
        }
        return "Celeritas";
    }
}