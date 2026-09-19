package com.replaymod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SettingsRegistryBackend {
   private final Map<SettingsRegistry.SettingKey<?>, Object> settings;
   private static final Object NULL_OBJECT = new Object();
   private ReplayModConfig configuration;

   SettingsRegistryBackend(Map<SettingsRegistry.SettingKey<?>, Object> settings) {
      this.settings = settings;
   }

   public void register() {
   }

   public void setConfiguration(ReplayModConfig configuration) {
      this.configuration = configuration;

      for (SettingsRegistry.SettingKey<?> key : new ArrayList<>(this.settings.keySet())) {
         this.register(key);
      }
   }

   public void register(SettingsRegistry.SettingKey<?> key) {
      Object value;
      if (this.configuration != null) {
         if (key.getDefault() instanceof Boolean) {
            value = this.configuration.get(key.getCategory(), key.getKey(), (Boolean)key.getDefault()).getBoolean();
         } else if (key.getDefault() instanceof Integer) {
            value = this.configuration.get(key.getCategory(), key.getKey(), (Integer)key.getDefault()).getInt();
         } else if (key.getDefault() instanceof Double) {
            value = this.configuration.get(key.getCategory(), key.getKey(), (Double)key.getDefault()).getDouble();
         } else {
            if (!(key.getDefault() instanceof String)) {
               throw new IllegalArgumentException("Default type " + key.getDefault().getClass() + " not supported.");
            }

            value = this.configuration.get(key.getCategory(), key.getKey(), (String)key.getDefault()).getString();
         }
      } else {
         value = NULL_OBJECT;
      }

      this.settings.put(key, value);
   }

   public <T> void update(SettingsRegistry.SettingKey<T> key, T value) {
      if (this.configuration == null) return;
      if (key.getDefault() instanceof Boolean) {
         this.configuration.get(key.getCategory(), key.getKey(), (Boolean)key.getDefault()).set((Boolean)value);
      } else if (key.getDefault() instanceof Integer) {
         this.configuration.get(key.getCategory(), key.getKey(), (Integer)key.getDefault()).set((Integer)value);
      } else if (key.getDefault() instanceof Double) {
         this.configuration.get(key.getCategory(), key.getKey(), (Double)key.getDefault()).set((Double)value);
      } else {
         if (!(key.getDefault() instanceof String)) {
            throw new IllegalArgumentException("Default type " + key.getDefault().getClass() + " not supported.");
         }

         this.configuration.get(key.getCategory(), key.getKey(), (String)key.getDefault()).set((String)value);
      }
   }

   public void save() {
      if (this.configuration != null) {
         this.configuration.save();
      }
   }
}
