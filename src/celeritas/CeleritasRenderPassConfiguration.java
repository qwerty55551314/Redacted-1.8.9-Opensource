package celeritas;

import java.util.EnumMap;
import java.util.Map;

import com.google.common.collect.ImmutableListMultimap;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumWorldBlockLayer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.opengl.GL11;

/**
 * Port of Natron's {@code NatronRenderPassConfiguration} onto 1.8.9's block layers.
 */
public final class CeleritasRenderPassConfiguration {

    public static TerrainRenderPass SOLID_PASS;
    public static TerrainRenderPass CUTOUT_MIPPED_PASS;
    public static TerrainRenderPass TRANSLUCENT_PASS;

    public static Material SOLID_MATERIAL;
    public static Material CUTOUT_MATERIAL;
    public static Material CUTOUT_MIPPED_MATERIAL;
    public static Material TRANSLUCENT_MATERIAL;

    private static int cachedAlphaFunc = -1;
    private static float cachedAlphaRef;

    private CeleritasRenderPassConfiguration() {
    }

    /** Reads the baseline alpha-test state once. Call at most once per frame, before any pass draws. */
    public static void refreshAlphaBaseline() {
        cachedAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        cachedAlphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    }

    private static TerrainRenderPass.TerrainRenderPassBuilder builderFor(
            int pass, boolean disableAlphaTest, ChunkVertexType vertexType) {
        return TerrainRenderPass.builder()
            .pipelineState(new PipelineState(pass, disableAlphaTest))
            .vertexType(vertexType)
            .primitiveType(QuadPrimitiveType.TRIANGULATED);
    }

    public static RenderPassConfiguration<EnumWorldBlockLayer> build(ChunkVertexType vertexType) {
        // Honor the user's translucent-sorting toggle. When off, the translucent pass skips per-quad
        // sorting (draws in section order), trading visual correctness for a small speed gain.
        final boolean sortTranslucent =
            net.minecraft.client.Minecraft.getMinecraft().gameSettings.ofCeleritasTranslucentSorting;

        SOLID_PASS = builderFor(0, true, vertexType)
            .name("solid")
            .fragmentDiscard(false)
            .useReverseOrder(false)
            .build();

        CUTOUT_MIPPED_PASS = builderFor(0, false, vertexType)
            .name("cutout_mipped")
            .fragmentDiscard(true)
            .useReverseOrder(false)
            .build();

        TRANSLUCENT_PASS = builderFor(1, false, vertexType)
            .name("translucent")
            .fragmentDiscard(false)
            .useReverseOrder(sortTranslucent)
            .useTranslucencySorting(sortTranslucent)
            .build();

        SOLID_MATERIAL = new Material(SOLID_PASS, AlphaCutoffParameter.ZERO, true);
        CUTOUT_MIPPED_MATERIAL = new Material(CUTOUT_MIPPED_PASS, AlphaCutoffParameter.HALF, true);
        CUTOUT_MATERIAL = new Material(CUTOUT_MIPPED_PASS, AlphaCutoffParameter.ONE_TENTH, false);
        TRANSLUCENT_MATERIAL = new Material(TRANSLUCENT_PASS, AlphaCutoffParameter.ZERO, true);

        final ImmutableListMultimap.Builder<EnumWorldBlockLayer, TerrainRenderPass> stages =
            ImmutableListMultimap.builder();
        stages.put(EnumWorldBlockLayer.SOLID, SOLID_PASS);
        stages.put(EnumWorldBlockLayer.CUTOUT, CUTOUT_MIPPED_PASS);
        stages.put(EnumWorldBlockLayer.CUTOUT_MIPPED, CUTOUT_MIPPED_PASS);
        stages.put(EnumWorldBlockLayer.TRANSLUCENT, TRANSLUCENT_PASS);

        final Map<EnumWorldBlockLayer, Material> materials =
            new EnumMap<EnumWorldBlockLayer, Material>(EnumWorldBlockLayer.class);
        materials.put(EnumWorldBlockLayer.SOLID, SOLID_MATERIAL);
        materials.put(EnumWorldBlockLayer.CUTOUT, CUTOUT_MATERIAL);
        materials.put(EnumWorldBlockLayer.CUTOUT_MIPPED, CUTOUT_MIPPED_MATERIAL);
        materials.put(EnumWorldBlockLayer.TRANSLUCENT, TRANSLUCENT_MATERIAL);

        return new RenderPassConfiguration<EnumWorldBlockLayer>(
            materials, stages.build().asMap(),
            CUTOUT_MIPPED_MATERIAL, CUTOUT_MIPPED_MATERIAL, TRANSLUCENT_MATERIAL);
    }

    private static final class PipelineState implements TerrainRenderPass.PipelineState {

        private final int pass;
        private final boolean disableAlphaTest;

        private PipelineState(int pass, boolean disableAlphaTest) {
            this.pass = pass;
            this.disableAlphaTest = disableAlphaTest;
        }

        @Override
        public void setup() {
            GlStateManager.depthMask(true);

            if (this.pass == 0) {
                GlStateManager.alphaFunc(GL11.GL_GREATER, AlphaCutoffParameter.HALF.cutoff());
            }
            if (this.disableAlphaTest) {
                GlStateManager.disableAlpha();
            }
        }

        @Override
        public void clear() {
            if (this.pass == 0) {
                GlStateManager.alphaFunc(cachedAlphaFunc, cachedAlphaRef);
            }
            if (this.disableAlphaTest) {
                GlStateManager.enableAlpha();
            }
        }
    }
}
