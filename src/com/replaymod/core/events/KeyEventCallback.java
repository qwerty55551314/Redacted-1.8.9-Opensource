package com.replaymod.core.events;

import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;

public interface KeyEventCallback {
   Event<KeyEventCallback> EVENT = Event.create(listeners -> (keyInput, action) -> {
      for (KeyEventCallback listener : listeners) {
         if (listener.onKeyEvent(keyInput, action)) {
            return true;
         }
      }

      return false;
   });
   int ACTION_RELEASE = 0;
   int ACTION_PRESS = 1;

   boolean onKeyEvent(KeyInput var1, int var2);
}
