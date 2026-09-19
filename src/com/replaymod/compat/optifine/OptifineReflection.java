package com.replaymod.compat.optifine;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.client.settings.GameSettings;

public class OptifineReflection {
   public static Field gameSettings_ofFastRender;

   public static void reloadLang() {
      try {
         Class<?> langClass;
         try {
            langClass = Class.forName("Lang");
         } catch (ClassNotFoundException ignore) {
            langClass = Class.forName("net.optifine.Lang");
         }

         langClass.getDeclaredMethod("resourcesReloaded").invoke(null);
      } catch (ClassNotFoundException var3) {
      } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
         e.printStackTrace();
      }
   }

   static {
      try {
         Class.forName("Config");
         gameSettings_ofFastRender = GameSettings.class.getDeclaredField("ofFastRender");
         gameSettings_ofFastRender.setAccessible(true);
      } catch (ClassNotFoundException var1) {
      } catch (NoSuchFieldException e) {
         e.printStackTrace();
      }
   }
}
