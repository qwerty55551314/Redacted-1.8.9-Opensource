package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.OffsetGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

public abstract class AbstractGuiHorizontalScrollbar<T extends AbstractGuiHorizontalScrollbar<T>>
   extends AbstractGuiElement<T>
   implements Clickable,
   Draggable,
   IGuiHorizontalScrollbar<T> {
   protected static final int TEXTURE_FG_X = 0;
   protected static final int TEXTURE_FG_Y = 0;
   protected static final int TEXTURE_FG_WIDTH = 62;
   protected static final int TEXTURE_FG_HEIGHT = 7;
   protected static final int TEXTURE_BG_X = 0;
   protected static final int TEXTURE_BG_Y = 7;
   protected static final int TEXTURE_BG_WIDTH = 64;
   protected static final int TEXTURE_BG_HEIGHT = 9;
   protected static final int BORDER_TOP = 1;
   protected static final int BORDER_BOTTOM = 1;
   protected static final int BORDER_LEFT = 1;
   protected static final int BORDER_RIGHT = 1;
   private Runnable onValueChanged;
   private double zoom = 1.0;
   private double offset;
   private ReadablePoint startDragging;
   private boolean dragging;

   public AbstractGuiHorizontalScrollbar() {
   }

   public AbstractGuiHorizontalScrollbar(GuiContainer container) {
      super(container);
   }

   @Override
   protected ReadableDimension calcMinSize() {
      return new Dimension(0, 0);
   }

   @Override
   public boolean mouseClick(Click click) {
      Point pos = new Point(click);
      if (this.getContainer() != null) {
         this.getContainer().convertFor(this, pos);
      }

      if (this.isMouseHoveringBar(pos) && this.isEnabled()) {
         this.dragging = true;
         this.updateValue(pos);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDrag(Click click) {
      if (this.dragging) {
         Point pos = new Point(click);
         if (this.getContainer() != null) {
            this.getContainer().convertFor(this, pos);
         }

         this.updateValue(pos);
      }

      return this.dragging;
   }

   @Override
   public boolean mouseRelease(Click click) {
      if (this.dragging) {
         Point pos = new Point(click);
         if (this.getContainer() != null) {
            this.getContainer().convertFor(this, pos);
         }

         this.updateValue(pos);
         this.dragging = false;
         this.startDragging = null;
         return true;
      } else {
         return false;
      }
   }

   protected boolean isMouseHoveringBar(ReadablePoint pos) {
      int bodyWidth = this.getLastSize().getWidth() - 1 - 1;
      int barOffset = (int)(bodyWidth * this.offset) + 1;
      int barWidth = (int)(bodyWidth * this.zoom);
      return pos.getX() >= barOffset && pos.getY() > 1 && pos.getX() <= barOffset + barWidth && pos.getY() < this.getLastSize().getHeight() - 1;
   }

   protected void updateValue(ReadablePoint position) {
      if (this.getLastSize() != null) {
         if (this.startDragging != null) {
            double d = position.getX() - this.startDragging.getX();
            this.offset = this.offset + d / (this.getLastSize().getWidth() - 1 - 1);
            this.checkOffset();
            this.onValueChanged();
         }

         this.startDragging = position;
      }
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      int width = size.getWidth();
      int height = size.getHeight();
      renderer.bindTexture(TEXTURE);
      Utils.drawDynamicRect(renderer, width, height, 0, 7, 64, 9, 2, 1, 2, 1);
      int bodyWidth = size.getWidth() - 1 - 1;
      int barOffset = (int)(bodyWidth * this.offset) + 1;
      int barWidth = (int)(bodyWidth * this.zoom);
      Utils.drawDynamicRect(new OffsetGuiRenderer(renderer, new Point(barOffset, 1), size), barWidth, height - 2 - 1, 0, 0, 62, 7, 2, 1, 1, 1);
   }

   public void onValueChanged() {
      if (this.onValueChanged != null) {
         this.onValueChanged.run();
      }
   }

   public T onValueChanged(Runnable runnable) {
      this.onValueChanged = runnable;
      return this.getThis();
   }

   public T setPosition(double pos) {
      this.offset = pos;
      this.checkOffset();
      this.onValueChanged();
      return this.getThis();
   }

   @Override
   public double getPosition() {
      return this.offset;
   }

   public T setZoom(double zoom) {
      this.zoom = Math.min(1.0, Math.max(1.0E-4, zoom));
      this.checkOffset();
      this.onValueChanged();
      return this.getThis();
   }

   @Override
   public double getZoom() {
      return this.zoom;
   }

   private void checkOffset() {
      if (this.offset < 0.0) {
         this.offset = 0.0;
      } else if (this.zoom + this.offset > 1.0) {
         this.offset = 1.0 - this.zoom;
      }
   }
}
