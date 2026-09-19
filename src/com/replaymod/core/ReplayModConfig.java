package com.replaymod.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Minimal replacement for Forge's Configuration. Stores settings as a flat
 * properties file (category.key = value) in the minecraft data directory.
 */
class ReplayModConfig {
   private final File file;
   private final Properties props = new Properties();
   private final Map<String, Property> properties = new HashMap<>();

   ReplayModConfig(File file) {
      this.file = file;
   }

   void load() {
      if (this.file.exists()) {
         try (FileInputStream in = new FileInputStream(this.file)) {
            this.props.load(in);
         } catch (IOException e) {
            System.err.println("[ReplayMod] Failed to load config: " + e.getMessage());
         }
      }
   }

   Property get(String category, String key, boolean defaultValue) {
      return this.get(category, key, String.valueOf(defaultValue), Property.Type.BOOLEAN);
   }

   Property get(String category, String key, int defaultValue) {
      return this.get(category, key, String.valueOf(defaultValue), Property.Type.INTEGER);
   }

   Property get(String category, String key, double defaultValue) {
      return this.get(category, key, String.valueOf(defaultValue), Property.Type.DOUBLE);
   }

   Property get(String category, String key, String defaultValue) {
      return this.get(category, key, defaultValue, Property.Type.STRING);
   }

   private Property get(String category, String key, String defaultValue, Property.Type type) {
      String fullKey = category + "." + key;
      Property property = this.properties.get(fullKey);
      if (property == null) {
         String value = this.props.getProperty(fullKey, defaultValue);
         property = new Property(value, type);
         this.properties.put(fullKey, property);
      }
      return property;
   }

   void save() {
      for (Map.Entry<String, Property> entry : this.properties.entrySet()) {
         this.props.setProperty(entry.getKey(), entry.getValue().value);
      }
      try {
         if (this.file.getParentFile() != null) this.file.getParentFile().mkdirs();
         try (FileOutputStream out = new FileOutputStream(this.file)) {
            this.props.store(out, "ReplayMod configuration");
         }
      } catch (IOException e) {
         System.err.println("[ReplayMod] Failed to save config: " + e.getMessage());
      }
   }

   static class Property {
      String value;
      final Type type;

      Property(String value, Type type) {
         this.value = value;
         this.type = type;
      }

      boolean getBoolean() { return Boolean.parseBoolean(this.value); }
      int getInt() { return Integer.parseInt(this.value); }
      double getDouble() { return Double.parseDouble(this.value); }
      String getString() { return this.value; }

      void set(boolean value) { this.value = String.valueOf(value); }
      void set(int value) { this.value = String.valueOf(value); }
      void set(double value) { this.value = String.valueOf(value); }
      void set(String value) { this.value = value; }

      enum Type { BOOLEAN, INTEGER, DOUBLE, STRING }
   }
}
