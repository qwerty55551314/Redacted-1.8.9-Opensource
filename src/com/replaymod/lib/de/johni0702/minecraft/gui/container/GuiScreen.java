package com.replaymod.lib.de.johni0702.minecraft.gui.container;

public class GuiScreen extends AbstractGuiScreen<GuiScreen> {
   public static AbstractGuiScreen from(net.minecraft.client.gui.GuiScreen minecraft) {
      return !(minecraft instanceof AbstractGuiScreen.MinecraftGuiScreen) ? null : ((AbstractGuiScreen.MinecraftGuiScreen)minecraft).getWrapper();
   }

   public static GuiScreen wrap(net.minecraft.client.gui.GuiScreen minecraft) {
      return new GuiScreen() {
         @Override
         public net.minecraft.client.gui.GuiScreen toMinecraft() {
            return minecraft;
         }
      };
   }

   protected GuiScreen getThis() {
      return this;
   }
}
