package com.replaymod.lib.de.johni0702.minecraft.gui.utils;

import java.util.ArrayList;
import java.util.List;

public class EventRegistrations {
   private List<EventRegistration<?>> registrations = new ArrayList<>();

   public <T> EventRegistrations on(EventRegistration<T> registration) {
      this.registrations.add(registration);
      return this;
   }

   public <T> EventRegistrations on(Event<T> event, T listener) {
      return this.on(EventRegistration.create(event, listener));
   }

   public void register() {
      for (EventRegistration<?> registration : this.registrations) {
         registration.register();
      }
   }

   public void unregister() {
      for (EventRegistration<?> registration : this.registrations) {
         registration.unregister();
      }
   }
}
