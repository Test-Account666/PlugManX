package core.com.rylinaux.plugman.util;

public interface ThreadUtil {
    void async(Runnable runnable);

    void sync(Runnable runnable);

    /**
     * Runs a task after the given delay in milliseconds.
     */
    void asyncLater(Runnable runnable, long delay);

    /**
     * Runs a task on the platform main thread after the given delay in milliseconds.
     */
    void syncLater(Runnable runnable, long delay);

    /**
     * Runs a repeating task with delay and period in milliseconds.
     */
    void syncRepeating(Runnable runnable, long delay, long period);

    /**
     * Runs a repeating async task with delay and period in milliseconds.
     */
    void asyncRepeating(Runnable runnable, long delay, long period);
}
