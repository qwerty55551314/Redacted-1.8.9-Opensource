package redacted.client.mcp;

import net.minecraft.client.Minecraft;
import redacted.client.event.EventTarget;
import redacted.client.event.impl.EventTick;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Marshals game-state work onto the Minecraft client thread. MCP socket threads enqueue
 * a job and block on a latch; the tick event drains the queue. This mirrors how the
 * reference bridges schedule through the client thread and wait from their worker.
 *
 * <p>Queries may run without this (reads of primitives are tolerated), but every
 * mutation (toggles, value writes, config IO) goes through here.
 */
public final class MainThreadDispatcher {

    private static final long DEFAULT_TIMEOUT_MS = 8000L;

    private static volatile MainThreadDispatcher active;

    private final ConcurrentLinkedQueue<Job<?>> queue = new ConcurrentLinkedQueue<Job<?>>();

    /** The dispatcher registered with the running MCP server, or null when MCP is off. */
    public static MainThreadDispatcher get() {
        return active;
    }

    static void setActive(final MainThreadDispatcher dispatcher) {
        active = dispatcher;
    }

    /**
     * Static convenience for tools: run on the client thread using the registered dispatcher.
     * Throws {@link McpToolException} when MCP is not running.
     */
    public static <T> T dispatch(Callable<T> task) throws Exception {
        final MainThreadDispatcher current = active;
        if (current == null) throw new McpToolException("MCP is not running");
        return current.call(task);
    }

    public <T> T call(Callable<T> task) throws Exception {
        return call(task, DEFAULT_TIMEOUT_MS);
    }

    public <T> T call(Callable<T> task, long timeoutMs) throws Exception {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            return task.call();
        }

        final Job<T> job = new Job<T>(task);
        this.queue.add(job);

        if (!job.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            this.queue.remove(job);
            throw new McpToolException("Timed out waiting for the Minecraft client thread");
        }

        final Throwable error = job.error;
        if (error instanceof Exception) throw (Exception) error;
        if (error instanceof Error) throw (Error) error;
        if (error != null) throw new McpToolException("Client-thread execution failed", error);
        return job.result;
    }

    /** Fire-and-forget -- used for chat feedback from socket threads. */
    public void post(final Runnable task) {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            task.run();
            return;
        }
        this.queue.add(new Job<Object>(new Callable<Object>() {
            @Override
            public Object call() {
                task.run();
                return null;
            }
        }));
    }

    @EventTarget
    public void onTick(final EventTick event) {
        if (!event.isPre()) return;

        Job<?> job;
        while ((job = this.queue.poll()) != null) {
            job.run();
        }
    }

    private static final class Job<T> implements Runnable {
        private final Callable<T> task;
        private final CountDownLatch latch = new CountDownLatch(1);
        private T result;
        private Throwable error;

        private Job(Callable<T> task) {
            this.task = task;
        }

        @Override
        public void run() {
            try {
                this.result = this.task.call();
            } catch (Throwable t) {
                this.error = t;
            } finally {
                this.latch.countDown();
            }
        }
    }
}
