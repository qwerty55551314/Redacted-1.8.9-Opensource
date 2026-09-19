package redacted.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

public class StencilUtil {
    private static final int GL_DEPTH24_STENCIL8 = 34041;
    private static final int GL_STENCIL_ATTACHMENT = 36128;

    private static int lastFboId = -1;
    private static int lastFboWidth = -1;
    private static int lastFboHeight = -1;
    private static int lastDepthBuffer = -1;

    public static boolean checkSetupFBO() {
        Framebuffer fbo = Minecraft.getMinecraft().getFramebuffer();
        if (fbo != null && OpenGlHelper.isFramebufferEnabled() && fbo.framebufferObject > -1) {
            if (fbo.framebufferObject != lastFboId || fbo.depthBuffer != lastDepthBuffer || fbo.framebufferTextureWidth != lastFboWidth || fbo.framebufferTextureHeight != lastFboHeight) {
                if (!setupFBO(fbo)) {
                    lastFboId = -1;
                    lastDepthBuffer = -1;
                    lastFboWidth = -1;
                    lastFboHeight = -1;
                    return false;
                }

                lastFboId = fbo.framebufferObject;
                lastDepthBuffer = fbo.depthBuffer;
                lastFboWidth = fbo.framebufferTextureWidth;
                lastFboHeight = fbo.framebufferTextureHeight;
            }

            return true;
        }

        return false;
    }

    private static boolean setupFBO(Framebuffer fbo) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo.framebufferObject);

        int previousDepthBuffer = fbo.depthBuffer;
        int stencilDepthBufferID = OpenGlHelper.glGenRenderbuffers();
        if (stencilDepthBufferID <= 0) {
            return false;
        }

        OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, stencilDepthBufferID);
        OpenGlHelper.glRenderbufferStorage(OpenGlHelper.GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, fbo.framebufferTextureWidth, fbo.framebufferTextureHeight);
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, stencilDepthBufferID);
        OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, stencilDepthBufferID);

        boolean complete = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE;
        if (complete) {
            if (previousDepthBuffer > -1 && previousDepthBuffer != stencilDepthBufferID) {
                OpenGlHelper.glDeleteRenderbuffers(previousDepthBuffer);
            }

            fbo.depthBuffer = stencilDepthBufferID;
        } else {
            OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, 0);
            OpenGlHelper.glFramebufferRenderbuffer(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_DEPTH_ATTACHMENT, OpenGlHelper.GL_RENDERBUFFER, previousDepthBuffer > -1 ? previousDepthBuffer : 0);
            OpenGlHelper.glDeleteRenderbuffers(stencilDepthBufferID);
            fbo.depthBuffer = previousDepthBuffer;
        }

        OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER, 0);
        return complete;
    }

    public static boolean initStencilToWrite() {
        if (!checkSetupFBO()) {
            return false;
        }

        GL11.glStencilMask(0xFF);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        GL11.glColorMask(false, false, false, false);
        return true;
    }

    public static void readStencilBuffer(int ref) {
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, ref, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    public static void uninitStencilBuffer() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}
