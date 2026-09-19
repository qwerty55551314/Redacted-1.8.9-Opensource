package com.replaymod.render.hooks;

import net.minecraft.client.renderer.RenderGlobal;

public interface IForceChunkLoading {
   void replayModRender_setHook(ForceChunkLoadingHook var1);

   static IForceChunkLoading from(RenderGlobal worldRenderer) {
      return (IForceChunkLoading)worldRenderer;
   }
}
