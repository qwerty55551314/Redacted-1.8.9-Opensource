package com.replaymod.core.mixin;

import net.minecraft.client.settings.KeyBinding;

public interface KeyBindingAccessor {
   int getPressTime();

   void setPressTime(int var1);
}
