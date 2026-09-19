package com.replaymod.render.capturer;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.frame.CubicOpenGlFrame;
import com.replaymod.render.frame.ODSOpenGlFrame;
import com.replaymod.render.frame.OpenGlFrame;
import com.replaymod.render.hooks.FogStateCallback;
import com.replaymod.render.hooks.Texture2DStateCallback;
import com.replaymod.render.rendering.Channel;
import com.replaymod.render.rendering.FrameCapturer;
import com.replaymod.render.shader.Program;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;

public class ODSFrameCapturer implements FrameCapturer<ODSOpenGlFrame> {
   private static final ResourceLocation vertexResource = MCVer.identifier("replaymod", "shader/ods.vert");
   private static final ResourceLocation fragmentResource = MCVer.identifier("replaymod", "shader/ods.frag");
   private final CubicPboOpenGlFrameCapturer left;
   private final CubicPboOpenGlFrameCapturer right;
   private final Program shaderProgram;
   private final Program.Uniform directionVariable;
   private final Program.Uniform leftEyeVariable;
   private EventRegistrations renderStateEvents;

   public ODSFrameCapturer(WorldRenderer worldRenderer, RenderInfo renderInfo, int frameSize) {
      RenderInfo fakeInfo = new RenderInfo() {
         private int call;
         private float partialTicks;

         @Override
         public ReadableDimension getFrameSize() {
            return renderInfo.getFrameSize();
         }

         @Override
         public int getFramesDone() {
            return renderInfo.getFramesDone();
         }

         @Override
         public int getTotalFrames() {
            return renderInfo.getTotalFrames();
         }

         @Override
         public float updateForNextFrame() {
            if (this.call++ % 2 == 0) {
               ODSFrameCapturer.this.unbindProgram();
               this.partialTicks = renderInfo.updateForNextFrame();
               ODSFrameCapturer.this.bindProgram();
            }

            return this.partialTicks;
         }

         @Override
         public RenderSettings getRenderSettings() {
            return renderInfo.getRenderSettings();
         }
      };
      this.left = new ODSFrameCapturer.CubicStereoFrameCapturer(worldRenderer, fakeInfo, frameSize);
      this.right = new ODSFrameCapturer.CubicStereoFrameCapturer(worldRenderer, fakeInfo, frameSize);

      try {
         this.shaderProgram = new Program(vertexResource, fragmentResource);
         this.leftEyeVariable = this.shaderProgram.getUniformVariable("leftEye");
         this.directionVariable = this.shaderProgram.getUniformVariable("direction");
      } catch (Exception e) {
         throw new ReportedException(CrashReport.makeCrashReport(e, "Creating ODS shaders"));
      }
   }

   private void bindProgram() {
      this.shaderProgram.use();
      this.setTexture("texture", 0);
      this.setTexture("lightMap", 1);
      this.renderStateEvents = new EventRegistrations();
      Program.Uniform[] texture2DUniforms = new Program.Uniform[]{
         this.shaderProgram.getUniformVariable("textureEnabled"),
         this.shaderProgram.getUniformVariable("lightMapEnabled"),
         this.shaderProgram.getUniformVariable("overlayEnabled")
      };
      this.renderStateEvents.on(Texture2DStateCallback.EVENT, (id, enabled) -> {
         if (id >= 0 && id < texture2DUniforms.length) {
            texture2DUniforms[id].set(enabled);
         }
      });
      Program.Uniform fogUniform = this.shaderProgram.getUniformVariable("fogEnabled");
      this.renderStateEvents.on(FogStateCallback.EVENT, fogUniform::set);
      this.renderStateEvents.register();
   }

   private void unbindProgram() {
      this.renderStateEvents.unregister();
      this.renderStateEvents = null;
      this.shaderProgram.stopUsing();
   }

   private void setTexture(String texture, int i) {
      this.shaderProgram.getUniformVariable(texture).set(i);
   }

   @Override
   public boolean isDone() {
      return this.left.isDone() && this.right.isDone();
   }

   @Override
   public Map<Channel, ODSOpenGlFrame> process() {
      this.bindProgram();
      this.leftEyeVariable.set(true);
      Map<Channel, CubicOpenGlFrame> leftChannels = this.left.process();
      this.leftEyeVariable.set(false);
      Map<Channel, CubicOpenGlFrame> rightChannels = this.right.process();
      this.unbindProgram();
      if (leftChannels != null && rightChannels != null) {
         Map<Channel, ODSOpenGlFrame> result = new HashMap<>();

         for (Channel channel : Channel.values()) {
            CubicOpenGlFrame leftFrame = leftChannels.get(channel);
            CubicOpenGlFrame rightFrame = rightChannels.get(channel);
            if (leftFrame != null && rightFrame != null) {
               result.put(channel, new ODSOpenGlFrame(leftFrame, rightFrame));
            }
         }

         return result;
      } else {
         return null;
      }
   }

   @Override
   public void close() throws IOException {
      this.left.close();
      this.right.close();
      this.shaderProgram.delete();
   }

   private class CubicStereoFrameCapturer extends CubicPboOpenGlFrameCapturer {
      public CubicStereoFrameCapturer(WorldRenderer worldRenderer, RenderInfo renderInfo, int frameSize) {
         super(worldRenderer, renderInfo, frameSize);
      }

      protected OpenGlFrame renderFrame(int frameId, float partialTicks, CubicOpenGlFrameCapturer.Data captureData) {
         ODSFrameCapturer.this.directionVariable.set(captureData.ordinal());
         return super.renderFrame(frameId, partialTicks, captureData);
      }
   }
}
