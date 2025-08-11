package core.com.rylinaux.plugman.util;

public interface ThreadUtil {
    void async(Runnable runnable);

    void sync(Runnable runnable);

    void syncRepeating(Runnable runnable, long delay, long period);

    void asyncRepeating(Runnable runnable, long delay, long period);
}
