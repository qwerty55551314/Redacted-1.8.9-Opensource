package com.replaymod.replay;

import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.core.versions.Window;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.Image;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ScreenShotHelper;
import org.apache.commons.io.FileUtils;

public class NoGuiScreenshot {
   private final Image image;
   private final int width;
   private final int height;

   private NoGuiScreenshot(Image image, int width, int height) {
      this.image = image;
      this.width = width;
      this.height = height;
   }

   public Image getImage() {
      return this.image;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public static ListenableFuture<NoGuiScreenshot> take(Minecraft mc, int width, int height) {
      final SettableFuture<NoGuiScreenshot> future = SettableFuture.create();
      Runnable runnable = new Runnable() {
         @Override
         public void run() {
            if (!future.isCancelled()) {
               int frameWidth = new Window(mc).getFramebufferWidth();
               int frameHeight = new Window(mc).getFramebufferHeight();
               boolean guiHidden = mc.gameSettings.hideGUI;

               label77: {
                  try {
                     mc.gameSettings.hideGUI = true;
                     MCVer.pushMatrix();
                     GlStateManager.clear(16640);
                     mc.getFramebuffer().bindFramebuffer(true);
                     GlStateManager.enableTexture2D();
                     float tickDelta = ((MinecraftAccessor)mc).getTimer().renderPartialTicks;
                     mc.entityRenderer.updateCameraAndRender(tickDelta, System.nanoTime());
                     mc.getFramebuffer().unbindFramebuffer();
                     MCVer.popMatrix();
                     MCVer.pushMatrix();
                     mc.getFramebuffer().framebufferRender(frameWidth, frameHeight);
                     MCVer.popMatrix();
                     break label77;
                  } catch (Throwable t) {
                     future.setException(t);
                  } finally {
                     mc.gameSettings.hideGUI = guiHidden;
                  }

                  return;
               }

               try {
                  File tmpFolder = Files.createTempDir();

                  Image image;
                  try {
                     ScreenShotHelper.saveScreenshot(tmpFolder, "tmp", frameWidth, frameHeight, mc.getFramebuffer());
                     File screenshotFile = new File(tmpFolder, "screenshots/tmp");
                     image = Image.read(screenshotFile.toPath());
                  } finally {
                     FileUtils.deleteQuietly(tmpFolder);
                  }

                  int var27 = image.getWidth();
                  int imageHeight = image.getHeight();
                  float scaleFactor = Math.max((float)width / var27, (float)height / imageHeight);
                  int croppedWidth = Math.min(Math.max(0, (int)(width / scaleFactor)), var27);
                  int croppedHeight = Math.min(Math.max(0, (int)(height / scaleFactor)), imageHeight);
                  int offsetX = (var27 - croppedWidth) / 2;
                  int offsetY = (imageHeight - croppedHeight) / 2;
                  image = image.scaledSubRect(offsetX, offsetY, croppedWidth, croppedHeight, width, height);
                  future.set(new NoGuiScreenshot(image, width, height));
               } catch (Throwable t) {
                  future.setException(t);
               }
            }
         }
      };
      ReplayMod.instance.runLater(runnable);
      return future;
   }
}
