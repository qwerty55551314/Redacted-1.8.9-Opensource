package com.replaymod.lib.de.johni0702.minecraft.gui.container;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritableDimension;

public abstract class AbstractGuiVerticalList<T extends AbstractGuiVerticalList<T>> extends AbstractGuiScrollable<T> implements Draggable {
   public static final ReadableColor BACKGROUND = new Color(0, 0, 0, 150);
   private final VerticalLayout listLayout = new VerticalLayout().setSpacing(3);
   private final GuiPanel listPanel = new GuiPanel(this).setLayout(this.listLayout);
   private boolean drawShadow;
   private boolean drawSlider;
   private ReadablePoint lastMousePos;
   private boolean draggingSlider;

   public AbstractGuiVerticalList() {
      this.setLayout(new CustomLayout<T>() {
         protected void layout(T container, int width, int height) {
            this.pos(AbstractGuiVerticalList.this.listPanel, width / 2 - this.width(AbstractGuiVerticalList.this.listPanel) / 2, 5);
         }

         @Override
         public ReadableDimension calcMinSize(GuiContainer<?> container) {
            final ReadableDimension panelSize = AbstractGuiVerticalList.this.listPanel.getMinSize();
            return new ReadableDimension() {
               @Override
               public int getWidth() {
                  return panelSize.getWidth();
               }

               @Override
               public int getHeight() {
                  return panelSize.getHeight() + 10;
               }

               @Override
               public void getSize(WritableDimension dest) {
                  dest.setSize(this.getWidth(), this.getHeight());
               }
            };
         }
      });
   }

   public AbstractGuiVerticalList(GuiContainer container) {
      super(container);
      this.setLayout(new CustomLayout<T>() {
         protected void layout(T container, int width, int height) {
            this.pos(AbstractGuiVerticalList.this.listPanel, width / 2 - this.width(AbstractGuiVerticalList.this.listPanel) / 2, 5);
         }

         @Override
         public ReadableDimension calcMinSize(GuiContainer<?> container) {
            final ReadableDimension panelSize = AbstractGuiVerticalList.this.listPanel.getMinSize();
            return new ReadableDimension() {
               @Override
               public int getWidth() {
                  return panelSize.getWidth();
               }

               @Override
               public int getHeight() {
                  return panelSize.getHeight() + 10;
               }

               @Override
               public void getSize(WritableDimension dest) {
                  dest.setSize(this.getWidth(), this.getHeight());
               }
            };
         }
      });
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      int width = size.getWidth();
      int height = size.getHeight();
      if (this.drawShadow) {
         renderer.drawRect(0, 0, width, height, BACKGROUND);
         super.draw(renderer, size, renderInfo);
         renderer.drawRect(0, 0, width, 4, ReadableColor.BLACK, ReadableColor.BLACK, Colors.TRANSPARENT, Colors.TRANSPARENT);
         renderer.drawRect(0, height - 4, width, 4, Colors.TRANSPARENT, Colors.TRANSPARENT, ReadableColor.BLACK, ReadableColor.BLACK);
      } else {
         super.draw(renderer, size, renderInfo);
      }

      if (this.drawSlider) {
         ReadableDimension contentSize = this.listPanel.calcMinSize();
         int contentHeight = contentSize.getHeight() + 10;
         if (contentHeight > height) {
            int sliderX = width / 2 + contentSize.getWidth() / 2 + 3;
            renderer.drawRect(sliderX, 0, 6, height, ReadableColor.BLACK);
            int sliderY = this.getOffsetY() * height / contentHeight;
            int sliderSize = height * height / contentHeight;
            renderer.drawRect(sliderX, sliderY, 6, sliderSize, Color.LTGREY);
            renderer.drawRect(sliderX + 5, sliderY, 1, sliderSize, Color.GREY);
            renderer.drawRect(sliderX, sliderY + sliderSize - 1, 6, 1, Color.GREY);
         }
      }
   }

   @Override
   public boolean mouseClick(Click click) {
      ReadablePoint position = this.convert(click);
      if (this.isOnThis(position)) {
         if (this.isOnSliderBar(position)) {
            this.draggingSlider = true;
         }

         this.lastMousePos = position;
      }

      return false;
   }

   @Override
   public boolean mouseDrag(Click click) {
      ReadablePoint position = this.convert(click);
      if (this.lastMousePos != null) {
         int dPixel = this.lastMousePos.getY() - position.getY();
         if (this.draggingSlider) {
            int contentHeight = this.listPanel.calcMinSize().getHeight();
            int renderHeight = this.lastRenderSize.getHeight();
            this.scrollY(dPixel * (contentHeight + renderHeight) / renderHeight);
         } else {
            this.scrollY(-dPixel);
         }

         this.lastMousePos = position;
      }

      return false;
   }

   @Override
   public boolean mouseRelease(Click click) {
      if (this.lastMousePos != null) {
         this.lastMousePos = null;
         this.draggingSlider = false;
      }

      return false;
   }

   private ReadablePoint convert(ReadablePoint readablePoint) {
      if (this.getContainer() != null) {
         Point point = new Point(readablePoint);
         this.getContainer().convertFor(this, point);
         return point;
      } else {
         return readablePoint;
      }
   }

   private boolean isOnThis(ReadablePoint point) {
      return point.getX() > 0 && point.getY() > 0 && point.getX() < this.lastRenderSize.getWidth() && point.getY() < this.lastRenderSize.getHeight();
   }

   private boolean isOnSliderBar(ReadablePoint point) {
      if (!this.drawSlider) {
         return false;
      }

      int sliderX = this.lastRenderSize.getWidth() / 2 + this.listPanel.calcMinSize().getWidth() / 2 + 3;
      return sliderX <= point.getX() && point.getX() < sliderX + 6;
   }

   private boolean isOnBackground(ReadablePoint point) {
      int width = this.lastRenderSize.getWidth();
      int listPanelWidth = this.listPanel.calcMinSize().getWidth();
      return point.getX() < width / 2 - listPanelWidth / 2 || width / 2 + listPanelWidth / 2 + (this.drawSlider ? 6 : 0) < point.getX();
   }

   public boolean doesDrawSlider() {
      return this.drawSlider;
   }

   public T setDrawSlider(boolean drawSlider) {
      this.drawSlider = drawSlider;
      return this.getThis();
   }

   public boolean doesDrawShadow() {
      return this.drawShadow;
   }

   public T setDrawShadow(boolean drawShadow) {
      this.drawShadow = drawShadow;
      return this.getThis();
   }

   public VerticalLayout getListLayout() {
      return this.listLayout;
   }

   public GuiPanel getListPanel() {
      return this.listPanel;
   }
}
