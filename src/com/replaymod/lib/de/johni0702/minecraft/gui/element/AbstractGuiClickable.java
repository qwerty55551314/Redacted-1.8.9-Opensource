package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

public abstract class AbstractGuiClickable<T extends AbstractGuiClickable<T>> extends AbstractGuiElement<T> implements Clickable, IGuiClickable<T> {
   private Consumer<Click> onClick;

   public AbstractGuiClickable() {
   }

   public AbstractGuiClickable(GuiContainer container) {
      super(container);
   }

   @Override
   public boolean mouseClick(Click click) {
      Point pos = new Point(click);
      if (this.getContainer() != null) {
         this.getContainer().convertFor(this, pos);
      }

      if (this.isMouseHovering(pos) && this.isEnabled()) {
         this.onClick(click);
         return true;
      } else {
         return false;
      }
   }

   protected boolean isMouseHovering(ReadablePoint pos) {
      return pos.getX() > 0 && pos.getY() > 0 && pos.getX() < this.getLastSize().getWidth() && pos.getY() < this.getLastSize().getHeight();
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
   }

   protected void onClick(Click click) {
      if (this.onClick != null) {
         this.onClick.consume(click);
      }
   }

   public T onClick(Consumer<Click> onClick) {
      this.onClick = onClick;
      return this.getThis();
   }

   @Override
   public Consumer<Click> getOnClick() {
      return this.onClick;
   }
}
