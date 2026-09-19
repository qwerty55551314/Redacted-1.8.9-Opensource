package net.minecraft.util;

public abstract class LazyLoadBase<T>
{
    private T value;
    private boolean isLoaded = false;

    public T getValue()
    {
        // Thread-safe: the server-list pinger and the connect path can race on
        // the first load. Without synchronization one caller could observe the
        // value before the other's load() finished (a null EventLoopGroup ->
        // "group" NPE in Bootstrap). A failed load is also retried instead of
        // poisoning the lazy value forever.
        synchronized (this)
        {
            if (!this.isLoaded)
            {
                this.isLoaded = true;
                try
                {
                    this.value = this.load();
                }
                catch (Throwable t)
                {
                    this.isLoaded = false;
                    throw t;
                }
            }

            return this.value;
        }
    }

    protected abstract T load();
}
