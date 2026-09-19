package com.replaymod.replay.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public interface EntityPlayerAccessor {
   void setItemInUse(ItemStack var1);

   void setItemInUseCount(int var1);
}
