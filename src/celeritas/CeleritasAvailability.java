package celeritas;

/**
 * Whether this machine may run Celeritas at all, checked once before anything Celeritas-specific
 * is constructed.
 * <p>
 * Celeritas needs a modern GL 3.2+ core context (vertex array objects, base-vertex indexed
 * drawing, GLSL 330 core shaders). The client's LWJGL3 context is created via
 * {@code Display.create(new PixelFormat().withDepthBits(24))} which requests a core-ish context;
 * on a driver that only offers a compatibility 2.1 context, the shaders fail to compile. When that
 * is the case we fall back to vanilla terrain rendering entirely.
 */
public final class CeleritasAvailability {

    /** One OpenGL feature Celeritas has no fallback for, and why it is needed. */
    private static final class Requirement {
        final String reason;
        final String[] capabilityNames;

        Requirement(String reason, String... capabilityNames) {
            this.reason = reason;
            this.capabilityNames = capabilityNames;
        }
    }

    private static final Requirement[] REQUIREMENTS = {
        new Requirement("vertex array objects",
            "OpenGL30", "GL_APPLE_vertex_array_object", "GL_ARB_vertex_array_object"),
        new Requirement("base-vertex indexed drawing",
            "OpenGL32", "GL_ARB_draw_elements_base_vertex"),
        // No extension name to fall back to: GLSL version support tracks the context version.
        new Requirement("GLSL 330 core shaders", "OpenGL33"),
    };

    private static Boolean supported;
    private static String reason = "";
    private static boolean warned;

    private CeleritasAvailability() {
    }

    /** Resolved once and cached: GL capabilities do not change during a session. */
    public static boolean isSupported() {
        if (supported == null) {
            final StringBuilder missing = new StringBuilder();

            for (Requirement requirement : REQUIREMENTS) {
                if (!CeleritasGlCaps.anyOf(requirement.capabilityNames)) {
                    if (missing.length() > 0) {
                        missing.append(", ");
                    }
                    missing.append(requirement.reason);
                }
            }

            reason = missing.toString();
            supported = Boolean.valueOf(reason.isEmpty());
        }

        return supported.booleanValue();
    }

    /** Empty when {@link #isSupported()} is true. */
    public static String unsupportedReason() {
        isSupported();
        return reason;
    }
}
