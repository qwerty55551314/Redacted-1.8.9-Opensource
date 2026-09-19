package redacted.client.util.render;

import redacted.client.util.shader.GLSLShader;

// shared animated menu background. one static shader so every screen (main menu, SP, MP)
// rides the same time clock and we don't leak a GL program per screen-open.
public final class MenuBackground {

    private static GLSLShader shader;
    private static boolean failed;

    private MenuBackground() {}

    public static void render(final int width, final int height) {
        if (shader == null && !failed) {
            try {
                shader = new GLSLShader("redacted/shaders/passthrough.vsh", "redacted/shaders/mainmenu.fsh");
            } catch (final Exception e) {
                failed = true;
            }
        }
        // ponytail: dark base so a shader that failed to compile is a flat dark screen, not a garbage frame
        RenderUtil.drawRect(0, 0, width, height, 0xFF0A0A0A);
        if (shader != null) {
            shader.render(width, height); // no-ops internally if the program didn't link
        }
        RenderUtil.drawRect(0, 0, width, height, 0x40000000);
    }
}
