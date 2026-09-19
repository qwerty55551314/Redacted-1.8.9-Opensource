package com.replaymod.render.capturer;

import com.replaymod.render.frame.OpenGlFrame;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.Frame;
import com.replaymod.render.utils.ByteBufferPool;
import com.replaymod.render.utils.PixelBufferObject;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;

public abstract class PboOpenGlFrameCapturer<F extends Frame, D extends Enum<D> & CaptureData> extends OpenGlFrameCapturer<F, D> {
   private final boolean withDepth;
   private final D[] data;
   private PixelBufferObject pbo;
   private PixelBufferObject otherPBO;

   public PboOpenGlFrameCapturer(WorldRenderer worldRenderer, RenderInfo renderInfo, Class<D> type, int framePixels) {
      super(worldRenderer, renderInfo);
      this.withDepth = renderInfo.getRenderSettings().isDepthMap();
      this.data = type.getEnumConstants();
      int bufferSize = framePixels * (4 + (this.withDepth ? 4 : 0)) * this.data.length;
      this.pbo = new PixelBufferObject(bufferSize, PixelBufferObject.Usage.READ);
      this.otherPBO = new PixelBufferObject(bufferSize, PixelBufferObject.Usage.READ);
   }

   protected abstract F create(OpenGlFrame[] var1);

   private void swapPBOs() {
      PixelBufferObject old = this.pbo;
      this.pbo = this.otherPBO;
      this.otherPBO = old;
   }

   @Override
   public boolean isDone() {
      return this.framesDone >= this.renderInfo.getTotalFrames() + 2;
   }

   private F readFromPbo(ByteBuffer pboBuffer, int bytesPerPixel, boolean swapRB) {
      OpenGlFrame[] frames = new OpenGlFrame[this.data.length];
      int frameBufferSize = this.getFrameWidth() * this.getFrameHeight() * bytesPerPixel;

      for (int i = 0; i < frames.length; i++) {
         ByteBuffer frameBuffer = ByteBufferPool.allocate(frameBufferSize);
         ((Buffer)pboBuffer).limit(pboBuffer.position() + frameBufferSize);
         if (swapRB) {
            for (int j = 0; j < frameBufferSize; j += 4) {
               byte r = pboBuffer.get();
               byte g = pboBuffer.get();
               byte b = pboBuffer.get();
               byte a = pboBuffer.get();
               frameBuffer.put(b);
               frameBuffer.put(g);
               frameBuffer.put(r);
               frameBuffer.put(a);
            }
         } else {
            frameBuffer.put(pboBuffer);
         }

         ((Buffer)frameBuffer).rewind();
         frames[i] = new OpenGlFrame(this.framesDone - 2, this.frameSize, bytesPerPixel, frameBuffer);
      }

      return this.create(frames);
   }

   @Override
   public Map<Channel, F> process() {
      Map<Channel, F> channels = null;
      if (this.framesDone > 1) {
         this.pbo.bind();
         ByteBuffer pboBuffer = this.pbo.mapReadOnly();
         channels = new HashMap<>();
         channels.put(Channel.BRGA, this.readFromPbo(pboBuffer, 4, false));
         if (this.withDepth) {
            channels.put(Channel.DEPTH, this.readFromPbo(pboBuffer, 4, false));
         }

         this.pbo.unmap();
         this.pbo.unbind();
      }

      if (this.framesDone < this.renderInfo.getTotalFrames()) {
         float partialTicks = this.renderInfo.updateForNextFrame();

         for (D data : this.data) {
            this.renderFrame(this.framesDone, partialTicks, data);
         }
      }

      this.framesDone++;
      this.swapPBOs();
      return channels;
   }

   protected OpenGlFrame captureFrame(int frameId, D captureData) {
      this.pbo.bind();
      int offset = captureData.ordinal() * this.getFrameWidth() * this.getFrameHeight() * 4;
      this.frameBuffer().bindFramebuffer(true);
      GL11.glReadPixels(0, 0, this.getFrameWidth(), this.getFrameHeight(), 32993, 5121, offset);
      if (this.withDepth) {
         offset += this.data.length * this.getFrameWidth() * this.getFrameHeight() * 4;
         GL11.glReadPixels(0, 0, this.getFrameWidth(), this.getFrameHeight(), 6402, 5126, offset);
      }

      this.frameBuffer().unbindFramebuffer();
      this.pbo.unbind();
      return null;
   }

   @Override
   public void close() throws IOException {
      super.close();
      this.pbo.close();
      this.otherPBO.close();
   }
}
