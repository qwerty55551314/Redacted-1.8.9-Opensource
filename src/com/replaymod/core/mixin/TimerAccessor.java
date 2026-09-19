package com.replaymod.core.mixin;

import net.minecraft.util.Timer;

public interface TimerAccessor {
   float getTimerSpeed();

   void setTimerSpeed(float var1);
}
