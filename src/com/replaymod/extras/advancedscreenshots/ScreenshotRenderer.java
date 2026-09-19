package com.replaymod.extras.advancedscreenshots;

import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.core.versions.Window;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.render.rendering.Pipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;

public class ScreenshotRenderer implements RenderInfo {
   private final Minecraft mc = MCVer.getMinecraft();
   private final RenderSettings settings;
   private int framesDone;

   public ScreenshotRenderer(RenderSettings settings) {
      this.settings = settings;
   }

   public boolean renderScreenshot() throws Throwable {
      try {
         Window window = new Window(this.mc);
         int widthBefore = window.getFramebufferWidth();
         int heightBefore = window.getFramebufferHeight();
         ForceChunkLoadingHook clrg = new ForceChunkLoadingHook(this.mc.renderGlobal);
         if (this.settings.getRenderMethod() == RenderSettings.RenderMethod.BLEND) {
            BlendState.setState(new BlendState(this.settings.getOutputFile()));
            Pipelines.newBlendPipeline(this).run();
         } else {
            Pipelines.newPipeline(this.settings.getRenderMethod(), this, new ScreenshotWriter(this.settings.getOutputFile())).run();
         }

         clrg.uninstall();
         MCVer.resizeMainWindow(this.mc, widthBefore, heightBefore);
         return true;
      } catch (OutOfMemoryError e) {
         e.printStackTrace();
         CrashReport report = CrashReport.makeCrashReport(e, "Creating Equirectangular Screenshot");
         MCVer.getMinecraft().crashed(report);
         return false;
      }
   }

   @Override
   public ReadableDimension getFrameSize() {
      return new Dimension(this.settings.getVideoWidth(), this.settings.getVideoHeight());
   }

   @Override
   public int getFramesDone() {
      return this.framesDone;
   }

   @Override
   public int getTotalFrames() {
      return 2;
   }

   @Override
   public float updateForNextFrame() {
      this.framesDone++;
      return ((MinecraftAccessor)this.mc).getTimer().renderPartialTicks;
   }

   @Override
   public RenderSettings getRenderSettings() {
      return this.settings;
   }
}
