package com.replaymod.core.events;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;

public interface PostRenderWorldCallback {
   Event<PostRenderWorldCallback> EVENT = Event.create(listeners -> matrixStack -> {
      for (PostRenderWorldCallback listener : listeners) {
         listener.postRenderWorld(matrixStack);
      }
   });

   void postRenderWorld(MatrixStack var1);
}
