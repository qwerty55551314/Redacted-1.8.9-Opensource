package com.replaymod.core.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public interface GuiScreenAccessor {
   List<GuiButton> getButtons();
}
