package redacted.client.event;

import redacted.client.event.CancellableEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    // call() used to isAssignableFrom-scan the whole listener list for every event -- and that
    // runs per frame, per tick and per packet (Netty thread). Buckets are built lazily per
    // concrete event class from the master list (so priority/registration order is identical)
    // and stamped with the registration version: a module toggle bumps it and stale buckets
    // rebuild on their next dispatch, no locks. A dispatch racing a toggle sees a snapshot,
    // same as iterating the COW list did.
    private final ConcurrentHashMap<Class<?>, Bucket> buckets = new ConcurrentHashMap<Class<?>, Bucket>();
    private volatile long version;

    public void register(final Object object) {
        if (object == null) return;
        this.unregister(object);

        Class<?> type = object.getClass();
        while (type != null) {
            for (final Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(EventTarget.class)) continue;
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || !Event.class.isAssignableFrom(parameterTypes[0])) continue;

                method.setAccessible(true);
                final EventTarget target = method.getAnnotation(EventTarget.class);
                this.addListener(new Listener(object, method, parameterTypes[0], target.priority()));
            }
            type = type.getSuperclass();
        }
        this.invalidate();
    }

    private void addListener(final Listener listener) {
        for (int i = 0; i < this.listeners.size(); i++) {
            if (listener.priority > this.listeners.get(i).priority) {
                this.listeners.add(i, listener);
                return;
            }
        }
        this.listeners.add(listener);
    }

    public void unregister(final Object object) {
        if (object == null) return;

        final Iterator<Listener> iterator = this.listeners.iterator();
        while (iterator.hasNext()) {
            final Listener listener = iterator.next();
            if (listener.source == object) this.listeners.remove(listener);
        }
        this.invalidate();
    }

    public void clear() {
        this.listeners.clear();
        this.invalidate();
    }

    // volatile write publishes the list mutations to dispatch threads before they rebuild
    private void invalidate() {
        this.version++;
        this.buckets.clear();
    }

    public <T extends Event> T call(final T event) {
        if (event == null) return null;

        // read the version before the list -- a toggle racing the build then stamps the bucket
        // stale, so the worst case is one extra rebuild, never a wrong bucket served as fresh
        final long v = this.version;
        Bucket bucket = this.buckets.get(event.getClass());
        if (bucket == null || bucket.version != v) {
            final List<Listener> matched = new ArrayList<Listener>();
            for (final Listener listener : this.listeners) {
                if (listener.eventType.isAssignableFrom(event.getClass())) matched.add(listener);
            }
            bucket = new Bucket(v, matched.toArray(new Listener[0]));
            this.buckets.put(event.getClass(), bucket);
        }

        for (final Listener listener : bucket.targets) {
            try {
                listener.method.invoke(listener.source, event);
            } catch (final IllegalAccessException e) {
                e.printStackTrace();
            } catch (final InvocationTargetException e) {
                e.getCause().printStackTrace();
            }

            if (event instanceof CancellableEvent && ((CancellableEvent) event).isCancelled()) {
                break;
            }
        }

        return event;
    }

    private static final class Listener {
        private final Object source;
        private final Method method;
        private final Class<?> eventType;
        private final int priority;

        private Listener(final Object source, final Method method, final Class<?> eventType, final int priority) {
            this.source = source;
            this.method = method;
            this.eventType = eventType;
            this.priority = priority;
        }
    }

    private static final class Bucket {
        private final long version;
        private final Listener[] targets;

        private Bucket(final long version, final Listener[] targets) {
            this.version = version;
            this.targets = targets;
        }
    }
}
