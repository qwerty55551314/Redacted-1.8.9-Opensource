package com.replaymod.pathing.player;

import com.google.common.util.concurrent.ListenableFuture;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Timeline;

public class RealtimeTimelinePlayer extends AbstractTimelinePlayer {
   private boolean firstFrame;
   private boolean secondFrame;
   private long startTime;
   private boolean loadingResources;
   private long timeBeforeResourceLoading;

   public RealtimeTimelinePlayer(ReplayHandler replayHandler) {
      super(replayHandler);
   }

   @Override
   public ListenableFuture<Void> start(Timeline timeline) {
      this.firstFrame = true;
      this.loadingResources = false;
      return super.start(timeline);
   }

   @Override
   public void onTick() {
      if (this.secondFrame) {
         this.secondFrame = false;
         this.startTime = System.currentTimeMillis() - this.startOffset;
      }

      super.onTick();
      if (this.firstFrame) {
         this.firstFrame = false;
         this.secondFrame = true;
      }
   }

   @Override
   public long getTimePassed() {
      if (this.firstFrame) {
         return this.startOffset;
      } else {
         return this.loadingResources ? this.timeBeforeResourceLoading : System.currentTimeMillis() - this.startTime;
      }
   }
}
