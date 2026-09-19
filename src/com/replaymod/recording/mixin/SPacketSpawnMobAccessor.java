package com.replaymod.recording.mixin;

import net.minecraft.entity.DataWatcher;
import net.minecraft.network.play.server.S0FPacketSpawnMob;

public interface SPacketSpawnMobAccessor {
   DataWatcher getDataManager();

   void setDataManager(DataWatcher var1);
}
