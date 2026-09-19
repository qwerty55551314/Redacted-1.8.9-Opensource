package com.replaymod.core.mixin;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.FutureTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.Timer;

public interface MinecraftAccessor {
   Timer getTimer();

   void setTimer(Timer var1);

   Queue<FutureTask<?>> getScheduledTasks();

   CrashReport getCrashReporter();

   List<IResourcePack> getDefaultResourcePacks();
}
