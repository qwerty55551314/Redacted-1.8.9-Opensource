package com.replaymod.core.gui;

import com.replaymod.core.ReplayMod;
import net.minecraft.client.gui.GuiScreen;

public class GuiFactory {
   public static class ConfigGuiWrapper extends GuiScreen {
      private final GuiScreen parent;

      public ConfigGuiWrapper(GuiScreen parent) {
         this.parent = parent;
      }

      public void initGui() {
         new GuiReplaySettings(this.parent, ReplayMod.instance.getSettingsRegistry()).display();
      }
   }
}
