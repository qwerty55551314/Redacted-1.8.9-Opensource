package com.replaymod.render.hooks;

import com.replaymod.core.versions.MCVer;
import com.replaymod.render.mixin.ChunkRenderDispatcherAccessor;
import com.replaymod.render.mixin.WorldRendererAccessor;
import com.replaymod.render.utils.JailingQueue;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;
import net.minecraft.client.renderer.chunk.RenderChunk;

public class ForceChunkLoadingHook {
   private final RenderGlobal hooked;
   private ChunkRenderDispatcher renderDispatcher;
   private JailingQueue<ChunkCompileTaskGenerator> workerJailingQueue;
   private MCVer.ChunkRenderWorkerAccessor renderWorker;
   private int frame;

   public ForceChunkLoadingHook(RenderGlobal renderGlobal) {
      this.hooked = renderGlobal;
      this.setup(((WorldRendererAccessor)renderGlobal).getRenderDispatcher());
      IForceChunkLoading.from(renderGlobal).replayModRender_setHook(this);
   }

   public void updateRenderDispatcher(ChunkRenderDispatcher renderDispatcher) {
      if (this.renderDispatcher != null) {
         this.workerJailingQueue.freeAll();
         this.renderDispatcher = null;
      }

      if (renderDispatcher != null) {
         this.setup(renderDispatcher);
      }
   }

   private void setup(ChunkRenderDispatcher renderDispatcher) {
      this.renderDispatcher = renderDispatcher;
      this.renderWorker = (MCVer.ChunkRenderWorkerAccessor)(new ChunkRenderWorker(renderDispatcher, new RegionRenderCacheBuilder()));
      ChunkRenderDispatcherAccessor renderDispatcherAcc = (ChunkRenderDispatcherAccessor)renderDispatcher;
      int workerThreads = renderDispatcherAcc.getListThreadedWorkers().size();
      BlockingQueue<ChunkCompileTaskGenerator> queueChunkUpdates = renderDispatcherAcc.getQueueChunkUpdates();
      this.workerJailingQueue = new JailingQueue<>(queueChunkUpdates);
      renderDispatcherAcc.setQueueChunkUpdates(this.workerJailingQueue);
      ChunkCompileTaskGenerator element = new ChunkCompileTaskGenerator(null, null);
      element.finish();

      for (int i = 0; i < workerThreads; i++) {
         queueChunkUpdates.add(element);
      }

      while (renderDispatcher.runChunkUploads(0L)) {
      }

      this.workerJailingQueue.jail(workerThreads);
      renderDispatcherAcc.setQueueChunkUpdates(queueChunkUpdates);
   }

   public void updateChunks() {
      while (this.renderDispatcher.runChunkUploads(0L)) {
         ((WorldRendererAccessor)this.hooked).setDisplayListEntitiesDirty(true);
      }

      BlockingQueue<ChunkCompileTaskGenerator> queueChunkUpdates = ((ChunkRenderDispatcherAccessor)this.renderDispatcher).getQueueChunkUpdates();

      while (!queueChunkUpdates.isEmpty()) {
         try {
            this.renderWorker.doRunTask(queueChunkUpdates.poll());
         } catch (InterruptedException var4) {
         }
      }

      Iterator<RenderChunk> iterator = ((WorldRendererAccessor)this.hooked).getChunksToUpdate().iterator();

      while (iterator.hasNext()) {
         RenderChunk renderchunk = iterator.next();
         this.renderDispatcher.updateChunkNow(renderchunk);
         renderchunk.setNeedsUpdate(false);
         iterator.remove();
      }
   }

   public int nextFrameId() {
      return this.frame++;
   }

   public void uninstall() {
      this.workerJailingQueue.freeAll();
      IForceChunkLoading.from(this.hooked).replayModRender_setHook(null);
   }
}
