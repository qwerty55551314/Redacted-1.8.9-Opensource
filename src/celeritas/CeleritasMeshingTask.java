package celeritas;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.VisibilityEncoding;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.lwjgl.opengl.GL11;

/**
 * 1.8.9 counterpart of Natron's {@code NatronMeshingTask}. This client is vanilla (not Forge), so
 * the block layer is chosen with {@code block.getBlockLayer()} rather than Forge's
 * {@code canRenderInLayer}/{@code ForgeHooksClient.setRenderLayer}, and the layer is set on the
 * vanilla {@link WorldRenderer} via {@code setBlockLayer} (which {@link BlockModelRenderer} reads
 * back). Vanilla emits the geometry into a per-layer {@link WorldRenderer}, then
 * {@link VanillaBufferTranscoder} moves it into Celeritas's mesh builders.
 */
public class CeleritasMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {

    private static final EnumWorldBlockLayer[] LAYERS = EnumWorldBlockLayer.values();
    private static final EnumFacing[] FACINGS = EnumFacing.values();
    private static final int SECTION_SIZE = 16;
    private static final int SNAPSHOT_PADDING = 1;

    private final RenderSection render;
    private final WorldClient world;
    private final BlockPos origin;
    private final int sectionIndex;
    private final int frame;
    private final float camX;
    private final float camY;
    private final float camZ;

    public CeleritasMeshingTask(RenderSection render, WorldClient world, BlockPos origin,
                                int sectionIndex, int frame, float camX, float camY, float camZ) {
        this.render = render;
        this.world = world;
        this.origin = origin;
        this.sectionIndex = sectionIndex;
        this.frame = frame;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        if (!(context instanceof CeleritasChunkBuildContext)) {
            throw new IllegalStateException(
                "expected CeleritasChunkBuildContext, got " + context.getClass().getName());
        }

        final long startTime = System.nanoTime();
        final CeleritasChunkBuildContext ctx = (CeleritasChunkBuildContext) context;
        final BuiltRenderSectionData renderData = new BuiltRenderSectionData();
        final VisGraph visGraph = new VisGraph();
        final VanillaBufferTranscoder transcoder = ctx.beginSection();

        final BlockPos from = this.origin.add(-SNAPSHOT_PADDING, -SNAPSHOT_PADDING, -SNAPSHOT_PADDING);
        final BlockPos to = this.origin.add(SECTION_SIZE, SECTION_SIZE, SECTION_SIZE);
        final RegionRenderCache snapshot =
            new RegionRenderCache(this.world, from, to, SNAPSHOT_PADDING);

        try {
        return buildSection(ctx, renderData, visGraph, transcoder, snapshot,
            cancellationToken, startTime, context);
        } finally {
            // Return the snapshot's pooled arrays (int[8000] + IBlockState[8000]) to RegionRenderCache's
            // static cache. Without this, every section build allocates fresh arrays that never get
            // pooled, causing GC churn and pool starvation during world load / chunk streaming.
            snapshot.freeBuffers();
        }
    }

