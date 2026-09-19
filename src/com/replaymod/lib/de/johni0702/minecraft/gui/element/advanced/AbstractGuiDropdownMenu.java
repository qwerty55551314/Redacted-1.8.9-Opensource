package com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.OffsetGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractComposedGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiClickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.IGuiClickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.FontRenderer;

public abstract class AbstractGuiDropdownMenu<V, T extends AbstractGuiDropdownMenu<V, T>>
   extends AbstractComposedGuiElement<T>
   implements IGuiDropdownMenu<V, T>,
   Clickable {
   private static final ReadableColor OUTLINE_COLOR = new Color(160, 160, 160);
   private int selected;
   private V[] values;
   private boolean opened;
   private Consumer<Integer> onSelection;
   private GuiPanel dropdown;
   private Map<V, IGuiClickable> unmodifiableDropdownEntries;
   private Function<V, String> toString = Object::toString;

   public AbstractGuiDropdownMenu() {
   }

   public AbstractGuiDropdownMenu(GuiContainer container) {
      super(container);
   }

   @Override
   public int getMaxLayer() {
      return this.opened ? 1 : 0;
   }

   @Override
   protected ReadableDimension calcMinSize() {
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      int maxWidth = 0;

      for (V value : this.values) {
         int width = fontRenderer.getStringWidth(this.toString.apply(value));
         if (width > maxWidth) {
            maxWidth = width;
         }
      }

      return new Dimension(11 + maxWidth + fontRenderer.FONT_HEIGHT, fontRenderer.FONT_HEIGHT + 4);
   }

   @Override
   public void layout(ReadableDimension size, RenderInfo renderInfo) {
      super.layout(size, renderInfo);
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      if (renderInfo.layer == 1) {
         ReadablePoint offsetPoint = new Point(0, size.getHeight());
         ReadableDimension offsetSize = new Dimension(size.getWidth(), (fontRenderer.FONT_HEIGHT + 5) * this.values.length);
         this.dropdown.layout(offsetSize, renderInfo.offsetMouse(0, offsetPoint.getY()).layer(0));
      }
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      if (renderInfo.layer == 0) {
         int width = size.getWidth();
         int height = size.getHeight();
         renderer.drawRect(0, 0, width, height, OUTLINE_COLOR);
         renderer.drawRect(1, 1, width - 2, height - 2, ReadableColor.BLACK);
         renderer.drawRect(width - height, 0, 1, height, OUTLINE_COLOR);
         int base = height - 6;
         int tHeight = base / 2;
         int x = width - 3 - base / 2;
         int y = height / 2 - 2;

         for (int layer = tHeight; layer > 0; layer--) {
            renderer.drawRect(x - layer, y + (tHeight - layer), layer * 2 - 1, 1, OUTLINE_COLOR);
         }

         renderer.drawString(3, height / 2 - fontRenderer.FONT_HEIGHT / 2, ReadableColor.WHITE, this.toString.apply(this.getSelectedValue()));
      } else if (renderInfo.layer == 1) {
         ReadablePoint offsetPoint = new Point(0, size.getHeight());
         ReadableDimension offsetSize = new Dimension(size.getWidth(), (fontRenderer.FONT_HEIGHT + 5) * this.values.length);
         OffsetGuiRenderer offsetRenderer = new OffsetGuiRenderer(renderer, offsetPoint, offsetSize);
         offsetRenderer.startUsing();

         try {
            this.dropdown.draw(offsetRenderer, offsetSize, renderInfo.offsetMouse(0, offsetPoint.getY()).layer(0));
         } finally {
            offsetRenderer.stopUsing();
         }
      }
   }

   public T setValues(V... values) {
      this.values = values;
      this.dropdown = (GuiPanel)(new GuiPanel() {
         @Override
         public void convertFor(GuiElement element, Point point, int relativeLayer) {
            AbstractGuiDropdownMenu parent = AbstractGuiDropdownMenu.this;
            if (parent.getContainer() != null) {
               parent.getContainer().convertFor(parent, point, relativeLayer + 1);
            }

            point.translate(0, -AbstractGuiDropdownMenu.this.getLastSize().getHeight());
            super.convertFor(element, point, relativeLayer);
         }
      }).setLayout(new VerticalLayout());
      Map<V, IGuiClickable> dropdownEntries = new LinkedHashMap<>();

      for (V value : values) {
         AbstractGuiDropdownMenu<V, T>.DropdownEntry entry = new AbstractGuiDropdownMenu.DropdownEntry(value);
         dropdownEntries.put(value, entry);
         this.dropdown.addElements(null, entry);
      }

      this.unmodifiableDropdownEntries = Collections.unmodifiableMap(dropdownEntries);
      return this.getThis();
   }

   public T setSelected(int selected) {
      this.selected = selected;
      this.onSelection(selected);
      return this.getThis();
   }

   public T setSelected(V value) {
      for (int i = 0; i < this.values.length; i++) {
         if (this.values[i].equals(value)) {
            return this.setSelected(i);
         }
      }

      throw new IllegalArgumentException("The value " + value + " is not in this dropdown menu.");
   }

   @Override
   public V getSelectedValue() {
      return this.values[this.selected];
   }

   public T setOpened(boolean opened) {
      this.opened = opened;
      return this.getThis();
   }

   @Override
   public Collection<GuiElement> getChildren() {
      return this.opened ? Collections.singletonList(this.dropdown) : Collections.emptyList();
   }

   public T onSelection(Consumer<Integer> consumer) {
      this.onSelection = consumer;
      return this.getThis();
   }

   public void onSelection(Integer value) {
      if (this.onSelection != null) {
         this.onSelection.consume(value);
      }
   }

   @Override
   public boolean mouseClick(Click click) {
      Point pos = new Point(click);
      if (this.getContainer() != null) {
         this.getContainer().convertFor(this, pos);
      }

      if (this.isEnabled() && this.isMouseHovering(pos)) {
         this.setOpened(!this.isOpened());
         return true;
      } else {
         return false;
      }
   }

   protected boolean isMouseHovering(ReadablePoint pos) {
      return pos.getX() > 0 && pos.getY() > 0 && pos.getX() < this.getLastSize().getWidth() && pos.getY() < this.getLastSize().getHeight();
   }

   @Override
   public Map<V, IGuiClickable> getDropdownEntries() {
      return this.unmodifiableDropdownEntries;
   }

   public T setToString(Function<V, String> toString) {
      this.toString = toString;
      return this.getThis();
   }

   @Override
   public int getSelected() {
      return this.selected;
   }

   @Override
   public V[] getValues() {
      return this.values;
   }

   @Override
   public boolean isOpened() {
      return this.opened;
   }

   private class DropdownEntry extends AbstractGuiClickable<AbstractGuiDropdownMenu<V, T>.DropdownEntry> {
      private final Object value;

      public DropdownEntry(V value) {
         this.value = value;
      }

      protected AbstractGuiDropdownMenu<V, T>.DropdownEntry getThis() {
         return this;
      }

      @Override
      protected ReadableDimension calcMinSize() {
         return new Dimension(0, MCVer.getFontRenderer().FONT_HEIGHT + 5);
      }

      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         super.draw(renderer, size, renderInfo);
         int width = size.getWidth();
         int height = size.getHeight();
         renderer.drawRect(0, 0, width, height, AbstractGuiDropdownMenu.OUTLINE_COLOR);
         renderer.drawRect(1, 0, width - 2, height - 1, ReadableColor.BLACK);
         renderer.drawString(3, 2, ReadableColor.WHITE, AbstractGuiDropdownMenu.this.toString.apply((V)this.value));
      }

      @Override
      public boolean mouseClick(Click click) {
         boolean result = super.mouseClick(click);
         AbstractGuiDropdownMenu.this.setOpened(false);
         return result;
      }

      @Override
      protected void onClick(Click click) {
         AbstractGuiDropdownMenu.this.setSelected((V)this.value);
      }
   }
}
