package com.replaymod.render.hooks;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.Setting;
import com.replaymod.render.capturer.CaptureData;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.capturer.WorldRenderer;
import com.replaymod.render.mixin.GameRendererAccessor;
import com.replaymod.replay.ReplayModReplay;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public class EntityRendererHandler extends EventRegistrations implements WorldRenderer {
   public final Minecraft mc = MCVer.getMinecraft();
   protected final RenderSettings settings;
   private final RenderInfo renderInfo;
   public CaptureData data;
   public boolean omnidirectional;
   private final long startTime;
   private long fakeFinishTimeNano;

   public EntityRendererHandler(RenderSettings settings, RenderInfo renderInfo) {
      this.settings = settings;
      this.renderInfo = renderInfo;
      this.startTime = System.nanoTime();
      this.on(PreRenderHandCallback.EVENT, () -> this.omnidirectional);
      ((EntityRendererHandler.IEntityRenderer)this.mc.entityRenderer).replayModRender_setHandler(this);
      this.register();
   }

   @Override
   public void renderWorld(float partialTicks, CaptureData data) {
      this.data = data;
      long offsetMillis;
      if (ReplayMod.instance.getSettingsRegistry().get(Setting.FRAME_TIME_FROM_WORLD_TIME)) {
         offsetMillis = ReplayModReplay.instance.getReplayHandler().getReplaySender().currentTimeStamp();
      } else {
         offsetMillis = this.renderInfo.getFramesDone() * 1000L / this.settings.getFramesPerSecond();
      }

      long frameStartTimeNano = this.startTime + offsetMillis * 1000000L;
      this.renderWorld(partialTicks, frameStartTimeNano);
   }

   public void renderWorld(float partialTicks, long finishTimeNano) {
      this.fakeFinishTimeNano = finishTimeNano;
      com.replaymod.core.versions.forge.EventsAdapter.preRender();
      if (this.mc.theWorld != null && this.mc.thePlayer != null) {
         GameRendererAccessor gameRenderer = (GameRendererAccessor)this.mc.entityRenderer;
         GuiScreen orgScreen = this.mc.currentScreen;
         boolean orgPauseOnLostFocus = this.mc.gameSettings.pauseOnLostFocus;
         boolean orgRenderHand = gameRenderer.getRenderHand();

         try {
            this.mc.currentScreen = null;
            this.mc.gameSettings.pauseOnLostFocus = false;
            if (this.omnidirectional) {
               gameRenderer.setRenderHand(false);
            }

            this.mc.setIngameNotInFocus();
            this.mc.entityRenderer.updateCameraAndRender(partialTicks, finishTimeNano);
         } finally {
            this.mc.currentScreen = orgScreen;
            this.mc.gameSettings.pauseOnLostFocus = orgPauseOnLostFocus;
            gameRenderer.setRenderHand(orgRenderHand);
         }
      }

      com.replaymod.core.versions.forge.EventsAdapter.postRender();
   }

   @Override
   public void close() throws IOException {
      ((EntityRendererHandler.IEntityRenderer)this.mc.entityRenderer).replayModRender_setHandler(null);
      this.unregister();
   }

   @Override
   public void setOmnidirectional(boolean omnidirectional) {
      this.omnidirectional = omnidirectional;
   }

   public RenderSettings getSettings() {
      return this.settings;
   }

   public RenderInfo getRenderInfo() {
      return this.renderInfo;
   }

   public long getFakeFinishTimeNano() {
      return this.fakeFinishTimeNano;
   }

   public interface IEntityRenderer {
      void replayModRender_setHandler(EntityRendererHandler var1);

      EntityRendererHandler replayModRender_getHandler();
   }
}
