package com.replaymod.lib.de.johni0702.minecraft.gui.container;

import com.google.common.base.Preconditions;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.OffsetGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractComposedGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.ComposedGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.Layout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.LayoutData;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;

public abstract class AbstractGuiContainer<T extends AbstractGuiContainer<T>> extends AbstractComposedGuiElement<T> implements GuiContainer<T> {
   private static final Layout DEFAULT_LAYOUT = new HorizontalLayout();
   private Map<GuiElement, LayoutData> elements = new LinkedHashMap<>();
   private Map<GuiElement, Pair<ReadablePoint, ReadableDimension>> layedOutElements;
   private Layout layout = DEFAULT_LAYOUT;
   private ReadableColor backgroundColor;

   public AbstractGuiContainer() {
   }

   public AbstractGuiContainer(GuiContainer container) {
      super(container);
   }

   public T setLayout(Layout layout) {
      this.layout = layout;
      return this.getThis();
   }

   @Override
   public Layout getLayout() {
      return this.layout;
   }

   @Override
   public void convertFor(GuiElement element, Point point) {
      this.convertFor(element, point, element.getLayer());
   }

   @Override
   public void convertFor(GuiElement element, Point point, int relativeLayer) {
      if (this.layedOutElements == null || !this.layedOutElements.containsKey(element)) {
         this.layout(null, new RenderInfo(0.0F, 0, 0, relativeLayer));
      }

      Preconditions.checkState(this.layedOutElements != null, "Cannot convert position unless rendered at least once.");
      Pair<ReadablePoint, ReadableDimension> pair = this.layedOutElements.get(element);
      Preconditions.checkState(pair != null, "Element " + element + " not part of " + this);
      ReadablePoint pos = pair.getKey();
      if (this.getContainer() != null) {
         this.getContainer().convertFor(this, point, relativeLayer + this.getLayer());
      }

      point.translate(-pos.getX(), -pos.getY());
   }

   @Override
   public Collection<GuiElement> getChildren() {
      return Collections.unmodifiableCollection(this.elements.keySet());
   }

   @Override
   public Map<GuiElement, LayoutData> getElements() {
      return Collections.unmodifiableMap(this.elements);
   }

   public T addElements(LayoutData layoutData, GuiElement... elements) {
      if (layoutData == null) {
         layoutData = LayoutData.NONE;
      }

      for (GuiElement element : elements) {
         this.elements.put(element, layoutData);
         element.setContainer(this);
      }

      return this.getThis();
   }

   public T removeElement(GuiElement element) {
      if (this.elements.remove(element) != null) {
         element.setContainer(null);
         if (this.layedOutElements != null) {
            this.layedOutElements.remove(element);
         }
      }

      return this.getThis();
   }

   @Override
   public void layout(ReadableDimension size, RenderInfo renderInfo) {
      super.layout(size, renderInfo);
      if (size != null) {
         try {
            this.layedOutElements = this.layout.layOut(this, size);
         } catch (Exception ex) {
            CrashReport crashReport = CrashReport.makeCrashReport(ex, "Gui Layout");
            renderInfo.addTo(crashReport);
            CrashReportCategory category = crashReport.makeCategory("Gui container details");
            MCVer.addDetail(category, "Container", this::toString);
            MCVer.addDetail(category, "Layout", this.layout::toString);
            throw new ReportedException(crashReport);
         }

         for (Entry<GuiElement, Pair<ReadablePoint, ReadableDimension>> e : this.layedOutElements.entrySet()) {
            GuiElement element = e.getKey();
            if (element instanceof ComposedGuiElement
               ? ((ComposedGuiElement)element).getMaxLayer() >= renderInfo.layer
               : element.getLayer() == renderInfo.layer) {
               ReadablePoint ePosition = e.getValue().getLeft();
               ReadableDimension eSize = e.getValue().getRight();
               element.layout(eSize, renderInfo.offsetMouse(ePosition.getX(), ePosition.getY()).layer(renderInfo.getLayer() - element.getLayer()));
            }
         }
      }
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      if (this.backgroundColor != null && renderInfo.getLayer() == 0) {
         renderer.drawRect(0, 0, size.getWidth(), size.getHeight(), this.backgroundColor);
      }

      for (Entry<GuiElement, Pair<ReadablePoint, ReadableDimension>> e : this.layedOutElements.entrySet()) {
         GuiElement element = e.getKey();
         boolean strict;
         if (element instanceof ComposedGuiElement) {
            if (((ComposedGuiElement)element).getMaxLayer() < renderInfo.layer) {
               continue;
            }

            strict = renderInfo.layer == 0;
         } else {
            if (element.getLayer() != renderInfo.layer) {
               continue;
            }

            strict = true;
         }

         ReadablePoint ePosition = e.getValue().getLeft();
         ReadableDimension eSize = e.getValue().getRight();

         try {
            OffsetGuiRenderer eRenderer = new OffsetGuiRenderer(renderer, ePosition, eSize, strict);
            eRenderer.startUsing();
            e.getKey().draw(eRenderer, eSize, renderInfo.offsetMouse(ePosition.getX(), ePosition.getY()).layer(renderInfo.getLayer() - e.getKey().getLayer()));
            eRenderer.stopUsing();
         } catch (Exception ex) {
            CrashReport crashReport = CrashReport.makeCrashReport(ex, "Rendering Gui");
            renderInfo.addTo(crashReport);
            CrashReportCategory category = crashReport.makeCategory("Gui container details");
            MCVer.addDetail(category, "Container", this::toString);
            MCVer.addDetail(category, "Width", () -> "" + size.getWidth());
            MCVer.addDetail(category, "Height", () -> "" + size.getHeight());
            MCVer.addDetail(category, "Layout", this.layout::toString);
            category = crashReport.makeCategory("Gui element details");
            MCVer.addDetail(category, "Element", () -> e.getKey().toString());
            MCVer.addDetail(category, "Position", ePosition::toString);
            MCVer.addDetail(category, "Size", eSize::toString);
            if (e.getKey() instanceof GuiContainer) {
               MCVer.addDetail(category, "Layout", () -> ((GuiContainer)e.getKey()).getLayout().toString());
            }

            throw new ReportedException(crashReport);
         }
      }
   }

   @Override
   public ReadableDimension calcMinSize() {
      return this.layout.calcMinSize(this);
   }

   public T sortElements() {
      this.sortElements(new Comparator<GuiElement>() {
         public int compare(GuiElement o1, GuiElement o2) {
            return o1 instanceof Comparable && o2 instanceof Comparable ? ((Comparable)o1).compareTo(o2) : o1.hashCode() - o2.hashCode();
         }
      });
      return this.getThis();
   }

   public T sortElements(Comparator<GuiElement> comparator) {
      this.elements = this.elements
         .entrySet()
         .stream()
         .sorted(Entry.comparingByKey(comparator))
         .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (x, y) -> y, LinkedHashMap::new));
      return this.getThis();
   }

   @Override
   public ReadableColor getBackgroundColor() {
      return this.backgroundColor;
   }

   public T setBackgroundColor(ReadableColor backgroundColor) {
      this.backgroundColor = backgroundColor;
      return this.getThis();
   }
}
