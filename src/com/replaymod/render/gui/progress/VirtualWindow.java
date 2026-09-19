package com.replaymod.render.gui.progress;

import com.replaymod.core.versions.Window;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.render.hooks.FramebufferDelegateHolder;
import com.replaymod.render.hooks.WindowDelegateHolder;
import com.replaymod.render.mixin.MainWindowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VirtualWindow implements Closeable {
   private static final Logger LOGGER = LogManager.getLogger();
   private final Minecraft mc;
   private final Window window;
   private final MainWindowAccessor acc;
   private final Framebuffer guiFramebuffer;
   private boolean isBound;
   private int framebufferWidth;
   private int framebufferHeight;
   private int gameWidth;
   private int gameHeight;

   public VirtualWindow(Minecraft mc) {
      this.mc = mc;
      this.window = new Window(mc);
      this.acc = this.window;
      this.framebufferWidth = this.acc.getFramebufferWidth();
      this.framebufferHeight = this.acc.getFramebufferHeight();
      this.guiFramebuffer = new Framebuffer(this.framebufferWidth, this.framebufferHeight, true);
      WindowDelegateHolder.get(mc).setWindowDelegate(this);
   }

   @Override
   public void close() {
      this.guiFramebuffer.deleteFramebuffer();
      WindowDelegateHolder.get(this.mc).setWindowDelegate(null);
   }

   public void bind() {
      this.gameWidth = this.acc.getFramebufferWidth();
      this.gameHeight = this.acc.getFramebufferHeight();
      this.acc.setFramebufferWidth(this.framebufferWidth);
      this.acc.setFramebufferHeight(this.framebufferHeight);
      this.applyScaleFactor();
      this.isBound = true;
   }

   public void unbind() {
      this.acc.setFramebufferWidth(this.gameWidth);
      this.acc.setFramebufferHeight(this.gameHeight);
      this.applyScaleFactor();
      this.isBound = false;
   }

   public void beginWrite() {
      FramebufferDelegateHolder.get(this.mc).setFramebufferDelegate(this.guiFramebuffer);
      this.guiFramebuffer.bindFramebuffer(true);
   }

   public void endWrite() {
      this.guiFramebuffer.unbindFramebuffer();
      FramebufferDelegateHolder.get(this.mc).setFramebufferDelegate(null);
   }

   public void flip() {
      this.guiFramebuffer.framebufferRender(this.framebufferWidth, this.framebufferHeight);
      this.mc.updateDisplay();
   }

   public void onResolutionChanged(int newWidth, int newHeight) {
      if (newWidth != 0 && newHeight != 0) {
         if (this.framebufferWidth != newWidth || this.framebufferHeight != newHeight) {
            this.framebufferWidth = newWidth;
            this.framebufferHeight = newHeight;
            this.guiFramebuffer.createBindFramebuffer(newWidth, newHeight);
            this.applyScaleFactor();
            if (this.mc.currentScreen != null) {
               this.mc.currentScreen.onResize(this.mc, this.window.getScaledWidth(), this.window.getScaledHeight());
            }
         }
      }
   }

   private void applyScaleFactor() {
   }

   public int getFramebufferWidth() {
      return this.framebufferWidth;
   }

   public int getFramebufferHeight() {
      return this.framebufferHeight;
   }

   public boolean isBound() {
      return this.isBound;
   }
}
