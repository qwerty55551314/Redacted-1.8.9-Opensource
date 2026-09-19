package com.replaymod.replay;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.core.Module;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.ModCompat;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.replay.camera.CameraController;
import com.replaymod.replay.camera.CameraControllerRegistry;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.camera.ClassicCameraController;
import com.replaymod.replay.camera.VanillaCameraController;
import com.replaymod.replay.gui.screen.GuiModCompatWarning;
import com.replaymod.replay.handler.GuiHandler;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReplayModReplay implements Module {
   public GuiHandler guiHandler;
   public static ReplayModReplay instance;
   private ReplayMod core;
   public KeyBindingRegistry.Binding keyPlayPause;
   private final CameraControllerRegistry cameraControllerRegistry;
   public static Logger LOGGER = LogManager.getLogger();
   private ReplayHandler replayHandler;

   public ReplayHandler getReplayHandler() {
      return this.replayHandler;
   }

   public ReplayModReplay(ReplayMod core) {
      instance = this;
      this.cameraControllerRegistry = new CameraControllerRegistry();
      this.core = core;
      core.getSettingsRegistry().register(Setting.class);
   }

   @Override
   public void registerKeyBindings(KeyBindingRegistry registry) {
      registry.registerKeyBinding("replaymod.input.marker", 50, new Runnable() {
         @Override
         public void run() {
            if (ReplayModReplay.this.replayHandler != null) {
               CameraEntity camera = ReplayModReplay.this.replayHandler.getCameraEntity();
               if (camera != null) {
                  Marker marker = new Marker();
                  marker.setTime(ReplayModReplay.this.replayHandler.getReplaySender().currentTimeStamp());
                  marker.setX(camera.posX);
                  marker.setY(camera.posY);
                  marker.setZ(camera.posZ);
                  marker.setYaw(camera.rotationYaw);
                  marker.setPitch(camera.rotationPitch);
                  marker.setRoll(camera.roll);
                  ReplayModReplay.this.replayHandler.getOverlay().timeline.addMarker(marker);
               }
            }
         }
      }, true);
      registry.registerKeyBinding("replaymod.input.thumbnail", 49, new Runnable() {
         @Override
         public void run() {
            if (ReplayModReplay.this.replayHandler != null) {
               Minecraft mc = MCVer.getMinecraft();
               ListenableFuture<NoGuiScreenshot> future = NoGuiScreenshot.take(mc, 1280, 720);
               Futures.addCallback(future, new FutureCallback<NoGuiScreenshot>() {
                  public void onSuccess(NoGuiScreenshot result) {
                     try {
                        ReplayModReplay.this.core.printInfoToChat("replaymod.chat.savingthumb");
                        BufferedImage image = result.getImage().toBufferedImage();
                        BufferedImage bgrImage = new BufferedImage(image.getWidth(), image.getHeight(), 5);
                        Graphics graphics = bgrImage.getGraphics();
                        graphics.drawImage(image, 0, 0, null);
                        graphics.dispose();
                        ReplayModReplay.this.replayHandler.getReplayFile().writeThumb(bgrImage);
                        ReplayModReplay.this.core.printInfoToChat("replaymod.chat.savedthumb");
                     } catch (IOException e) {
                        e.printStackTrace();
                     }
                  }

                  public void onFailure(Throwable t) {
                     t.printStackTrace();
                     ReplayModReplay.this.core.printWarningToChat("replaymod.chat.failedthumb");
                  }
               });
            }
         }
      }, true);
      this.keyPlayPause = registry.registerKeyBinding("replaymod.input.playpause", 25, new Runnable() {
         @Override
         public void run() {
            if (ReplayModReplay.this.replayHandler != null) {
               ReplayModReplay.this.replayHandler.getOverlay().playPauseButton.onClick(new Click(-1, -1, 0, 0));
            } else {
               /* 
                * No replay active: we may be recording, in which case the play/pause key toggles the recording pause (indicator + chat).
                * When recording is stopped (auto-start off and not started yet), P starts the recording instead.
                */
               com.replaymod.recording.ReplayModRecording recording = com.replaymod.recording.ReplayModRecording.instance;
               if (recording != null && recording.getConnectionEventHandler() != null) {
                  com.replaymod.recording.gui.GuiRecordingControls controls = recording.getConnectionEventHandler().getGuiControls();
                  if (controls != null) {
                     if (controls.isStopped()) {
                        controls.startRecording();
                     } else {
                        controls.togglePause();
                     }
                  }
               }
            }
         }
      }, false);
      this.core.getKeyBindingRegistry().registerKeyBinding("replaymod.input.rollclockwise", 38, () -> {}, true);
      this.core.getKeyBindingRegistry().registerKeyBinding("replaymod.input.rollcounterclockwise", 36, () -> {}, true);
      this.core
         .getKeyBindingRegistry()
         .registerKeyBinding(
            "replaymod.input.resettilt",
            37,
            () -> Optional.ofNullable(this.replayHandler).map(ReplayHandler::getCameraEntity).ifPresent(c -> c.roll = 0.0F),
            true
         );
   }

   @Override
   public void initClient() {
      this.cameraControllerRegistry.register("replaymod.camera.classic", new Function<CameraEntity, CameraController>() {
         @Nullable
         public CameraController apply(CameraEntity cameraEntity) {
            return new ClassicCameraController(cameraEntity);
         }
      });
      this.cameraControllerRegistry.register("replaymod.camera.vanilla", new Function<CameraEntity, CameraController>() {
         @Nullable
         public CameraController apply(@Nullable CameraEntity cameraEntity) {
            return new VanillaCameraController(ReplayModReplay.this.core.getMinecraft(), cameraEntity);
         }
      });
      this.guiHandler = new GuiHandler(this);
      this.guiHandler.register();
   }

   public void startReplay(File file) throws IOException {
      this.startReplay(this.core.files.open(file.toPath()));
   }

   public void startReplay(ReplayFile replayFile) throws IOException {
      this.startReplay(replayFile, true, true);
   }

   public ReplayHandler startReplay(ReplayFile replayFile, boolean checkModCompat, boolean asyncMode) throws IOException {
      if (this.replayHandler != null) {
         this.replayHandler.endReplay();
      }

      if (checkModCompat) {
         ModCompat.ModInfoDifference modDifference = new ModCompat.ModInfoDifference(replayFile.getModInfo());
         if (!modDifference.getMissing().isEmpty() || !modDifference.getDiffering().isEmpty()) {
            GuiModCompatWarning screen = new GuiModCompatWarning(modDifference);
            screen.loadButton.onClick(() -> {
               try {
                  this.startReplay(replayFile, false, asyncMode);
               } catch (IOException e) {
                  e.printStackTrace();
               }
            });
            screen.display();
            return null;
         }
      }

      this.replayHandler = new ReplayHandler(replayFile, asyncMode);
      KeyBinding.resetKeyBindingArrayAndHash();
      return this.replayHandler;
   }

   public void forcefullyStopReplay() {
      this.replayHandler = null;
      KeyBinding.resetKeyBindingArrayAndHash();
   }

   public ReplayMod getCore() {
      return this.core;
   }

   public Logger getLogger() {
      return LOGGER;
   }

   public CameraControllerRegistry getCameraControllerRegistry() {
      return this.cameraControllerRegistry;
   }

   public CameraController createCameraController(CameraEntity cameraEntity) {
      String controllerName = this.core.getSettingsRegistry().get(Setting.CAMERA);
      return this.cameraControllerRegistry.create(controllerName, cameraEntity);
   }
}
