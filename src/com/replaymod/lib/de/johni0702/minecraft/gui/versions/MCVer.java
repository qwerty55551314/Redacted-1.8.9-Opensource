package com.replaymod.lib.de.johni0702.minecraft.gui.versions;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Callable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class MCVer {
   private static final ArrayDeque<MCVer.ScissorBounds> scissorStateStack = new ArrayDeque<>();
   private static MCVer.ScissorBounds scissorState = MCVer.ScissorBounds.DISABLED;

   public static Minecraft getMinecraft() {
      return Minecraft.getMinecraft();
   }

   public static void pushScissorState() {
      scissorStateStack.push(scissorState);
   }

   public static void popScissorState() {
      setScissorBounds(scissorStateStack.pop());
   }

   public static void setScissorBounds(int x, int y, int width, int height) {
      setScissorBounds(new MCVer.ScissorBounds(x, y, width, height));
   }

   public static void setScissorDisabled() {
      setScissorBounds(MCVer.ScissorBounds.DISABLED);
   }

   private static void setScissorBounds(MCVer.ScissorBounds newState) {
      MCVer.ScissorBounds oldState = scissorState;
      if (!Objects.equals(oldState, newState)) {
         scissorState = newState;
         boolean isEnabled = newState != MCVer.ScissorBounds.DISABLED;
         boolean wasEnabled = oldState != MCVer.ScissorBounds.DISABLED;
         if (isEnabled) {
            if (!wasEnabled) {
               GL11.glEnable(3089);
            }

            GL11.glScissor(scissorState.x, scissorState.y, scissorState.width, scissorState.height);
         } else {
            GL11.glDisable(3089);
         }
      }
   }

   public static ScaledResolution newScaledResolution(Minecraft mc) {
      return new ScaledResolution(mc);
   }

   public static void addDetail(CrashReportCategory category, String name, Callable<String> callable) {
      category.addCrashSectionCallable(name, callable);
   }

   public static void bindTexture(ResourceLocation identifier) {
      getMinecraft().getTextureManager().bindTexture(identifier);
   }

   public static FontRenderer getFontRenderer() {
      return getMinecraft().fontRendererObj;
   }

   public static void setClipboardString(String text) {
      GuiScreen.setClipboardString(text);
   }

   public static String getClipboardString() {
      return GuiScreen.getClipboardString();
   }

   public static IChatComponent literalText(String str) {
      return new ChatComponentText(str);
   }

   public static ResourceLocation identifier(String id) {
      return new ResourceLocation(id);
   }

   public static ResourceLocation identifier(String namespace, String path) {
      return new ResourceLocation(namespace, path);
   }

   public static class ScissorBounds {
      private static final MCVer.ScissorBounds DISABLED = new MCVer.ScissorBounds(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
      public final int x;
      public final int y;
      public final int width;
      public final int height;

      public ScissorBounds(int x, int y, int width, int height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            MCVer.ScissorBounds that = (MCVer.ScissorBounds)o;
            return this.x == that.x && this.y == that.y && this.width == that.width && this.height == that.height;
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.x, this.y, this.width, this.height);
      }
   }
}
