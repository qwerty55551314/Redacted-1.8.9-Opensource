package com.replaymod.replay.mixin;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;

public interface FirstPersonRendererAccessor {
   void setItemToRender(ItemStack var1);

   void setEquippedItemSlot(int var1);

   void setEquippedProgress(float var1);

   void setPrevEquippedProgress(float var1);
}
