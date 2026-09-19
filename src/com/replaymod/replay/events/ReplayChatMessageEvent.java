package com.replaymod.replay.events;

import com.replaymod.replay.camera.CameraEntity;

public class ReplayChatMessageEvent {
   private boolean canceled;
   private final CameraEntity cameraEntity;

   public ReplayChatMessageEvent(CameraEntity cameraEntity) {
      this.cameraEntity = cameraEntity;
   }

   public CameraEntity getCameraEntity() {
      return this.cameraEntity;
   }

   public boolean isCanceled() {
      return this.canceled;
   }

   public void setCanceled(boolean canceled) {
      this.canceled = canceled;
   }
}
