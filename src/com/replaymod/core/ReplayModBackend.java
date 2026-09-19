package com.replaymod.core;

import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.MCVer;
import java.io.File;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;

public class ReplayModBackend {
   private final ReplayMod mod = new ReplayMod(this);
   @Deprecated
   public static ReplayModConfig config;

   public void init() {
      config = new ReplayModConfig(new File(Minecraft.getMinecraft().mcDataDir, "config/replaymod.cfg"));
      config.load();
      SettingsRegistry settingsRegistry = this.mod.getSettingsRegistry();
      settingsRegistry.backend.setConfiguration(config);
      settingsRegistry.save();
      this.mod.initModules();
   }

   public String getVersion() {
      return "2.6.27";
   }

   public String getMinecraftVersion() {
      return "1.8.9";
   }

   public boolean isModLoaded(String id) {
      return false;
   }

   static {
      List<IResourcePack> defaultResourcePacks = ((MinecraftAccessor)MCVer.getMinecraft()).getDefaultResourcePacks();
      if (ReplayMod.jGuiResourcePack != null) {
         defaultResourcePacks.add(ReplayMod.jGuiResourcePack);
      }
   }
}
