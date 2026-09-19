package celeritas;

import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.EnumWorldBlockLayer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;

/**
 * Port of Natron's {@code NatronChunkBuildContext}: per-worker scratch state for Celeritas.
 */
public class CeleritasChunkBuildContext extends ChunkBuildContext {

    private final RegionRenderCacheBuilder vanillaBuffers;

    private final VanillaBufferTranscoder transcoder = new VanillaBufferTranscoder();

    private final boolean[] layerStarted = new boolean[EnumWorldBlockLayer.values().length];
    private final boolean[] layerHasGeometry = new boolean[EnumWorldBlockLayer.values().length];

    public CeleritasChunkBuildContext(RenderPassConfiguration<?> configuration) {
        super(configuration);
        this.vanillaBuffers = new RegionRenderCacheBuilder();
    }

    public WorldRenderer getBuffer(EnumWorldBlockLayer layer) {
        return this.vanillaBuffers.getWorldRendererByLayer(layer);
    }

    /** The worker's vanilla region builder, shared across the section's layers. */
    public RegionRenderCacheBuilder getRegionRenderCacheBuilder() {
        return this.vanillaBuffers;
    }

    /** @return this worker's transcoder, with its vertex layout brought up to date */
    public VanillaBufferTranscoder beginSection() {
        this.transcoder.refreshLayout();
        return this.transcoder;
    }

    /** @return this worker's layer flags, cleared for a new section */
    public boolean[] resetLayerStarted() {
        java.util.Arrays.fill(this.layerStarted, false);
        return this.layerStarted;
    }

    /** @return this worker's layer flags, cleared for a new section */
    public boolean[] resetLayerHasGeometry() {
        java.util.Arrays.fill(this.layerHasGeometry, false);
        return this.layerHasGeometry;
    }
}
