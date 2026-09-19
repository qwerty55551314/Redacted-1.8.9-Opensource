package com.replaymod.render.mixin;

import java.util.Set;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;

public interface WorldRendererAccessor {
   void setRenderEntitiesStartupCounter(int var1);

   ChunkRenderDispatcher getRenderDispatcher();

   void setDisplayListEntitiesDirty(boolean var1);

   Set<RenderChunk> getChunksToUpdate();
}
