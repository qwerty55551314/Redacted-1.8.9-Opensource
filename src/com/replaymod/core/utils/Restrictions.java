package com.replaymod.core.utils;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

public class Restrictions {
   public static final String PLUGIN_CHANNEL = "Replay|Restrict";
   private boolean noXray;
   private boolean noNoclip;
   private boolean onlyFirstPerson;
   private boolean onlyRecordingPlayer;

   public String handle(S3FPacketCustomPayload packet) {
      PacketBuffer buffer = packet.getBufferData();
      if (buffer.isReadable()) {
         String name = buffer.readStringFromBuffer(64);
         boolean active = buffer.readBoolean();
         return name;
      } else {
         return null;
      }
   }

   public boolean isNoXray() {
      return this.noXray;
   }

   public boolean isNoNoclip() {
      return this.noNoclip;
   }

   public boolean isOnlyFirstPerson() {
      return this.onlyFirstPerson;
   }

   public boolean isOnlyRecordingPlayer() {
      return this.onlyRecordingPlayer;
   }
}
