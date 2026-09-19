package celeritas;

import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;

/**
 * Port of Natron's {@code NatronChunkRenderer}: texture unit binding for terrain (unit 0 = block
 * atlas, unit 1 = lightmap).
 */
public class CeleritasChunkRenderer extends DefaultChunkRenderer {

    public CeleritasChunkRenderer(RenderDevice device, RenderPassConfiguration<?> configuration) {
        super(device, configuration);
    }

    @Override
    protected void configureShaderInterface(ChunkShaderInterface shader) {
        shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
        shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
    }
}
