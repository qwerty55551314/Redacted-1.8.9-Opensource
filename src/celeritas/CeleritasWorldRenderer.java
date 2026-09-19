package celeritas;

import java.nio.FloatBuffer;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumWorldBlockLayer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Port of Natron's {@code NatronWorldRenderer} - the object that owns the renderer for the
 * current world and drives it once per frame.
 */
public class CeleritasWorldRenderer
        extends SimpleWorldRenderer<WorldClient, CeleritasRenderSectionManager, EnumWorldBlockLayer, TileEntity, Object> {

    private static CeleritasWorldRenderer instance;

    private static final int MIN_BUILD_HEIGHT = 0;
    private static final int MAX_BUILD_HEIGHT = 256;

    private final FloatBuffer projectionBuffer = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer modelViewBuffer = GLAllocation.createDirectFloatBuffer(16);

    public static CeleritasWorldRenderer getInstance() {
        if (instance == null) {
            instance = new CeleritasWorldRenderer();
        }
        return instance;
    }

    public static CeleritasWorldRenderer getInstanceOrNull() {
        return instance;
    }

    @Override
    public int getEffectiveRenderDistance() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    @Override
    public int getMinimumBuildHeight() {
        return MIN_BUILD_HEIGHT;
    }

    @Override
    public int getMaximumBuildHeight() {
        return MAX_BUILD_HEIGHT;
    }

    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        this.projectionBuffer.rewind();
        this.modelViewBuffer.rewind();

        return new ChunkRenderMatrices(this.projectionBuffer, this.modelViewBuffer);
    }

    /** The one GL matrix read for this frame. Called from the setupTerrain hook. */
    public void refreshCameraMatrices() {
        this.projectionBuffer.clear();
        this.modelViewBuffer.clear();

        GlStateManager.getFloat(GL11.GL_PROJECTION_MATRIX, this.projectionBuffer);
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, this.modelViewBuffer);

        this.projectionBuffer.rewind();
        this.modelViewBuffer.rewind();
    }

    public FloatBuffer getProjectionBuffer() {
        return this.projectionBuffer;
    }

    public FloatBuffer getModelViewBuffer() {
        return this.modelViewBuffer;
    }

    @Override
    protected CeleritasRenderSectionManager createRenderSectionManager(CommandList commandList) {
        final int renderDistance = getEffectiveRenderDistance();

        return new CeleritasRenderSectionManager(
            CeleritasRenderPassConfiguration.build(
                org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats.COMPACT),
            this.world,
            renderDistance,
            commandList,
            MIN_BUILD_HEIGHT >> 4,
            MAX_BUILD_HEIGHT >> 4,
            Minecraft.getMinecraft().gameSettings.ofCeleritasChunkThreads);
    }

    @Override
    protected void renderBlockEntityList(List<TileEntity> list, Object context) {
        // intentionally empty until block entity rendering is moved over
    }

    public void invalidateAndScheduleRebuild(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Block edits are always near the player. Scheduling them as "important" routes them into
        // IMPORTANT_REBUILD, which dispatches and blocks on the render thread immediately instead of
        // sitting in the budget-limited deferred queue behind the world-load backlog. Without this a
        // broken block can stay in the mesh until a full reload.
        scheduleRebuildForBlockArea(x1, y1, z1, x2, y2, z2, true);
    }
}
