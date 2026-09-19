package com.replaymod.render.utils;

import com.google.common.base.Preconditions;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class JailingQueue<T> extends AbstractQueue<T> implements BlockingQueue<T> {
   private final BlockingQueue<T> delegate;
   private final Set<Thread> jailed = new HashSet<>();

   public JailingQueue(BlockingQueue<T> delegate) {
      this.delegate = delegate;
   }

   public synchronized void jail(int atLeast) {
      while (this.jailed.size() < atLeast) {
         try {
            this.wait();
         } catch (InterruptedException e) {
            Thread.interrupted();
         }
      }
   }

   public synchronized void free(Thread thread) {
      Preconditions.checkState(this.jailed.remove(thread), "Thread is not jailed.");
      thread.interrupt();
   }

   public synchronized void freeAll() {
      this.jailed.clear();
      this.notifyAll();
   }

   private synchronized void tryAccess() {
      this.jailed.add(Thread.currentThread());
      this.notifyAll();

      while (this.jailed.contains(Thread.currentThread())) {
         try {
            this.wait();
         } catch (InterruptedException e) {
            Thread.interrupted();
         }
      }
   }

   @Override
   public Iterator<T> iterator() {
      this.tryAccess();
      return this.delegate.iterator();
   }

   @Override
   public int size() {
      this.tryAccess();
      return this.delegate.size();
   }

   @Override
   public void put(T t) throws InterruptedException {
      this.tryAccess();
      this.delegate.put(t);
   }

   @Override
   public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
      this.tryAccess();
      return this.delegate.offer(t, timeout, unit);
   }

   @Override
   public T take() throws InterruptedException {
      this.tryAccess();
      return this.delegate.take();
   }

   @Override
   public T poll(long timeout, TimeUnit unit) throws InterruptedException {
      this.tryAccess();
      return this.delegate.poll(timeout, unit);
   }

   @Override
   public int remainingCapacity() {
      this.tryAccess();
      return this.delegate.remainingCapacity();
   }

   @Override
   public int drainTo(Collection<? super T> c) {
      this.tryAccess();
      return this.delegate.drainTo(c);
   }

   @Override
   public int drainTo(Collection<? super T> c, int maxElements) {
      this.tryAccess();
      return this.delegate.drainTo(c, maxElements);
   }

   @Override
   public boolean offer(T t) {
      this.tryAccess();
      return this.delegate.offer(t);
   }

   @Override
   public T poll() {
      this.tryAccess();
      return this.delegate.poll();
   }

   @Override
   public T peek() {
      this.tryAccess();
      return this.delegate.peek();
   }
}
