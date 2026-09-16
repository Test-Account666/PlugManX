package velocity.com.rylinaux.plugman.util;

import core.com.rylinaux.plugman.util.ThreadUtil;
import velocity.com.rylinaux.plugman.PlugManVelocity;

import java.time.Duration;
public class VelocityThreadUtil implements ThreadUtil {

    @Override
    public void async(Runnable runnable) {
        PlugManVelocity.getInstance().getServer().getScheduler()
                .buildTask(PlugManVelocity.getInstance(), runnable)
                .schedule();
    }

    @Override
    public void sync(Runnable runnable) {
        // Velocity doesn't have a main thread concept like Bukkit
        // All tasks are essentially async, so we just run it immediately
        async(runnable);
    }

    @Override
    public void asyncLater(Runnable runnable, long delay) {
        PlugManVelocity.getInstance().getServer().getScheduler()
                .buildTask(PlugManVelocity.getInstance(), runnable)
                .delay(Duration.ofMillis(delay))
                .schedule();
    }

    @Override
    public void syncLater(Runnable runnable, long delay) {
        asyncLater(runnable, delay);
    }

    @Override
    public void syncRepeating(Runnable runnable, long delay, long period) {
        PlugManVelocity.getInstance().getServer().getScheduler()
                .buildTask(PlugManVelocity.getInstance(), runnable)
                .delay(Duration.ofMillis(delay))
                .repeat(Duration.ofMillis(period))
                .schedule();
    }

    @Override
    public void asyncRepeating(Runnable runnable, long delay, long period) {
        PlugManVelocity.getInstance().getServer().getScheduler()
                .buildTask(PlugManVelocity.getInstance(), runnable)
                .delay(Duration.ofMillis(delay))
                .repeat(Duration.ofMillis(period))
                .schedule();
    }
}
