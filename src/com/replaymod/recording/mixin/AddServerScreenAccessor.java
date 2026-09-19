package com.replaymod.recording.mixin;

import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.multiplayer.ServerData;

public interface AddServerScreenAccessor {
   ServerData getServer();
}
