package com.replaymod.render.rendering;

import com.google.common.collect.Iterables;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.GLFW;
import com.replaymod.core.versions.Window;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.pathing.player.AbstractTimelinePlayer;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.render.CameraPathExporter;
import com.replaymod.render.EXRWriter;
import com.replaymod.render.FFmpegWriter;
import com.replaymod.render.PNGWriter;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.ReplayModRender;
import com.replaymod.render.blend.BlendState;
import com.replaymod.render.capturer.RenderInfo;
import com.replaymod.render.events.ReplayRenderCallback;
import com.replaymod.render.frame.BitmapFrame;
import com.replaymod.render.gui.GuiRenderingDone;
import com.replaymod.render.gui.GuiVideoRenderer;
import com.replaymod.render.gui.progress.VirtualWindow;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.render.metadata.MetadataInjector;
import com.replaymod.render.mixin.WorldRendererAccessor;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;

public class VideoRenderer implements RenderInfo {
   private static final ResourceLocation SOUND_RENDER_SUCCESS = MCVer.identifier("replaymod", "render_success");
   private final Minecraft mc = com.replaymod.core.versions.MCVer.getMinecraft();
   private final RenderSettings settings;
   private final ReplayHandler replayHandler;
   private final Timeline timeline;
   private final Pipeline renderingPipeline;
   private final FFmpegWriter ffmpegWriter;
   private final CameraPathExporter cameraPathExporter;
   private int fps;
   private boolean mouseWasGrabbed;
   private boolean debugInfoWasShown;
   private Map<SoundCategory, Float> originalSoundLevels;
   private VideoRenderer.TimelinePlayer timelinePlayer;
   private Future<Void> timelinePlayerFuture;
   private ForceChunkLoadingHook forceChunkLoadingHook;
   private int framesDone;
   private int totalFrames;
   private final VirtualWindow guiWindow = new VirtualWindow(this.mc);
   private final GuiVideoRenderer gui;
   private boolean paused;
   private boolean cancelled;
   private volatile Throwable failureCause;

   public VideoRenderer(RenderSettings settings, ReplayHandler replayHandler, Timeline timeline) throws IOException {
      this.settings = settings;
      this.replayHandler = replayHandler;
      this.timeline = timeline;
      this.gui = new GuiVideoRenderer(this);
      if (settings.getRenderMethod() == RenderSettings.RenderMethod.BLEND) {
         BlendState.setState(new BlendState(settings.getOutputFile()));
         this.renderingPipeline = Pipelines.newBlendPipeline(this);
         this.ffmpegWriter = null;
      } else {
         final FrameConsumer<BitmapFrame> frameConsumer;
         if (settings.getEncodingPreset() == RenderSettings.EncodingPreset.EXR) {
            frameConsumer = EXRWriter.create(settings.getOutputFile().toPath(), settings.isIncludeAlphaChannel());
         } else if (settings.getEncodingPreset() == RenderSettings.EncodingPreset.PNG) {
            frameConsumer = new PNGWriter(settings.getOutputFile().toPath(), settings.isIncludeAlphaChannel());
         } else {
            frameConsumer = new FFmpegWriter(this);
         }

         this.ffmpegWriter = frameConsumer instanceof FFmpegWriter ? (FFmpegWriter)frameConsumer : null;
         FrameConsumer<BitmapFrame> previewingFrameConsumer = new FrameConsumer<BitmapFrame>() {
            private int lastFrameId = -1;

            @Override
            public void consume(Map<Channel, BitmapFrame> channels) {
               BitmapFrame bgra = channels.get(Channel.BRGA);
               if (bgra != null) {
                  synchronized (this) {
                     int frameId = bgra.getFrameId();
                     if (this.lastFrameId < frameId) {
                        this.lastFrameId = frameId;
                        VideoRenderer.this.gui.updatePreview(bgra.getByteBuffer(), bgra.getSize());
                     }
                  }
               }

               frameConsumer.consume(channels);
            }

            @Override
            public void close() throws IOException {
               frameConsumer.close();
            }

            @Override
            public boolean isParallelCapable() {
               return frameConsumer.isParallelCapable();
            }
         };
         this.renderingPipeline = Pipelines.newPipeline(settings.getRenderMethod(), this, previewingFrameConsumer);
      }

      if (settings.isCameraPathExport()) {
         this.cameraPathExporter = new CameraPathExporter(settings);
      } else {
         this.cameraPathExporter = null;
      }
   }

