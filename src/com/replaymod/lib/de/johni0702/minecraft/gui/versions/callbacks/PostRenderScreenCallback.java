package com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;

public interface PostRenderScreenCallback {
   Event<PostRenderScreenCallback> EVENT = Event.create(listeners -> (stack, partialTicks) -> {
      for (PostRenderScreenCallback listener : listeners) {
         listener.postRenderScreen(stack, partialTicks);
      }
   });

   void postRenderScreen(MatrixStack var1, float var2);
}
