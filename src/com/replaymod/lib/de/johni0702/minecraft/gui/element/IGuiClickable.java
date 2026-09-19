package com.replaymod.lib.de.johni0702.minecraft.gui.element;

import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer;

public interface IGuiClickable<T extends IGuiClickable<T>> extends GuiElement<T> {
   default T onClick(Runnable onClick) {
      return this.onClick(click -> onClick.run());
   }

   T onClick(Consumer<Click> var1);

   Consumer<Click> getOnClick();
}
