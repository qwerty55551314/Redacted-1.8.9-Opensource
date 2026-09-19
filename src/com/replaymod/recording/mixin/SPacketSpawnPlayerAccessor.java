package com.replaymod.recording.mixin;

import net.minecraft.entity.DataWatcher;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;

public interface SPacketSpawnPlayerAccessor {
   DataWatcher getDataManager();

   void setDataManager(DataWatcher var1);
}
