package com.replaymod.lib.de.johni0702.minecraft.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.NonNull;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public class MinecraftGuiRenderer implements GuiRenderer {
   private final Minecraft mc = MCVer.getMinecraft();
   private final Gui gui = new Gui() {};
   private final MatrixStack matrixStack;
   @NonNull
   private final int scaledWidth = MCVer.newScaledResolution(this.mc).getScaledWidth();
   private final int scaledHeight = MCVer.newScaledResolution(this.mc).getScaledHeight();
   private final double scaleFactor = MCVer.newScaledResolution(this.mc).getScaleFactor();
   private ResourceLocation boundTexture;
   private int boundTextureGpu;

   public MinecraftGuiRenderer(MatrixStack matrixStack) {
      this.matrixStack = matrixStack;
   }

   @Override
   public ReadablePoint getOpenGlOffset() {
      return new Point(0, 0);
   }

   @Override
   public MatrixStack getMatrixStack() {
      return this.matrixStack;
   }

   @Override
   public ReadableDimension getSize() {
      return new ReadableDimension() {
         @Override
         public int getWidth() {
            return MinecraftGuiRenderer.this.scaledWidth;
         }

         @Override
         public int getHeight() {
            return MinecraftGuiRenderer.this.scaledHeight;
         }

         @Override
         public void getSize(WritableDimension dest) {
            dest.setSize(this.getWidth(), this.getHeight());
         }
      };
   }

   @Override
   public void pushScissor(int x, int y, int width, int height) {
      y = this.scaledHeight - y - height;
      int f = (int)this.scaleFactor;
      MCVer.pushScissorState();
      MCVer.setScissorBounds(x * f, y * f, width * f, height * f);
   }

   @Override
   public void popScissor() {
      MCVer.popScissorState();
   }

   @Override
   public void bindTexture(ResourceLocation location) {
      this.boundTexture = location;
      this.boundTextureGpu = 0;
   }

   @Override
   public void bindTexture(AbstractTexture texture) {
      this.boundTexture = null;
      this.boundTextureGpu = texture.getGlTextureId();
   }

   @Override
   public void drawTexturedRect(int x, int y, int u, int v, int width, int height) {
      this.drawTexturedRect(x, y, u, v, width, height, width, height, 256, 256);
   }

   @Override
   public void drawTexturedRect(int x, int y, int u, int v, int width, int height, int uWidth, int vHeight, int textureWidth, int textureHeight) {
      this.color(1.0F, 1.0F, 1.0F);
      if (this.boundTexture != null) {
         MCVer.bindTexture(this.boundTexture);
      } else {
         GlStateManager.bindTexture(this.boundTextureGpu);
      }

      Gui.drawScaledCustomSizeModalRect(x, y, u, v, uWidth, vHeight, width, height, textureWidth, textureHeight);
   }

   @Override
   public void drawRect(int x, int y, int width, int height, int color) {
      Gui.drawRect(x, y, x + width, y + height, color);
      this.color(1.0F, 1.0F, 1.0F);
      GlStateManager.enableBlend();
   }

   @Override
   public void drawRect(int x, int y, int width, int height, ReadableColor color) {
      this.drawRect(x, y, width, height, this.color(color));
   }

   @Override
   public void drawRect(int x, int y, int width, int height, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
      this.drawRect(x, y, width, height, this.color(topLeftColor), this.color(topRightColor), this.color(bottomLeftColor), this.color(bottomRightColor));
   }

   @Override
   public void drawRect(int x, int y, int width, int height, ReadableColor tl, ReadableColor tr, ReadableColor bl, ReadableColor br) {
      this.drawRect(x, y, width, height, tl, tr, bl, br, false);
   }

   private void drawRect(int x, int y, int width, int height, ReadableColor tl, ReadableColor tr, ReadableColor bl, ReadableColor br, boolean highlight) {
      GlStateManager.disableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.disableAlpha();
      GlStateManager.shadeModel(7425);
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer vertexBuffer = tessellator.getWorldRenderer();
      vertexBuffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
      vertexBuffer.pos(x, y + height, 0.0).color(bl.getRed(), bl.getGreen(), bl.getBlue(), bl.getAlpha()).endVertex();
      vertexBuffer.pos(x + width, y + height, 0.0).color(br.getRed(), br.getGreen(), br.getBlue(), br.getAlpha()).endVertex();
      vertexBuffer.pos(x + width, y, 0.0).color(tr.getRed(), tr.getGreen(), tr.getBlue(), tr.getAlpha()).endVertex();
      vertexBuffer.pos(x, y, 0.0).color(tl.getRed(), tl.getGreen(), tl.getBlue(), tl.getAlpha()).endVertex();
      tessellator.draw();
      GlStateManager.shadeModel(7424);
      GlStateManager.enableAlpha();
      GlStateManager.enableTexture2D();
   }

   @Override
   public int drawString(int x, int y, int color, String text) {
      return this.drawString(x, y, color, text, false);
   }

   @Override
   public int drawString(int x, int y, ReadableColor color, String text) {
      return this.drawString(x, y, this.color(color), text);
   }

   @Override
   public int drawCenteredString(int x, int y, int color, String text) {
      return this.drawCenteredString(x, y, color, text, false);
   }

   @Override
   public int drawCenteredString(int x, int y, ReadableColor color, String text) {
      return this.drawCenteredString(x, y, this.color(color), text);
   }

   @Override
   public int drawString(int x, int y, int color, String text, boolean shadow) {
      FontRenderer fontRenderer = MCVer.getFontRenderer();

      try {
         return shadow ? fontRenderer.drawStringWithShadow(text, x, y, color) : fontRenderer.drawString(text, x, y, color);
      } finally {
         this.color(1.0F, 1.0F, 1.0F);
      }
   }

   @Override
   public int drawString(int x, int y, ReadableColor color, String text, boolean shadow) {
      return this.drawString(x, y, this.color(color), text, shadow);
   }

   @Override
   public int drawCenteredString(int x, int y, int color, String text, boolean shadow) {
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      x -= fontRenderer.getStringWidth(text) / 2;
      return this.drawString(x, y, color, text, shadow);
   }

   @Override
   public int drawCenteredString(int x, int y, ReadableColor color, String text, boolean shadow) {
      return this.drawCenteredString(x, y, this.color(color), text, shadow);
   }

   private int color(ReadableColor color) {
      return color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
   }

   private ReadableColor color(int color) {
      return new Color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF);
   }

   private void color(float r, float g, float b) {
      GlStateManager.color(r, g, b);
   }

   @Override
   public void invertColors(int right, int bottom, int left, int top) {
      if (left < right && top < bottom) {
         this.color(0.0F, 0.0F, 1.0F);
         GlStateManager.disableTexture2D();
         GlStateManager.enableColorLogic();
         GlStateManager.colorLogicOp(5387);
         this.drawRect(right, bottom, right - left, bottom - top, ReadableColor.WHITE, ReadableColor.WHITE, ReadableColor.WHITE, ReadableColor.WHITE, true);
         GlStateManager.disableColorLogic();
         GlStateManager.enableTexture2D();
         this.color(1.0F, 1.0F, 1.0F);
      }
   }
}
