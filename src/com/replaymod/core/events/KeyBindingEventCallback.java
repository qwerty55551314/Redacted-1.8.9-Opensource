package com.replaymod.core.events;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;

public interface KeyBindingEventCallback {
   Event<KeyBindingEventCallback> EVENT = Event.create(listeners -> () -> {
      for (KeyBindingEventCallback listener : listeners) {
         listener.onKeybindingEvent();
      }
   });

   void onKeybindingEvent();
}
