package com.replaymod.lib.de.johni0702.minecraft.gui.layout;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;

public class VerticalLayout implements Layout {
   private static final VerticalLayout.Data DEFAULT_DATA = new VerticalLayout.Data(0.0);
   private final VerticalLayout.Alignment alignment;
   private int spacing;

   public VerticalLayout() {
      this(VerticalLayout.Alignment.TOP);
   }

   public VerticalLayout(VerticalLayout.Alignment alignment) {
      this.alignment = alignment;
   }

   @Override
   public Map<GuiElement, Pair<ReadablePoint, ReadableDimension>> layOut(GuiContainer<?> container, ReadableDimension size) {
      int y = 0;
      int spacing = 0;
      Map<GuiElement, Pair<ReadablePoint, ReadableDimension>> map = new LinkedHashMap<>();

      for (Entry<GuiElement, LayoutData> entry : container.getElements().entrySet()) {
         y += spacing;
         spacing = this.spacing;
         GuiElement element = entry.getKey();
         VerticalLayout.Data data = entry.getValue() instanceof VerticalLayout.Data ? (VerticalLayout.Data)entry.getValue() : DEFAULT_DATA;
         Dimension elementSize = new Dimension(element.getMinSize());
         ReadableDimension elementMaxSize = element.getMaxSize();
         elementSize.setHeight(Math.min(size.getHeight() - y, Math.min(elementSize.getHeight(), elementMaxSize.getHeight())));
         elementSize.setWidth(Math.min(size.getWidth(), (data.maximizeWidth ? elementMaxSize : elementSize).getWidth()));
         int remainingWidth = size.getWidth() - elementSize.getWidth();
         int x = (int)(data.alignment * remainingWidth);
         map.put(element, Pair.of(new Point(x, y), elementSize));
         y += elementSize.getHeight();
      }

      if (this.alignment != VerticalLayout.Alignment.TOP) {
         int remaining = size.getHeight() - y;
         if (this.alignment == VerticalLayout.Alignment.CENTER) {
            remaining /= 2;
         }

         for (Pair<ReadablePoint, ReadableDimension> pair : map.values()) {
            ((Point)pair.getLeft()).translate(0, remaining);
         }
      }

      return map;
   }

   @Override
   public ReadableDimension calcMinSize(GuiContainer<?> container) {
      int maxWidth = 0;
      int height = 0;
      int spacing = 0;

      for (Entry<GuiElement, LayoutData> entry : container.getElements().entrySet()) {
         height += spacing;
         spacing = this.spacing;
         GuiElement element = entry.getKey();
         ReadableDimension minSize = element.getMinSize();
         int width = minSize.getWidth();
         if (width > maxWidth) {
            maxWidth = width;
         }

         height += minSize.getHeight();
      }

      return new Dimension(maxWidth, height);
   }

   public int getSpacing() {
      return this.spacing;
   }

   public VerticalLayout setSpacing(int spacing) {
      this.spacing = spacing;
      return this;
   }

   public enum Alignment {
      TOP,
      BOTTOM,
      CENTER;
   }

   public static class Data implements LayoutData {
      private double alignment;
      private boolean maximizeWidth;

      public Data() {
         this(0.0);
      }

      public Data(double alignment) {
         this(alignment, true);
      }

      public Data(double alignment, boolean maximizeWidth) {
         this.alignment = alignment;
         this.maximizeWidth = maximizeWidth;
      }

      public double getAlignment() {
         return this.alignment;
      }

      public boolean isMaximizeWidth() {
         return this.maximizeWidth;
      }

      public void setAlignment(double alignment) {
         this.alignment = alignment;
      }

      public void setMaximizeWidth(boolean maximizeWidth) {
         this.maximizeWidth = maximizeWidth;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            VerticalLayout.Data data = (VerticalLayout.Data)o;
            return Double.compare(data.alignment, this.alignment) == 0 && this.maximizeWidth == data.maximizeWidth;
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.alignment, this.maximizeWidth);
      }

      @Override
      public String toString() {
         return "Data{alignment=" + this.alignment + ", maximizeWidth=" + this.maximizeWidth + '}';
      }
   }
}
