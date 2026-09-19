package com.replaymod.lib.de.johni0702.minecraft.gui.function;

import net.minecraft.client.gui.GuiScreen;

public interface InputWithModifiers {
   int _CTRL_MOD = 1;
   int _SHIFT_MOD = 2;
   int _ALT_MOD = 4;

   int modifiers();

   default boolean hasCtrl() {
      return (this.modifiers() & 1) != 0;
   }

   default boolean hasShift() {
      return (this.modifiers() & 2) != 0;
   }

   default boolean hasAlt() {
      return (this.modifiers() & 4) != 0;
   }

   static int currentModifiers() {
      int ctrl = GuiScreen.isCtrlKeyDown() ? 1 : 0;
      int shift = GuiScreen.isShiftKeyDown() ? 2 : 0;
      int alt = GuiScreen.isAltKeyDown() ? 4 : 0;
      return ctrl | shift | alt;
   }
}
