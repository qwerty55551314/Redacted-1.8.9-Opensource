package redacted.client.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class GLSLShader {

    private int program;

    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();
    // real-time clock: the old per-frame `time += 0.05` made the animation speed track
    // the framerate (warp-speed at 300fps), which someone "fixed" by capping menu FPS
    // to 30. 1.5 units/sec reproduces the intended 30fps x 0.05 pace, at any framerate
    private final long created = System.currentTimeMillis();

    public GLSLShader(final String vertexPath, final String fragmentPath) {
        int vertId = 0;
        int fragId = 0;

        try {
            final InputStream vertStream = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("minecraft", vertexPath)).getInputStream();
            vertId = createShader(IOUtils.toString(vertStream), ARBVertexShader.GL_VERTEX_SHADER_ARB);
            IOUtils.closeQuietly(vertStream);

            final InputStream fragStream = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("minecraft", fragmentPath)).getInputStream();
            fragId = createShader(IOUtils.toString(fragStream), ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);
            IOUtils.closeQuietly(fragStream);
        } catch (final Exception e) {
            System.err.println("[GLSLShader] Failed to load shader files:");
            e.printStackTrace();
            return;
        }

        if (vertId == 0 || fragId == 0) {
            System.err.println("[GLSLShader] Shader compilation returned 0");
            return;
        }

        program = ARBShaderObjects.glCreateProgramObjectARB();
        if (program == 0) {
            System.err.println("[GLSLShader] glCreateProgramObjectARB returned 0");
            return;
        }

        ARBShaderObjects.glAttachObjectARB(program, vertId);
        ARBShaderObjects.glAttachObjectARB(program, fragId);
        ARBShaderObjects.glLinkProgramARB(program);
        ARBShaderObjects.glValidateProgramARB(program);

        System.out.println("[GLSLShader] Shader loaded successfully, program=" + program);
    }

    public void render(final int width, final int height) {
        if (program == 0) return;
        GL11.glPushMatrix();
        try {
            GL20.glUseProgram(program);

            if (uniforms.isEmpty()) {
                uniforms.put("resolution", GL20.glGetUniformLocation(program, "resolution"));
                uniforms.put("time", GL20.glGetUniformLocation(program, "time"));
                uniforms.put("mouse", GL20.glGetUniformLocation(program, "mouse"));
            }

            final ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            final Integer resLoc = uniforms.get("resolution");
            if (resLoc != null && resLoc > -1) {
                GL20.glUniform2f(resLoc, sr.getScaledWidth() * 2, sr.getScaledHeight() * 2);
            }

            final Integer timeLoc = uniforms.get("time");
            if (timeLoc != null && timeLoc > -1) {
                GL20.glUniform1f(timeLoc, (System.currentTimeMillis() - this.created) * 0.0015f);
            }

            final Integer mouseLoc = uniforms.get("mouse");
            if (mouseLoc != null && mouseLoc > -1) {
                GL20.glUniform2f(mouseLoc, 0.5f, 0.5f);
            }

            final Tessellator tess = Tessellator.getInstance();
            final WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            wr.pos(0, height, 0).endVertex();
            wr.pos(width, height, 0).endVertex();
            wr.pos(width, 0, 0).endVertex();
            wr.pos(0, 0, 0).endVertex();
            tess.draw();
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            // always unbind + pop, else a mid-draw throw leaves the whole screen rendering through this shader
            GL20.glUseProgram(0);
            GL11.glPopMatrix();
        }
    }

    private int createShader(final String source, final int type) {
        try {
            final int shader = ARBShaderObjects.glCreateShaderObjectARB(type);
            if (shader == 0) return 0;

            ARBShaderObjects.glShaderSourceARB(shader, source);
            ARBShaderObjects.glCompileShaderARB(shader);

            final int compiled = ARBShaderObjects.glGetObjectParameteriARB(shader,
                    ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB);
            if (compiled == GL11.GL_FALSE) {
                final String log = ARBShaderObjects.glGetInfoLogARB(shader,
                        ARBShaderObjects.glGetObjectParameteriARB(shader,
                                ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB));
                System.err.println("[GLSLShader] Compile error: " + log);
                ARBShaderObjects.glDeleteObjectARB(shader);
                return 0;
            }

            return shader;
        } catch (final Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
