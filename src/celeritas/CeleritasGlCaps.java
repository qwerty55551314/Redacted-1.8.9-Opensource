package celeritas;

import org.lwjgl.opengl.GL;

/**
 * Driver capability answers that change which code path the renderer takes, resolved once and
 * cached.
 * <p>
 * Our client runs real LWJGL3 (GL.getCapabilities()), so these are read from LWJGL3's
 * {@link org.lwjgl.opengl.GLCapabilities} rather than from Natron's reflection over an LWJGL2
 * GLContext. Mirrors NatronGlCaps.
 */
public final class CeleritasGlCaps {

    private static Boolean nativeBufferCopy;
    private static Boolean persistentMapping;

    private CeleritasGlCaps() {
    }

    /** True if any capability in {@code names} is present. Unknown names read as absent. */
    static boolean anyOf(String... names) {
        try {
            final org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();

            for (String name : names) {
                try {
                    if (caps.getClass().getField(name).getBoolean(caps)) {
                        return true;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Older LWJGL builds simply do not know this capability.
                }
            }
        } catch (Throwable ignored) {
            // Treat an unreadable capability set as "not supported" and take the safe path.
        }
        return false;
    }

    /** Whether the driver can copy buffer-to-buffer on the GPU (glCopyBufferSubData). */
    public static boolean hasNativeBufferCopy() {
        if (nativeBufferCopy == null) {
            nativeBufferCopy = Boolean.valueOf(anyOf("OpenGL31", "GL_ARB_copy_buffer"));
        }
        return nativeBufferCopy.booleanValue();
    }

    /** Whether mesh uploads go through MappedStagingBuffer rather than FallbackStagingBuffer. */
    public static boolean hasPersistentMapping() {
        if (persistentMapping == null) {
            persistentMapping = Boolean.valueOf(anyOf("OpenGL44", "GL_ARB_buffer_storage"));
        }
        return persistentMapping.booleanValue();
    }
}
