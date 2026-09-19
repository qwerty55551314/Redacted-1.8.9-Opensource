package com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import net.minecraft.client.gui.GuiScreen;

public interface OpenGuiScreenCallback {
   Event<OpenGuiScreenCallback> EVENT = Event.create(listeners -> screen -> {
      for (OpenGuiScreenCallback listener : listeners) {
         listener.openGuiScreen(screen);
      }
   });

   void openGuiScreen(GuiScreen var1);
}
