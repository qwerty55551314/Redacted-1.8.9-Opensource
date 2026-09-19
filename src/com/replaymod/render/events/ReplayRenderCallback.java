package com.replaymod.render.events;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import com.replaymod.render.rendering.VideoRenderer;

public interface ReplayRenderCallback {
   interface Post {
      Event<ReplayRenderCallback.Post> EVENT = Event.create(listeners -> renderer -> {
         for (ReplayRenderCallback.Post listener : listeners) {
            listener.afterRendering(renderer);
         }
      });

      void afterRendering(VideoRenderer var1);
   }

   interface Pre {
      Event<ReplayRenderCallback.Pre> EVENT = Event.create(listeners -> renderer -> {
         for (ReplayRenderCallback.Pre listener : listeners) {
            listener.beforeRendering(renderer);
         }
      });

      void beforeRendering(VideoRenderer var1);
   }
}
