package com.replaymod.replay;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.replay.camera.CameraController;
import com.replaymod.replay.camera.CameraEntity;
import java.io.IOException;
import net.minecraft.client.Minecraft;

public class InputReplayTimer {
   public static void updateInReplay() {
      ReplayModReplay mod = ReplayModReplay.instance;
      if (mod == null || ReplayMod.instance == null) {
         // ReplayMod failed to initialize (or is still starting up): nothing to drive.
         return;
      }
      Minecraft mc = mod.getCore().getMinecraft();
      ReplayMod.instance.runTasks();
      if (mod.getReplayHandler() != null && mc.theWorld != null && mc.thePlayer != null) {
         if (mc.currentScreen != null) {
            try {
               mc.currentScreen.handleInput();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }

         if (mc.currentScreen == null || mc.currentScreen.allowUserInput) {
            ((MCVer.MinecraftMethodAccessor)mc).replayModSetEarlyReturnFromRunTick(true);

            try {
               mc.runTick();
            } catch (IOException e) {
               e.printStackTrace();
            }

            ((MCVer.MinecraftMethodAccessor)mc).replayModSetEarlyReturnFromRunTick(false);
         }
      }
   }

   public static void handleScroll(int wheel) {
      if (wheel != 0) {
         ReplayHandler replayHandler = ReplayModReplay.instance.getReplayHandler();
         if (replayHandler != null) {
            CameraEntity cameraEntity = replayHandler.getCameraEntity();
            if (cameraEntity != null) {
               CameraController controller = cameraEntity.getCameraController();
               /* 
                * The lwjglx wheel is scaled to the LWJGL2 convention (120 per notch); only the direction matters here - one step per notch.
                */
               if (wheel > 0) {
                  controller.increaseSpeed();
               } else {
                  controller.decreaseSpeed();
               }
            }
         }
      }
   }
}
