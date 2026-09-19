package com.replaymod.lib.de.johni0702.minecraft.gui.function;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritablePoint;

public class Click implements InputWithModifiers, ReadablePoint {
   public int x;
   public int y;
   public int button;
   public int modifiers;

   public Click(int x, int y, int button, int modifiers) {
      this.x = x;
      this.y = y;
      this.button = button;
      this.modifiers = modifiers;
   }

   public Click(double x, double y, int button) {
      this((int)Math.round(x), (int)Math.round(y), button, InputWithModifiers.currentModifiers());
   }

   @Override
   public int modifiers() {
      return this.modifiers;
   }

   @Override
   public int getX() {
      return this.x;
   }

   @Override
   public int getY() {
      return this.y;
   }

   @Override
   public void getLocation(WritablePoint writablePoint) {
      writablePoint.setLocation(this.x, this.y);
   }
}
