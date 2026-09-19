package com.replaymod.render.hooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

public interface FramebufferDelegateHolder {
   void setFramebufferDelegate(Framebuffer var1);

   static FramebufferDelegateHolder get(Minecraft mc) {
      return (FramebufferDelegateHolder)mc;
   }
}
