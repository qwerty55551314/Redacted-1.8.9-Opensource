package com.replaymod.core.versions.scheduler;

import com.google.common.util.concurrent.ListenableFutureTask;
import com.replaymod.core.mixin.MinecraftAccessor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ReportedException;

public class SchedulerImpl implements Scheduler {
   private static final Minecraft mc = Minecraft.getMinecraft();
   private static SchedulerImpl INSTANCE;
   private boolean inRunLater = false;
   /* 
    * Tasks deferred from inside a scheduled task.
    * Minecraft drains scheduledTasks with `while (!isEmpty()) poll().run();` while holding the lock, so re-adding to that queue from inside a task would make the drain loop run forever.
    * Deferred tasks are parked here and flushed from Minecraft.runGameLoop AFTER the main drain has fully completed.
    */
   private final Queue<Runnable> pendingTasks = new ArrayDeque<>();

   public SchedulerImpl() {
      INSTANCE = this;
   }

   public static void flushPendingTasks() {
      SchedulerImpl scheduler = INSTANCE;
      if (scheduler == null) return;
      while (true) {
         Runnable next;
         synchronized (scheduler.pendingTasks) {
            next = scheduler.pendingTasks.poll();
         }
         if (next == null) break;
         try {
            next.run();
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }
   }

   @Override
   public void runSync(Runnable runnable) throws InterruptedException, ExecutionException, TimeoutException {
      if (mc.isCallingFromMinecraftThread()) {
         runnable.run();
      } else {
         FutureTask<Void> future = new FutureTask<>(runnable, null);
         this.runLater(future);
         future.get(30L, TimeUnit.SECONDS);
      }
   }

   @Override
   public void runPostStartup(Runnable runnable) {
      this.runLater(runnable);
   }

   @Override
   public void runLaterWithoutLock(Runnable runnable) {
      this.runLater(() -> this.runLaterWithoutLock(runnable), runnable);
   }

   @Override
   public void runLater(Runnable runnable) {
      this.runLater(runnable, () -> this.runLater(runnable));
   }

   private void runLater(Runnable runnable, Runnable defer) {
      if (mc.isCallingFromMinecraftThread() && this.inRunLater) {
         /* 
          * Called from inside a task that the game is currently draining.
          * Do NOT touch scheduledTasks here (infinite drain); park the defer and flush it after the drain loop finishes.
          */
         synchronized (this.pendingTasks) {
            this.pendingTasks.add(defer);
         }
      } else {
         Queue<FutureTask<?>> tasks = ((MinecraftAccessor)mc).getScheduledTasks();
         synchronized (tasks) {
            tasks.add(ListenableFutureTask.create(() -> {
               this.inRunLater = true;

               try {
                  runnable.run();
               } catch (ReportedException e) {
                  e.printStackTrace();
                  System.err.println(e.getCrashReport().getCompleteReport());
                  mc.crashed(e.getCrashReport());
               } finally {
                  this.inRunLater = false;
               }
            }, null));
         }
      }
   }

   @Override
   public void runTasks() {
   }
}
