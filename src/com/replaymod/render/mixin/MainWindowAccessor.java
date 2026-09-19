package com.replaymod.render.mixin;

import net.minecraft.client.Minecraft;

public interface MainWindowAccessor {
   int getFramebufferWidth();

   void setFramebufferWidth(int var1);

   int getFramebufferHeight();

   void setFramebufferHeight(int var1);
}
