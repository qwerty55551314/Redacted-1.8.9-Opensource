package com.replaymod.render.mixin;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;

public interface ChunkRenderDispatcherAccessor {
   List<ChunkRenderWorker> getListThreadedWorkers();

   BlockingQueue<ChunkCompileTaskGenerator> getQueueChunkUpdates();

   void setQueueChunkUpdates(BlockingQueue<ChunkCompileTaskGenerator> var1);
}
