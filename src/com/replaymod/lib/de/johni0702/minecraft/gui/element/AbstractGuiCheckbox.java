package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

public abstract class AbstractGuiCheckbox<T extends AbstractGuiCheckbox<T>> extends AbstractGuiClickable<T> implements IGuiCheckbox<T> {
   protected static final ResourceLocation BUTTON_SOUND = MCVer.identifier("gui.button.press");
   protected static final ReadableColor BOX_BACKGROUND_COLOR = new Color(46, 46, 46);
   private String label;
   private boolean checked;

   public AbstractGuiCheckbox() {
   }

   public AbstractGuiCheckbox(GuiContainer container) {
      super(container);
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      int color = 14737632;
      if (!this.isEnabled()) {
         color = 10526880;
      }

      int boxSize = size.getHeight();
      renderer.drawRect(0, 0, boxSize, boxSize, ReadableColor.BLACK);
      renderer.drawRect(1, 1, boxSize - 2, boxSize - 2, BOX_BACKGROUND_COLOR);
      if (this.isChecked()) {
         renderer.drawCenteredString(boxSize / 2 + 1, 1, color, "x", true);
      }

      renderer.drawString(boxSize + 2, 2, color, this.label);
   }

   @Override
   public ReadableDimension calcMinSize() {
      FontRenderer fontRenderer = MCVer.getFontRenderer();
      int height = fontRenderer.FONT_HEIGHT + 2;
      int width = height + 2 + fontRenderer.getStringWidth(this.label);
      return new Dimension(width, height);
   }

   @Override
   public ReadableDimension getMaxSize() {
      return this.getMinSize();
   }

   @Override
   public void onClick(Click click) {
      AbstractGuiButton.playClickSound(this.getMinecraft());
      this.setChecked(!this.isChecked());
      super.onClick(click);
   }

   public T setLabel(String label) {
      this.label = label;
      return this.getThis();
   }

   public T setI18nLabel(String label, Object... args) {
      return this.setLabel(I18n.format(label, args));
   }

   public T setChecked(boolean checked) {
      this.checked = checked;
      return this.getThis();
   }

   @Override
   public String getLabel() {
      return this.label;
   }

   @Override
   public boolean isChecked() {
      return this.checked;
   }
}
