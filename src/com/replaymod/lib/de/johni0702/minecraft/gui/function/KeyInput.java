package com.replaymod.lib.de.johni0702.minecraft.gui.function;

public class KeyInput implements InputWithModifiers {
   public int key;
   public int scancode;
   public int modifiers;

   public KeyInput(int key, int scancode, int modifiers) {
      this.key = key;
      this.scancode = scancode;
      this.modifiers = modifiers;
   }

   public KeyInput(int key) {
      this(key, key, InputWithModifiers.currentModifiers());
   }

   @Override
   public int modifiers() {
      return this.modifiers;
   }

   public boolean isEscape() {
      return this.key == 1;
   }
}
