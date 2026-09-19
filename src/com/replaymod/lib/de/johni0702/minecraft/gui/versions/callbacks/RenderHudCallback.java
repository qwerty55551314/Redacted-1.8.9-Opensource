package com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;

public interface RenderHudCallback {
   Event<RenderHudCallback> EVENT = Event.create(listeners -> (stack, partialTicks) -> {
      for (RenderHudCallback listener : listeners) {
         listener.renderHud(stack, partialTicks);
      }
   });

   void renderHud(MatrixStack var1, float var2);
}
