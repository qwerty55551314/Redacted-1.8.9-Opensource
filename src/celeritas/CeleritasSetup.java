package celeritas;

import net.minecraft.client.renderer.OpenGlHelper;
import org.embeddedt.embeddium.impl.gl.device.GLRenderDevice;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Port of Natron's {@code NatronCeleritasSetup}: replaces Celeritas's throwing
 * {@code VANILLA_STATE_RESETTER} stub with one that unbinds the array buffer before entering
 * managed code, so the renderer starts from a known point.
 */
public final class CeleritasSetup {

    private static boolean initialized;

    private CeleritasSetup() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        GLRenderDevice.VANILLA_STATE_RESETTER = new Runnable() {
            @Override
            public void run() {
                // Restore vanilla fixed-function state that Celeritas's managed blocks leave behind.
                // Celeritas renders terrain with core-profile vertex array objects and a GLSL program.
                // Vanilla 1.8.9 draws entities with fixed-function vertex pointers and no program. If
                // a VAO or program stays bound after terrain, vanilla's glVertexPointer/glColorPointer
                // calls are captured/ignored and the GLSL program's attributes are used instead ->
                // entity geometry reads garbage (e.g. skeleton heads/ribs go black through the
                // transparent texture). Unbind the VAO and program and reset client arrays so
                // vanilla's fixed-function entity rendering is clean again.
                if (org.lwjgl.opengl.GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING) != 0) {
                    org.lwjgl.opengl.GL30.glBindVertexArray(0);
                }
                OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
                GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
                GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
                GL20.glUseProgram(0);
            }
        };
    }
}