   public boolean renderVideo() throws Throwable {
      ReplayRenderCallback.Pre.EVENT.invoker().beforeRendering(this);
      this.setup();
      this.drawGui();
      ReplayTimer timer = (ReplayTimer)((MinecraftAccessor)this.mc).getTimer();
      Optional<Integer> optionalVideoStartTime = this.timeline.getValue(TimestampProperty.PROPERTY, 0L);
      if (optionalVideoStartTime.isPresent()) {
         int videoStart = optionalVideoStartTime.get();
         if (videoStart > 1000) {
            int replayTime = videoStart - 1000;
            timer.elapsedPartialTicks = timer.renderPartialTicks = 0.0F;
            timer.timerSpeed = 1.0F;

            while (replayTime < videoStart) {
               timer.elapsedTicks = 1;
               replayTime += 50;
               this.replayHandler.getReplaySender().sendPacketsTill(replayTime);
               this.tick();
            }
         }
      }

      ((WorldRendererAccessor)this.mc.renderGlobal).setRenderEntitiesStartupCounter(0);
      this.renderingPipeline.run();
      if (((MinecraftAccessor)this.mc).getCrashReporter() != null) {
         throw new ReportedException(((MinecraftAccessor)this.mc).getCrashReporter());
      }

      if (this.settings.isInjectSphericalMetadata()) {
         MetadataInjector.injectMetadata(
            this.settings.getRenderMethod(),
            this.settings.getOutputFile(),
            this.settings.getTargetVideoWidth(),
            this.settings.getTargetVideoHeight(),
            this.settings.getSphericalFovX(),
            this.settings.getSphericalFovY()
         );
      }

      this.finish();
      ReplayRenderCallback.Post.EVENT.invoker().afterRendering(this);
      if (this.failureCause != null) {
         throw this.failureCause;
      } else {
         return !this.cancelled;
      }
   }

