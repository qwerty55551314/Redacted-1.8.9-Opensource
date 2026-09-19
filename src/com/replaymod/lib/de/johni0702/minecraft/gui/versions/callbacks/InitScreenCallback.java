package com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import java.util.Collection;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public interface InitScreenCallback {
   Event<InitScreenCallback> EVENT = Event.create(listeners -> (screen, buttons) -> {
      for (InitScreenCallback listener : listeners) {
         listener.initScreen(screen, buttons);
      }
   });

   void initScreen(GuiScreen var1, Collection<GuiButton> var2);

   interface Pre {
      Event<InitScreenCallback.Pre> EVENT = Event.create(listeners -> screen -> {
         for (InitScreenCallback.Pre listener : listeners) {
            listener.preInitScreen(screen);
         }
      });

      void preInitScreen(GuiScreen var1);
   }
}
