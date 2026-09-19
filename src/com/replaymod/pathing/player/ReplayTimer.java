package com.replaymod.pathing.player;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import net.minecraft.util.Timer;

public class ReplayTimer extends Timer {
   public ReplayTimer() {
      super(0.0F);
   }

   public void updateTimer() {
      ReplayTimer.UpdatedCallback.EVENT.invoker().onUpdate();
   }

   public interface UpdatedCallback {
      Event<ReplayTimer.UpdatedCallback> EVENT = Event.create(listeners -> () -> {
         for (ReplayTimer.UpdatedCallback listener : listeners) {
            listener.onUpdate();
         }
      });

      void onUpdate();
   }
}
