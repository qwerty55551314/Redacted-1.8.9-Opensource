package celeritas;

import java.nio.FloatBuffer;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.lwjgl.opengl.GL11;

/**
 * Port of Natron's {@code NatronFogService}: reads fog from the driver once per frame and hands
 * the values to Celeritas's {@link FogService} contract.
 * <p>
 * The per-frame refresh is wired from the setupTerrain hook, so the driver is not queried up to
 * twenty times a frame (once per pass for the shader cache key plus once per setup()).
 */
public class CeleritasFogService implements FogService {

    private static float fogStart;
    private static float fogEnd;
    private static float fogDensity;
    private static final float[] fogColor = new float[4];
    private static ChunkFogMode fogMode = ChunkFogMode.NONE;

    private static final FloatBuffer colorBuffer = java.nio.ByteBuffer.allocateDirect(16)
        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();

    /** Reads all six values once. Call at most once per frame, before any pass draws. */
    public static void refresh() {
        fogStart = GL11.glGetFloat(GL11.GL_FOG_START);
        fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);
        fogDensity = GL11.glGetFloat(GL11.GL_FOG_DENSITY);

        colorBuffer.clear();
        GL11.glGetFloatv(GL11.GL_FOG_COLOR, colorBuffer);
        colorBuffer.get(fogColor, 0, 4);

        fogMode = GL11.glIsEnabled(GL11.GL_FOG)
            ? ChunkFogMode.fromGLMode(GL11.glGetInteger(GL11.GL_FOG_MODE))
            : ChunkFogMode.NONE;
    }

    @Override
    public float getFogStart() {
        return fogStart;
    }

    @Override
    public float getFogEnd() {
        return fogEnd;
    }

    @Override
    public float getFogDensity() {
        return fogDensity;
    }

    /** 1.8.9 has a single fog shape. */
    @Override
    public int getFogShapeIndex() {
        return 0;
    }

    @Override
    public float getFogCutoff() {
        return fogEnd;
    }

    @Override
    public float[] getFogColor() {
        return fogColor;
    }

    @Override
    public ChunkFogMode getFogMode() {
        return fogMode;
    }
}
