package com.replaymod.core.versions;

import com.replaymod.render.mixin.MainWindowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjglx.opengl.Display;

public class Window implements MainWindowAccessor {
   private final Minecraft mc;

   public Window(Minecraft mc) {
      this.mc = mc;
   }

   @Override
   public int getFramebufferWidth() {
      return this.mc.displayWidth;
   }

   @Override
   public void setFramebufferWidth(int value) {
      this.mc.displayWidth = value;
   }

   @Override
   public int getFramebufferHeight() {
      return this.mc.displayHeight;
   }

   @Override
   public void setFramebufferHeight(int value) {
      this.mc.displayHeight = value;
   }

   public long getHandle() {
      return 0L;
   }

   public int getWidth() {
      return Display.getWidth();
   }

   public int getHeight() {
      return Display.getHeight();
   }

   private ScaledResolution scaledResolution() {
      return new ScaledResolution(this.mc);
   }

   public int getScaledWidth() {
      return this.scaledResolution().getScaledWidth();
   }

   public int getScaledHeight() {
      return this.scaledResolution().getScaledHeight();
   }
}
