package com.replaymod.pathing.player;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.Timeline;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;

public abstract class AbstractTimelinePlayer extends EventRegistrations {
   private final Minecraft mc = MCVer.getMinecraft();
   private final ReplayHandler replayHandler;
   private Timeline timeline;
   protected long startOffset;
   private boolean wasAsyncMode;
   private Timer orgTimer;
   private long lastTime;
   private long lastTimestamp;
   private ListenableFuture<Void> future;
   private SettableFuture<Void> settableFuture;

   public AbstractTimelinePlayer(ReplayHandler replayHandler) {
      this.on(ReplayTimer.UpdatedCallback.EVENT, this::onTick);
      this.replayHandler = replayHandler;
   }

   public ListenableFuture<Void> start(Timeline timeline, long from) {
      this.startOffset = from;
      return this.start(timeline);
   }

   public ListenableFuture<Void> start(Timeline timeline) {
      this.timeline = timeline;
      Iterator<Keyframe> iter = Iterables.concat(Iterables.transform(timeline.getPaths(), new Function<Path, Iterable<Keyframe>>() {
         @Nullable
         public Iterable<Keyframe> apply(@Nullable Path input) {
            assert input != null;
            return input.getKeyframes();
         }
      })).iterator();
      if (!iter.hasNext()) {
         this.lastTimestamp = 0L;
      } else {
         this.lastTimestamp = ((Keyframe)(new Ordering<Keyframe>() {
            public int compare(@Nullable Keyframe left, @Nullable Keyframe right) {
               assert left != null;
               assert right != null;
               return Longs.compare(left.getTime(), right.getTime());
            }
         }).max(iter)).getTime();
      }

      this.wasAsyncMode = this.replayHandler.getReplaySender().isAsyncMode();
      this.replayHandler.getReplaySender().setSyncModeAndWait();
      this.register();
      this.lastTime = 0L;
      MinecraftAccessor mcA = (MinecraftAccessor)this.mc;
      this.orgTimer = mcA.getTimer();
      ReplayTimer timer = new ReplayTimer();
      mcA.setTimer(timer);
      TimerAccessor timerA = (TimerAccessor)timer;
      timer.timerSpeed = 1.0F;
      timer.elapsedPartialTicks = timer.elapsedTicks = 0;
      return this.future = this.settableFuture = SettableFuture.create();
   }

   public ListenableFuture<Void> getFuture() {
      return this.future;
   }

   public boolean isActive() {
      return this.future != null && !this.future.isDone();
   }

   public void onTick() {
      if (this.future.isDone()) {
         MinecraftAccessor mcA = (MinecraftAccessor)this.mc;
         mcA.setTimer(this.orgTimer);
         this.replayHandler.getReplaySender().setReplaySpeed(0.0);
         if (this.wasAsyncMode) {
            this.replayHandler.getReplaySender().setAsyncMode(true);
         }

         this.unregister();
      } else {
         long time = this.getTimePassed();
         if (time > this.lastTimestamp) {
            time = this.lastTimestamp;
         }

         this.timeline.applyToGame(time, this.replayHandler);
         this.timeline.applyToGame(time, this.replayHandler);
         long replayTime = this.replayHandler.getReplaySender().currentTimeStamp();
         if (this.lastTime == 0L) {
            this.lastTime = replayTime;
         }

         float timeInTicks = (float)replayTime / 50.0F;
         float previousTimeInTicks = (float)this.lastTime / 50.0F;
         float passedTicks = timeInTicks - previousTimeInTicks;
         Timer renderTickCounter = ((MinecraftAccessor)this.mc).getTimer();
         if (renderTickCounter instanceof ReplayTimer) {
            ReplayTimer timer = (ReplayTimer)renderTickCounter;
            timer.renderPartialTicks += passedTicks;
            timer.elapsedTicks = (int)timer.renderPartialTicks;
            timer.renderPartialTicks = timer.renderPartialTicks - timer.elapsedTicks;
         }

         this.lastTime = replayTime;
         if (time >= this.lastTimestamp) {
            this.settableFuture.set(null);
         }
      }
   }

   public abstract long getTimePassed();
}
