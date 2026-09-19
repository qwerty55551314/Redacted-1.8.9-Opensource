package com.replaymod.replay.camera;

import com.replaymod.core.versions.MCVer;
import com.replaymod.replay.ReplayModReplay;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import org.lwjglx.input.Mouse;

public class SpectatorCameraController implements CameraController {
   private final CameraEntity camera;

   public SpectatorCameraController(CameraEntity camera) {
      this.camera = camera;
   }

   @Override
   public void update(float partialTicksPassed) {
      Minecraft mc = MCVer.getMinecraft();
      if (mc.gameSettings.keyBindSneak.isPressed()) {
         ReplayModReplay.instance.getReplayHandler().spectateCamera();
      }

      for (KeyBinding binding : Arrays.asList(
         mc.gameSettings.keyBindAttack,
         mc.gameSettings.keyBindUseItem,
         mc.gameSettings.keyBindJump,
         mc.gameSettings.keyBindSneak,
         mc.gameSettings.keyBindForward,
         mc.gameSettings.keyBindBack,
         mc.gameSettings.keyBindLeft,
         mc.gameSettings.keyBindRight
      )) {
         while (binding.isPressed()) {
         }
      }

      Mouse.updateCursor();
      Entity view = mc.getRenderViewEntity();
      if (view != null && view != this.camera) {
         this.camera.setCameraPosRot(mc.getRenderViewEntity());
      }
   }

   @Override
   public void increaseSpeed() {
   }

   @Override
   public void decreaseSpeed() {
   }
}
