package com.replaymod.render.mixin;

import net.minecraft.client.renderer.EntityRenderer;

public interface GameRendererAccessor {
   boolean getRenderHand();

   void setRenderHand(boolean var1);
}
