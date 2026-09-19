package celeritas;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;

/**
 * Moves geometry out of a vanilla {@code WorldRenderer} buffer and into Celeritas's chunk mesh
 * builders. Port of Natron's {@code VanillaBufferTranscoder}.
 */
public final class VanillaBufferTranscoder {

    private static final int VERTICES_PER_QUAD = 4;

    private static final class Layout {
        final VertexFormat format;
        final int stride;
        final int offsetPosition;
        final int offsetColor;
        final int offsetTexture;
        final int offsetLight;

        Layout(VertexFormat format) {
            this.format = format;
            this.stride = format.getNextOffset();
            this.offsetPosition = offsetOf(format, DefaultVertexFormats.POSITION_3F);
            this.offsetColor = offsetOf(format, DefaultVertexFormats.COLOR_4UB);
            this.offsetTexture = offsetOf(format, DefaultVertexFormats.TEX_2F);
            this.offsetLight = offsetOf(format, DefaultVertexFormats.TEX_2S);
        }

        private static int offsetOf(VertexFormat format, VertexFormatElement element) {
            for (int i = 0; i < format.getElementCount(); i++) {
                if (format.getElement(i).equals(element)) {
                    return format.getOffset(i);
                }
            }

            throw new IllegalStateException("DefaultVertexFormats.BLOCK has no " + element
                + "; another mod has changed the block vertex format in a way this renderer "
                + "cannot read");
        }
    }

    private static volatile Layout cachedLayout;

    private static Layout currentLayout() {
        final VertexFormat current = DefaultVertexFormats.BLOCK;
        Layout layout = cachedLayout;

        if (layout == null || layout.format != current) {
            layout = new Layout(current);
            cachedLayout = layout;
        }

        return layout;
    }

    private Layout layout;

    /** Call once per section build, before any {@link #transcode} call for that section. */
    public void refreshLayout() {
        this.layout = currentLayout();
    }

    private final ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();

    public int transcode(ByteBuffer buffer, int vertexCount, ChunkModelBuilder builder, Material material) {
        final int quadCount = vertexCount / VERTICES_PER_QUAD;
        final int base = buffer.position();
        final int stride = this.layout.stride;

        for (int q = 0; q < quadCount; q++) {
            final int quadStart = base + q * VERTICES_PER_QUAD * stride;

            for (int v = 0; v < VERTICES_PER_QUAD; v++) {
                readVertex(buffer, quadStart + v * stride, this.quad[v]);
            }

            computeNormal(this.quad);

            final int normal = packedNormal();
            for (int v = 0; v < VERTICES_PER_QUAD; v++) {
                this.quad[v].vanillaNormal = normal;
                this.quad[v].trueNormal = normal;
            }

            builder.getVertexBuffer(facingOf()).push(this.quad, material);
        }

        return quadCount;
    }

    private void readVertex(ByteBuffer buffer, int offset, ChunkVertexEncoder.Vertex vertex) {
        final Layout layout = this.layout;

        vertex.x = buffer.getFloat(offset + layout.offsetPosition);
        vertex.y = buffer.getFloat(offset + layout.offsetPosition + 4);
        vertex.z = buffer.getFloat(offset + layout.offsetPosition + 8);
        vertex.color = buffer.getInt(offset + layout.offsetColor);
        vertex.u = buffer.getFloat(offset + layout.offsetTexture);
        vertex.v = buffer.getFloat(offset + layout.offsetTexture + 4);
        vertex.light = buffer.getInt(offset + layout.offsetLight);
    }

    private float normalX;
    private float normalY;
    private float normalZ;

    private void computeNormal(ChunkVertexEncoder.Vertex[] quad) {
        final float ax = quad[1].x - quad[0].x;
        final float ay = quad[1].y - quad[0].y;
        final float az = quad[1].z - quad[0].z;

        final float bx = quad[3].x - quad[0].x;
        final float by = quad[3].y - quad[0].y;
        final float bz = quad[3].z - quad[0].z;

        this.normalX = ay * bz - az * by;
        this.normalY = az * bx - ax * bz;
        this.normalZ = ax * by - ay * bx;
    }

    private ModelQuadFacing facingOf() {
        final float nx = this.normalX;
        final float ny = this.normalY;
        final float nz = this.normalZ;

        final float ax = Math.abs(nx);
        final float ay = Math.abs(ny);
        final float az = Math.abs(nz);

        if (ax > ay && ax > az) {
            return axisAligned(nx, ay + az, ax) ? (nx > 0 ? ModelQuadFacing.POS_X : ModelQuadFacing.NEG_X)
                : ModelQuadFacing.UNASSIGNED;
        }
        if (ay > az) {
            return axisAligned(ny, ax + az, ay) ? (ny > 0 ? ModelQuadFacing.POS_Y : ModelQuadFacing.NEG_Y)
                : ModelQuadFacing.UNASSIGNED;
        }
        if (az > 0.0F) {
            return axisAligned(nz, ax + ay, az) ? (nz > 0 ? ModelQuadFacing.POS_Z : ModelQuadFacing.NEG_Z)
                : ModelQuadFacing.UNASSIGNED;
        }
        return ModelQuadFacing.UNASSIGNED;
    }

    private static boolean axisAligned(float dominant, float others, float dominantAbs) {
        return dominant != 0.0F && others <= dominantAbs * 1.0E-4F;
    }

    private int packedNormal() {
        final float nx = this.normalX;
        final float ny = this.normalY;
        final float nz = this.normalZ;

        final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0.0F) {
            return 0;
        }

        final int px = (int) (nx / length * 127.0F) & 0xFF;
        final int py = (int) (ny / length * 127.0F) & 0xFF;
        final int pz = (int) (nz / length * 127.0F) & 0xFF;

        return px | py << 8 | pz << 16;
    }
}