    private ChunkBuildOutput buildSection(final CeleritasChunkBuildContext ctx,
        final BuiltRenderSectionData renderData, final VisGraph visGraph,
        final VanillaBufferTranscoder transcoder, final RegionRenderCache snapshot,
        final CancellationToken cancellationToken, final long startTime,
        final ChunkBuildContext context) {

        context.buffers.init(renderData, this.sectionIndex);

        final BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        final int originX = this.origin.getX();
        final int originY = this.origin.getY();
        final int originZ = this.origin.getZ();

        final boolean[] layerStarted = ctx.resetLayerStarted();
        final boolean[] layerHasGeometry = ctx.resetLayerHasGeometry();

        for (int y = 0; y < SECTION_SIZE; y++) {
            if (cancellationToken.isCancelled()) {
                abortStartedBuffers(ctx, layerStarted);
                return null;
            }

            for (int z = 0; z < SECTION_SIZE; z++) {
                for (int x = 0; x < SECTION_SIZE; x++) {
                    pos.set(originX + x, originY + y, originZ + z);

                    final IBlockState state = snapshot.getBlockState(pos);
                    final Block block = state.getBlock();

                    if (block == Blocks.air) {
                        continue;
                    }

                    if (block.isOpaqueCube()) {
                        visGraph.func_178606_a(pos);
                    }
                    if (block.getRenderType() == -1) {
                        continue;
                    }

                    // Vanilla: each block renders into a single layer chosen by getBlockLayer().
                    final EnumWorldBlockLayer layer = block.getBlockLayer();
                    final int li = layer.ordinal();

                    if (!layerStarted[li]) {
                        layerStarted[li] = true;
                        final WorldRenderer buffer = ctx.getBuffer(layer);
                        buffer.setBlockLayer(layer);
                        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                        buffer.setTranslation(-originX, -originY, -originZ);
                    }

                    // OptiFine's BlockModelRenderer reads back a per-vertex RenderEnv on the
                    // WorldRenderer, and its overlay/emissive path needs the region builder set or
                    // it renders nothing / NPEs. Mirror what vanilla's RenderChunk.rebuildChunk does
                    // before every renderBlock call.
                    final WorldRenderer buffer = ctx.getBuffer(layer);
                    buffer.getRenderEnv(state, pos).setRegionRenderCacheBuilder(ctx.getRegionRenderCacheBuilder());

                    layerHasGeometry[li] |= dispatcher.renderBlock(state, pos, snapshot, buffer);
                }
            }
        }

        boolean anyGeometry = false;

        for (int i = 0; i < LAYERS.length; i++) {
            if (!layerStarted[i]) {
                continue;
            }

            final WorldRenderer buffer = ctx.getBuffer(LAYERS[i]);
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            buffer.finishDrawing();

            if (layerHasGeometry[i] && buffer.getVertexCount() > 0) {
                anyGeometry = true;
                transcodeLayer(ctx, transcoder, buffer, LAYERS[i]);
            }

            buffer.setBlockLayer(null);
            buffer.reset();
        }

        renderData.hasBlockGeometry = anyGeometry;
        renderData.visibilityData = encodeVisibility(visGraph);

        final Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
            new Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts>();

        for (TerrainRenderPass pass : context.buffers.getBuilderPasses()) {
            final BuiltSectionMeshParts mesh =
                context.buffers.createMesh(pass, this.camX, this.camY, this.camZ);
            if (mesh != null) {
                meshes.put(pass, mesh);
            }
        }

        final int buildDurationUs = (int) ((System.nanoTime() - startTime) / 1000L);
        if (redacted.client.util.debug.DebugSystem.isEnabled(redacted.client.util.debug.DebugSystem.CELERITAS)) {
            redacted.client.util.debug.DebugSystem.log(redacted.client.util.debug.DebugSystem.CELERITAS,
                "meshed section origin=(" + originX + "," + originY + "," + originZ + ")"
                    + " anyGeometry=" + anyGeometry + " passes=" + meshes.size()
                    + " buildTimeUs=" + buildDurationUs);
        }
        // buildTime MUST be the monotonic frame, not the wall-clock build duration. RenderSectionManager
        // compares it against getLastBuiltFrame()/getLastSubmittedFrame() (both frames) to decide whether a
        // rebuild result is newer than the previous one and to clear the in-flight build cancellation token.
        // Passing the microsecond duration here made every rebuild that finished faster than the previous one
        // look stale (block breaks discarded) and, once the frame counter grew past a few thousand, made
        // "buildTime >= lastSubmittedFrame" always false so the token never cleared and sections could never
        // be rebuilt again (placed blocks invisible).
        return new ChunkBuildOutput(this.render, renderData, meshes, this.frame);
    }

    private static void abortStartedBuffers(CeleritasChunkBuildContext ctx, boolean[] layerStarted) {
        for (int i = 0; i < LAYERS.length; i++) {
            if (!layerStarted[i]) {
                continue;
            }

            final WorldRenderer buffer = ctx.getBuffer(LAYERS[i]);
            buffer.setTranslation(0.0D, 0.0D, 0.0D);
            buffer.finishDrawing();
            buffer.setBlockLayer(null);
            buffer.reset();
        }
    }

    private void transcodeLayer(CeleritasChunkBuildContext ctx, VanillaBufferTranscoder transcoder,
                                WorldRenderer buffer, EnumWorldBlockLayer layer) {
        final Material material = materialFor(layer);
        final java.nio.ByteBuffer bytes = buffer.getByteBuffer();
        bytes.position(0);
        transcoder.transcode(bytes, buffer.getVertexCount(), ctx.buffers.get(material), material);
    }

    private static Material materialFor(EnumWorldBlockLayer layer) {
        switch (layer) {
            case SOLID:
                return CeleritasRenderPassConfiguration.SOLID_MATERIAL;
            case CUTOUT:
                return CeleritasRenderPassConfiguration.CUTOUT_MATERIAL;
            case CUTOUT_MIPPED:
                return CeleritasRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL;
            case TRANSLUCENT:
            default:
                return CeleritasRenderPassConfiguration.TRANSLUCENT_MATERIAL;
        }
    }

    private static long encodeVisibility(VisGraph visGraph) {
        final SetVisibility visibility = visGraph.computeVisibility();

        return VisibilityEncoding.encode(new VisibilityEncoding.DataHolder() {
            @Override
            public boolean canFaceSeeFace(int fromDir, int toDir) {
                return visibility.isVisible(FACINGS[fromDir], FACINGS[toDir]);
            }
        });
    }
}
