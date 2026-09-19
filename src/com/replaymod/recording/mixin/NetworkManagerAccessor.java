package com.replaymod.recording.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.NetworkManager;

public interface NetworkManagerAccessor {
   Channel getChannel();
}
