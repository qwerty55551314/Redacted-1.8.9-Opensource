package celeritas;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.jetbrains.annotations.Nullable;

/**
 * Port of Natron's {@code NatronRenderSectionManager} - the version-specific half that plugs into
 * Celeritas: it owns the occlusion graph, region arenas and the async build queue.
 */
public class CeleritasRenderSectionManager extends RenderSectionManager {

    private final WorldClient world;

    private float cameraX;
    private float cameraY;
    private float cameraZ;

    public CeleritasRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world,
                                         int renderDistance, CommandList commandList,
                                         int minSection, int maxSection, int requestedThreads) {
        super(configuration,
            new ContextSupplier(configuration),
            CeleritasChunkRenderer::new,
            renderDistance, commandList, minSection, maxSection, requestedThreads,
            false);
        this.world = world;
    }

    private static final class ContextSupplier
            implements Supplier<org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext> {

        private final RenderPassConfiguration<?> configuration;

        ContextSupplier(RenderPassConfiguration<?> configuration) {
            this.configuration = configuration;
        }

        @Override
        public org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext get() {
            return new CeleritasChunkBuildContext(this.configuration);
        }
    }

    private boolean initialCameraSectionReady;

    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet knownSections =
        new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    @Override
    public void onSectionAdded(int x, int y, int z) {
        super.onSectionAdded(x, y, z);

        if (!this.initialCameraSectionReady) {
            this.knownSections.add(packSection(x, y, z));
        }

        if (!this.world.getChunkFromChunkCoords(x, z).isEmpty()) {
            return;
        }

        this.renderListManager.updateVisibilityData(x, y, z, 0L);
    }

    @Override
    public void update(Viewport viewport, int frame, boolean spectator) {
        if (!this.initialCameraSectionReady) {
            final org.joml.Vector3ic camera = viewport.getChunkCoord();

            if (!this.knownSections.contains(packSection(camera.x(), camera.y(), camera.z()))) {
                return;
            }

            this.initialCameraSectionReady = true;

            this.knownSections.clear();
            this.knownSections.trim();
        }

        super.update(viewport, frame, spectator);
    }

    private static long packSection(int x, int y, int z) {
        return org.embeddedt.embeddium.impl.util.PositionUtil.packSection(x, y, z);
    }

    /** Records where the camera is, so rebuild tasks can sort translucent geometry against it. */
    public void setCameraPosition(double x, double y, double z) {
        this.cameraX = (float) x;
        this.cameraY = (float) y;
        this.cameraZ = (float) z;
    }

    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        switch (Minecraft.getMinecraft().gameSettings.ofCeleritasAsyncOcclusion) {
            case 0: return AsyncOcclusionMode.NONE;
            case 1: return AsyncOcclusionMode.ONLY_SHADOW;
            default: return AsyncOcclusionMode.EVERYTHING;
        }
    }

    @Override
    protected boolean useFogOcclusion() {
        // Defaults to false: Natron/Angelica enable fog-based search-distance culling, but that
        // makes the visible radius depend on the live fog color/cutoff. When fog state isn't exactly
        // as expected (disabled, or a custom fog color with non-opaque alpha), getEffectiveRenderDistance()
        // collapses to a tiny value and the occlusion walk culls every section except the camera's own -
        // exactly the "only the chunk I'm standing in renders" bug. With fog occlusion off we always
        // search out to the full render distance, which is correct and can't collapse. Exposed as a
        // setting for parity; flipping it on reintroduces the fog-dependent search radius.
        return Minecraft.getMinecraft().gameSettings.ofCeleritasFogOcclusion;
    }

    @Override
    protected boolean allowImportantRebuilds() {
        // Critical: without this, block edits (breaking/placing near the player) are scheduled as plain
        // throttled REBUILD tasks that only dispatch when the async build budget has room - so a broken
        // block can sit in the mesh for many frames (or until a reload) before it disappears. Returning
        // true makes edits within ~16 blocks of the camera dispatch as IMPORTANT_REBUILD, which runs on
        // the render thread immediately, so the section mesh updates the same frame the block changes.
        return true;
    }

    @Override
    protected boolean shouldUseOcclusionCulling(Viewport viewport, boolean spectator) {
        if (spectator) {
            final BlockPos pos = new BlockPos(
                viewport.getBlockCoord().x(), viewport.getBlockCoord().y(), viewport.getBlockCoord().z());
            if (this.world.getBlockState(pos).getBlock().isOpaqueCube()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isSectionVisuallyEmpty(int x, int y, int z) {
        final Chunk chunk = this.world.getChunkFromChunkCoords(x, z);

        if (chunk == null || chunk.isEmpty()) {
            return true;
        }

        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

        if (y < 0 || y >= sections.length) {
            return true;
        }

        final ExtendedBlockStorage section = sections[y];
        return section == null || section.isEmpty();
    }

    @Override
    protected @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        final int x = render.getChunkX();
        final int y = render.getChunkY();
        final int z = render.getChunkZ();

        final boolean empty = isSectionVisuallyEmpty(x, y, z);
        if (redacted.client.util.debug.DebugSystem.isEnabled(redacted.client.util.debug.DebugSystem.CELERITAS)) {
            redacted.client.util.debug.DebugSystem.log(redacted.client.util.debug.DebugSystem.CELERITAS,
                "createRebuildTask section=(" + x + "," + y + "," + z + ") empty=" + empty + " frame=" + frame);
        }

        if (empty) {
            return null;
        }

        final BlockPos origin = new BlockPos(x << 4, y << 4, z << 4);

        return new CeleritasMeshingTask(render, this.world, origin, render.getSectionIndex(), frame,
            this.cameraX, this.cameraY, this.cameraZ);
    }

    public static final int AUTO_THREAD_COUNT = 0;

    public static WorldClient currentWorld() {
        return Minecraft.getMinecraft().theWorld;
    }
}
