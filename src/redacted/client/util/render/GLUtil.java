package redacted.client.util.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * GL capability helpers shared by the 2D render utils. Mipmap generation needs
 * GL 3.0 core ({@code glGenerateMipmap}); on contexts without it we fall back
 * to plain LINEAR and the callers simply skip the mipmap filter mode.
 */
public final class GLUtil {

    private static volatile Boolean mipmapSupported;

    private GLUtil() {
    }

    public static boolean isMipmapsSupported() {
        Boolean supported = mipmapSupported;
        if (supported == null) {
            try {
                supported = org.lwjgl.opengl.GL.getCapabilities().OpenGL30;
            } catch (final Throwable t) {
                supported = false;
            }
            mipmapSupported = supported;
        }
        return supported;
    }

    /** Builds the mipmap chain for the currently bound 2D texture. Call only when {@link #isMipmapsSupported()}. */
    public static void generateMipmap() {
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }
}
