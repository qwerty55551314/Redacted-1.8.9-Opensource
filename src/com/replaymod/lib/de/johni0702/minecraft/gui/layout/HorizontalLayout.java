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

public class HorizontalLayout implements Layout {
   private static final HorizontalLayout.Data DEFAULT_DATA = new HorizontalLayout.Data(0.0);
   private final HorizontalLayout.Alignment alignment;
   private int spacing;

   public HorizontalLayout() {
      this(HorizontalLayout.Alignment.LEFT);
   }

   public HorizontalLayout(HorizontalLayout.Alignment alignment) {
      this.alignment = alignment;
   }

   @Override
   public Map<GuiElement, Pair<ReadablePoint, ReadableDimension>> layOut(GuiContainer<?> container, ReadableDimension size) {
      int x = 0;
      int spacing = 0;
      Map<GuiElement, Pair<ReadablePoint, ReadableDimension>> map = new LinkedHashMap<>();

      for (Entry<GuiElement, LayoutData> entry : container.getElements().entrySet()) {
         x += spacing;
         spacing = this.spacing;
         GuiElement element = entry.getKey();
         HorizontalLayout.Data data = entry.getValue() instanceof HorizontalLayout.Data ? (HorizontalLayout.Data)entry.getValue() : DEFAULT_DATA;
         Dimension elementSize = new Dimension(element.getMinSize());
         ReadableDimension elementMaxSize = element.getMaxSize();
         elementSize.setWidth(Math.min(size.getWidth() - x, Math.min(elementSize.getWidth(), elementMaxSize.getWidth())));
         elementSize.setHeight(Math.min(size.getHeight(), elementMaxSize.getHeight()));
         int remainingHeight = size.getHeight() - elementSize.getHeight();
         int y = (int)(data.alignment * remainingHeight);
         map.put(element, Pair.of(new Point(x, y), elementSize));
         x += elementSize.getWidth();
      }

      if (this.alignment != HorizontalLayout.Alignment.LEFT) {
         int remaining = size.getWidth() - x;
         if (this.alignment == HorizontalLayout.Alignment.CENTER) {
            remaining /= 2;
         }

         for (Pair<ReadablePoint, ReadableDimension> pair : map.values()) {
            ((Point)pair.getLeft()).translate(remaining, 0);
         }
      }

      return map;
   }

   @Override
   public ReadableDimension calcMinSize(GuiContainer<?> container) {
      int maxHeight = 0;
      int width = 0;
      int spacing = 0;

      for (Entry<GuiElement, LayoutData> entry : container.getElements().entrySet()) {
         width += spacing;
         spacing = this.spacing;
         GuiElement element = entry.getKey();
         ReadableDimension minSize = element.getMinSize();
         int height = minSize.getHeight();
         if (height > maxHeight) {
            maxHeight = height;
         }

         width += minSize.getWidth();
      }

      return new Dimension(width, maxHeight);
   }

   public int getSpacing() {
      return this.spacing;
   }

   public HorizontalLayout setSpacing(int spacing) {
      this.spacing = spacing;
      return this;
   }

   public enum Alignment {
      LEFT,
      RIGHT,
      CENTER;
   }

   public static class Data implements LayoutData {
      private double alignment;

      public Data() {
         this(0.0);
      }

      public Data(double alignment) {
         this.alignment = alignment;
      }

      public double getAlignment() {
         return this.alignment;
      }

      public void setAlignment(double alignment) {
         this.alignment = alignment;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            HorizontalLayout.Data data = (HorizontalLayout.Data)o;
            return Double.compare(data.alignment, this.alignment) == 0;
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.alignment);
      }

      @Override
      public String toString() {
         return "Data{alignment=" + this.alignment + '}';
      }
   }
}