   @Override
   public float updateForNextFrame() {
      this.guiWindow.bind();
      if (!this.settings.isHighPerformance() || this.framesDone % this.fps == 0) {
         while (this.drawGui() && this.paused) {
            try {
               Thread.sleep(50L);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      }

      ReplayTimer timer = (ReplayTimer)((MinecraftAccessor)this.mc).getTimer();
      timer.updateTimer();
      int elapsedTicks = timer.elapsedTicks;
      this.executeTaskQueue();

      while (elapsedTicks-- > 0) {
         this.tick();
      }

      this.guiWindow.unbind();
      if (this.cameraPathExporter != null) {
         this.cameraPathExporter.recordFrame(timer.renderPartialTicks);
      }

      this.framesDone++;
      return timer.renderPartialTicks;
   }

   @Override
   public RenderSettings getRenderSettings() {
      return this.settings;
   }

   private void setup() {
      this.timelinePlayer = new VideoRenderer.TimelinePlayer(this.replayHandler);
      this.timelinePlayerFuture = this.timelinePlayer.start(this.timeline);
      if (!OpenGlHelper.isFramebufferEnabled()) {
         Display.setResizable(false);
      }

      if (this.mc.gameSettings.showDebugInfo) {
         this.mc.gameSettings.showDebugInfo = false;
         this.debugInfoWasShown = true;
      }

      if (Mouse.isGrabbed()) {
         this.mouseWasGrabbed = true;
      }

      Mouse.setGrabbed(false);
      this.originalSoundLevels = new EnumMap<>(SoundCategory.class);

      for (SoundCategory category : SoundCategory.values()) {
         if (category != SoundCategory.MASTER) {
            this.originalSoundLevels.put(category, this.mc.gameSettings.getSoundLevel(category));
            this.mc.gameSettings.setSoundLevel(category, 0.0F);
         }
      }

      this.fps = this.settings.getFramesPerSecond();
      long duration = 0L;

      for (Path path : this.timeline.getPaths()) {
         if (path.isActive()) {
            path.updateAll();
            Collection<Keyframe> keyframes = path.getKeyframes();
            if (keyframes.size() > 0) {
               duration = Math.max(duration, ((Keyframe)Iterables.getLast(keyframes)).getTime());
            }
         }
      }

      this.totalFrames = (int)(duration * this.fps / 1000L);
      if (this.cameraPathExporter != null) {
         this.cameraPathExporter.setup(this.totalFrames);
      }

      this.gui.toMinecraft().setWorldAndResolution(this.mc, new Window(this.mc).getScaledWidth(), new Window(this.mc).getScaledHeight());
      this.forceChunkLoadingHook = new ForceChunkLoadingHook(this.mc.renderGlobal);
   }

   private void finish() {
      if (!this.timelinePlayerFuture.isDone()) {
         this.timelinePlayerFuture.cancel(false);
      }

      this.timelinePlayer.onTick();
      this.guiWindow.close();
      if (!OpenGlHelper.isFramebufferEnabled()) {
         Display.setResizable(true);
      }

      if (this.debugInfoWasShown) {
         this.mc.gameSettings.showDebugInfo = true;
      }

      if (this.mouseWasGrabbed) {
         this.mc.mouseHelper.grabMouseCursor();
      }

      for (Entry<SoundCategory, Float> entry : this.originalSoundLevels.entrySet()) {
         this.mc.gameSettings.setSoundLevel(entry.getKey(), entry.getValue());
      }

      this.mc.displayGuiScreen(null);
      this.forceChunkLoadingHook.uninstall();
      if (!this.hasFailed() && this.cameraPathExporter != null) {
         try {
            this.cameraPathExporter.finish();
         } catch (IOException e) {
            this.setFailure(e);
         }
      }

      this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(SOUND_RENDER_SUCCESS, 1.0F));

      try {
         if (!this.hasFailed() && this.ffmpegWriter != null) {
            new GuiRenderingDone(ReplayModRender.instance, this.ffmpegWriter.getVideoFile(), this.totalFrames, this.settings).display();
         }
      } catch (FFmpegWriter.FFmpegStartupException e) {
         this.setFailure(e);
      }

      com.replaymod.core.versions.MCVer.resizeMainWindow(this.mc, this.guiWindow.getFramebufferWidth(), this.guiWindow.getFramebufferHeight());
   }

   private void executeTaskQueue() {
      Queue<FutureTask<?>> scheduledTasks = ((MinecraftAccessor)this.mc).getScheduledTasks();
      synchronized (scheduledTasks) {
         while (!scheduledTasks.isEmpty()) {
            scheduledTasks.poll().run();
         }
      }

      this.mc.currentScreen = this.gui.toMinecraft();
   }

   private void tick() {
      try {
         this.mc.runTick();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public boolean drawGui() {
      Window window = new Window(this.mc);
      if (!GLFW.glfwWindowShouldClose(window.getHandle()) && ((MinecraftAccessor)this.mc).getCrashReporter() == null) {
         com.replaymod.core.versions.MCVer.pushMatrix();
         GlStateManager.clear(16640);
         GlStateManager.enableTexture2D();
         this.guiWindow.beginWrite();
         this.mc.entityRenderer.setupOverlayRendering();
         this.gui.toMinecraft().setWorldAndResolution(this.mc, window.getScaledWidth(), window.getScaledHeight());

         try {
            this.gui.toMinecraft().handleInput();
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

         int mouseX = Mouse.getX() * window.getScaledWidth() / this.mc.displayWidth;
         int mouseY = window.getScaledHeight() - Mouse.getY() * window.getScaledHeight() / this.mc.displayHeight - 1;
         this.gui.toMinecraft().updateScreen();
         this.gui.toMinecraft().drawScreen(mouseX, mouseY, 0.0F);
         this.guiWindow.endWrite();
         com.replaymod.core.versions.MCVer.popMatrix();
         com.replaymod.core.versions.MCVer.pushMatrix();
         this.guiWindow.flip();
         com.replaymod.core.versions.MCVer.popMatrix();
         if (Mouse.isGrabbed()) {
            Mouse.setGrabbed(false);
         }

         return !this.hasFailed() && !this.cancelled;
      } else {
         return false;
      }
   }

   @Override
   public int getFramesDone() {
      return this.framesDone;
   }

   @Override
   public ReadableDimension getFrameSize() {
      return new Dimension(this.settings.getVideoWidth(), this.settings.getVideoHeight());
   }

   @Override
   public int getTotalFrames() {
      return this.totalFrames;
   }

   public int getVideoTime() {
      return this.framesDone * 1000 / this.fps;
   }

   public void setPaused(boolean paused) {
      this.paused = paused;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void cancel() {
      if (this.ffmpegWriter != null) {
         this.ffmpegWriter.abort();
      }

      this.cancelled = true;
      this.renderingPipeline.cancel();
   }

   public boolean hasFailed() {
      return this.failureCause != null;
   }

   public synchronized void setFailure(Throwable cause) {
      if (this.failureCause != null) {
         ReplayModRender.LOGGER.error("Further failure during failed rendering: ", cause);
      } else {
         ReplayModRender.LOGGER.error("Failure during rendering: ", cause);
         this.failureCause = cause;
         this.cancel();
      }
   }

   public static String[] checkCompat(Stream<RenderSettings> settings) {
      return settings.<String[]>map(VideoRenderer::checkCompat).filter(Objects::nonNull).findFirst().orElse(null);
   }

   public static String[] checkCompat(RenderSettings settings) {
      return null;
   }

   private class TimelinePlayer extends AbstractTimelinePlayer {
      public TimelinePlayer(ReplayHandler replayHandler) {
         super(replayHandler);
      }

      @Override
      public long getTimePassed() {
         return VideoRenderer.this.getVideoTime();
      }
   }
}
