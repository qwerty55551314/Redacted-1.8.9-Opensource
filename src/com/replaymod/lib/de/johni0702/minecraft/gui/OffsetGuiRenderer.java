package com.replaymod.lib.de.johni0702.minecraft.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.NonNull;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.ResourceLocation;

public class OffsetGuiRenderer implements GuiRenderer {
   @NonNull
   private final GuiRenderer renderer;
   @NonNull
   private final ReadablePoint position;
   @NonNull
   private final ReadableDimension size;
   private final boolean strict;

   public OffsetGuiRenderer(OffsetGuiRenderer renderer) {
      this(renderer.renderer, renderer.position, renderer.size, true);
   }

   public OffsetGuiRenderer(GuiRenderer renderer, ReadablePoint position, ReadableDimension size) {
      this(renderer, position, size, true);
   }

   public OffsetGuiRenderer(GuiRenderer renderer, ReadablePoint position, ReadableDimension size, boolean strict) {
      this.renderer = renderer;
      this.position = position;
      this.size = size;
      this.strict = strict;
   }

   @Override
   public ReadablePoint getOpenGlOffset() {
      ReadablePoint parentOffset = this.renderer.getOpenGlOffset();
      return new Point(parentOffset.getX() + this.position.getX(), parentOffset.getY() + this.position.getY());
   }

   @Override
   public MatrixStack getMatrixStack() {
      return this.renderer.getMatrixStack();
   }

   @Override
   public ReadableDimension getSize() {
      return this.size;
   }

   @Override
   public void pushScissor(int x, int y, int width, int height) {
      if (!this.strict) {
         this.renderer.pushScissor(x + this.position.getX(), y + this.position.getY(), width, height);
      } else {
         int x2 = x + width;
         int y2 = y + height;
         x = Math.max(0, x + this.position.getX());
         y = Math.max(0, y + this.position.getY());
         x2 = Math.min(x2, this.size.getWidth()) + this.position.getX();
         y2 = Math.min(y2, this.size.getHeight()) + this.position.getY();
         x2 = Math.max(x2, x);
         y2 = Math.max(y2, y);
         this.renderer.pushScissor(x, y, x2 - x, y2 - y);
      }
   }

   @Override
   public void popScissor() {
      this.renderer.popScissor();
   }

   public void startUsing() {
      this.pushScissor(0, 0, this.size.getWidth(), this.size.getHeight());
   }

   public void stopUsing() {
      this.popScissor();
   }

   @Override
   public void bindTexture(ResourceLocation location) {
      this.renderer.bindTexture(location);
   }

   @Override
   public void bindTexture(AbstractTexture texture) {
      this.renderer.bindTexture(texture);
   }

   @Override
   public void drawTexturedRect(int x, int y, int u, int v, int width, int height) {
      this.renderer.drawTexturedRect(x + this.position.getX(), y + this.position.getY(), u, v, width, height);
   }

   @Override
   public void drawTexturedRect(int x, int y, int u, int v, int width, int height, int uWidth, int vHeight, int textureWidth, int textureHeight) {
      this.renderer.drawTexturedRect(x + this.position.getX(), y + this.position.getY(), u, v, width, height, uWidth, vHeight, textureWidth, textureHeight);
   }

   @Override
   public void drawRect(int x, int y, int width, int height, int color) {
      this.renderer.drawRect(x + this.position.getX(), y + this.position.getY(), width, height, color);
   }

   @Override
   public void drawRect(int x, int y, int width, int height, ReadableColor color) {
      this.renderer.drawRect(x + this.position.getX(), y + this.position.getY(), width, height, color);
   }

   @Override
   public void drawRect(int x, int y, int width, int height, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
      this.renderer.drawRect(x + this.position.getX(), y + this.position.getY(), width, height, topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
   }

   @Override
   public void drawRect(
      int x,
      int y,
      int width,
      int height,
      ReadableColor topLeftColor,
      ReadableColor topRightColor,
      ReadableColor bottomLeftColor,
      ReadableColor bottomRightColor
   ) {
      this.renderer.drawRect(x + this.position.getX(), y + this.position.getY(), width, height, topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
   }

   @Override
   public int drawString(int x, int y, int color, String text) {
      return this.renderer.drawString(x + this.position.getX(), y + this.position.getY(), color, text) - this.position.getX();
   }

   @Override
   public int drawString(int x, int y, ReadableColor color, String text) {
      return this.renderer.drawString(x + this.position.getX(), y + this.position.getY(), color, text) - this.position.getX();
   }

   @Override
   public int drawCenteredString(int x, int y, int color, String text) {
      return this.renderer.drawCenteredString(x + this.position.getX(), y + this.position.getY(), color, text) - this.position.getX();
   }

   @Override
   public int drawCenteredString(int x, int y, ReadableColor color, String text) {
      return this.renderer.drawCenteredString(x + this.position.getX(), y + this.position.getY(), color, text) - this.position.getX();
   }

   @Override
   public int drawString(int x, int y, int color, String text, boolean shadow) {
      return this.renderer.drawString(x + this.position.getX(), y + this.position.getY(), color, text, shadow) - this.position.getX();
   }

   @Override
   public int drawString(int x, int y, ReadableColor color, String text, boolean shadow) {
      return this.renderer.drawString(x + this.position.getX(), y + this.position.getY(), color, text, shadow) - this.position.getX();
   }

   @Override
   public int drawCenteredString(int x, int y, int color, String text, boolean shadow) {
      return this.renderer.drawCenteredString(x + this.position.getX(), y + this.position.getY(), color, text, shadow) - this.position.getX();
   }

   @Override
   public int drawCenteredString(int x, int y, ReadableColor color, String text, boolean shadow) {
      return this.renderer.drawCenteredString(x + this.position.getX(), y + this.position.getY(), color, text, shadow) - this.position.getX();
   }

   @Override
   public void invertColors(int right, int bottom, int left, int top) {
      int x = this.position.getX();
      int y = this.position.getY();
      this.renderer.invertColors(right + x, bottom + y, left + x, top + y);
   }
}
