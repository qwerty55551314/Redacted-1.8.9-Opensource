package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

public abstract class AbstractGuiLabel<T extends AbstractGuiLabel<T>> extends AbstractGuiElement<T> implements IGuiLabel<T> {
   private String text = "";
   private ReadableColor color = ReadableColor.WHITE;
   private ReadableColor disabledColor = ReadableColor.GREY;

   public AbstractGuiLabel() {
   }

   public AbstractGuiLabel(GuiContainer container) {
      super(container);
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      List<String> lines = fontRenderer.listFormattedStringToWidth(this.text, size.getWidth());
      int y = 0;

      for (String line : lines) {
         renderer.drawString(0, y, this.isEnabled() ? this.color : this.disabledColor, line);
         y += fontRenderer.FONT_HEIGHT;
      }
   }

   @Override
   public ReadableDimension calcMinSize() {
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      return new Dimension(fontRenderer.getStringWidth(this.text), fontRenderer.FONT_HEIGHT);
   }

   @Override
   public ReadableDimension getMaxSize() {
      return this.getMinSize();
   }

   public T setText(String text) {
      this.text = text;
      return this.getThis();
   }

   public T setI18nText(String text, Object... args) {
      return this.setText(I18n.format(text, args));
   }

   public T setColor(ReadableColor color) {
      this.color = color;
      return this.getThis();
   }

   public T setDisabledColor(ReadableColor disabledColor) {
      this.disabledColor = disabledColor;
      return this.getThis();
   }

   @Override
   public String getText() {
      return this.text;
   }

   @Override
   public ReadableColor getColor() {
      return this.color;
   }

   @Override
   public ReadableColor getDisabledColor() {
      return this.disabledColor;
   }
}
