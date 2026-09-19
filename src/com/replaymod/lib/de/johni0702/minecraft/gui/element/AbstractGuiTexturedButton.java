package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritablePoint;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public abstract class AbstractGuiTexturedButton<T extends AbstractGuiTexturedButton<T>>
   extends AbstractGuiClickable<T>
   implements Clickable,
   IGuiTexturedButton<T> {
   private ResourceLocation texture;
   private ReadableDimension textureSize = new ReadableDimension() {
      @Override
      public int getWidth() {
         return AbstractGuiTexturedButton.this.getMaxSize().getWidth();
      }

      @Override
      public int getHeight() {
         return AbstractGuiTexturedButton.this.getMaxSize().getHeight();
      }

      @Override
      public void getSize(WritableDimension dest) {
         AbstractGuiTexturedButton.this.getMaxSize().getSize(dest);
      }
   };
   private ReadableDimension textureTotalSize;
   private ReadablePoint textureNormal;
   private ReadablePoint textureHover;
   private ReadablePoint textureDisabled;

   public AbstractGuiTexturedButton() {
   }

   public AbstractGuiTexturedButton(GuiContainer container) {
      super(container);
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      renderer.bindTexture(this.texture);
      ReadablePoint texture = this.textureNormal;
      if (!this.isEnabled()) {
         texture = this.textureDisabled;
      } else if (this.isMouseHovering(new Point(renderInfo.mouseX, renderInfo.mouseY))) {
         texture = this.textureHover;
      }

      if (texture == null) {
         GlStateManager.color(0.5F, 0.5F, 0.5F, 1.0F);
         texture = this.textureNormal;
      }

      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.blendFunc(770, 771);
      renderer.drawTexturedRect(
         0,
         0,
         texture.getX(),
         texture.getY(),
         size.getWidth(),
         size.getHeight(),
         this.textureSize.getWidth(),
         this.textureSize.getHeight(),
         this.textureTotalSize.getWidth(),
         this.textureTotalSize.getHeight()
      );
   }

   @Override
   public ReadableDimension calcMinSize() {
      return new Dimension(0, 0);
   }

   @Override
   public void onClick(Click click) {
      AbstractGuiButton.playClickSound(this.getMinecraft());
      super.onClick(click);
   }

   public T setTexture(ResourceLocation resourceLocation, int size) {
      return this.setTexture(resourceLocation, size, size);
   }

   public T setTexture(ResourceLocation resourceLocation, int width, int height) {
      this.texture = resourceLocation;
      this.textureTotalSize = new Dimension(width, height);
      return this.getThis();
   }

   public T setTextureSize(int size) {
      return this.setTextureSize(size, size);
   }

   public T setTextureSize(int width, int height) {
      this.textureSize = new Dimension(width, height);
      return this.getThis();
   }

   public T setTexturePosH(int x, int y) {
      return this.setTexturePosH(new Point(x, y));
   }

   public T setTexturePosV(int x, int y) {
      return this.setTexturePosV(new Point(x, y));
   }

   public T setTexturePosH(ReadablePoint pos) {
      this.textureNormal = pos;
      this.textureHover = new ReadablePoint() {
         @Override
         public int getX() {
            return pos.getX() + AbstractGuiTexturedButton.this.textureSize.getWidth();
         }

         @Override
         public int getY() {
            return pos.getY();
         }

         @Override
         public void getLocation(WritablePoint dest) {
            dest.setLocation(this.getX(), this.getY());
         }
      };
      return this.getThis();
   }

   public T setTexturePosV(ReadablePoint pos) {
      this.textureNormal = pos;
      this.textureHover = new ReadablePoint() {
         @Override
         public int getX() {
            return pos.getX();
         }

         @Override
         public int getY() {
            return pos.getY() + AbstractGuiTexturedButton.this.textureSize.getHeight();
         }

         @Override
         public void getLocation(WritablePoint dest) {
            dest.setLocation(this.getX(), this.getY());
         }
      };
      return this.getThis();
   }

   public T setTexturePos(int normalX, int normalY, int hoverX, int hoverY) {
      return this.setTexturePos(new Point(normalX, normalY), new Point(hoverX, hoverY));
   }

   public T setTexturePos(ReadablePoint normal, ReadablePoint hover) {
      this.textureNormal = normal;
      this.textureHover = hover;
      return this.getThis();
   }

   public T setTexturePos(int normalX, int normalY, int hoverX, int hoverY, int disabledX, int disabledY) {
      return this.setTexturePos(new Point(normalX, normalY), new Point(hoverX, hoverY), new Point(disabledX, disabledY));
   }

   public T setTexturePos(ReadablePoint normal, ReadablePoint hover, ReadablePoint disabled) {
      this.textureDisabled = disabled;
      return this.setTexturePos(normal, hover);
   }

   @Override
   public ResourceLocation getTexture() {
      return this.texture;
   }

   @Override
   public ReadableDimension getTextureSize() {
      return this.textureSize;
   }

   @Override
   public ReadableDimension getTextureTotalSize() {
      return this.textureTotalSize;
   }

   @Override
   public ReadablePoint getTextureNormal() {
      return this.textureNormal;
   }

   @Override
   public ReadablePoint getTextureHover() {
      return this.textureHover;
   }

   @Override
   public ReadablePoint getTextureDisabled() {
      return this.textureDisabled;
   }
}
