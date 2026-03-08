package core.com.rylinaux.plugman.util;

public interface ThreadUtil {
    void async(Runnable runnable);

    void sync(Runnable runnable);

    void asyncLater(Runnable runnable, long delay);

    void syncLater(Runnable runnable, long delay);

    void syncRepeating(Runnable runnable, long delay, long period);

    void asyncRepeating(Runnable runnable, long delay, long period);
}
