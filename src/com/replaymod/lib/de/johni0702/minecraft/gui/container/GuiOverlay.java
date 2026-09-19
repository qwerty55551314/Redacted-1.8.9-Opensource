package com.replaymod.lib.de.johni0702.minecraft.gui.container;

public class GuiOverlay extends AbstractGuiOverlay<GuiOverlay> {
   public static AbstractGuiOverlay from(net.minecraft.client.gui.GuiScreen minecraft) {
      return !(minecraft instanceof AbstractGuiOverlay.UserInputGuiScreen) ? null : ((AbstractGuiOverlay.UserInputGuiScreen)minecraft).getOverlay();
   }

   protected GuiOverlay getThis() {
      return this;
   }
}
