package bungee.com.rylinaux.plugman.util;

import bungee.com.rylinaux.plugman.PlugManBungee;
import core.com.rylinaux.plugman.util.ThreadUtil;
import net.md_5.bungee.api.ProxyServer;

import java.util.concurrent.TimeUnit;

public class BungeeThreadUtil implements ThreadUtil {

    @Override
    public void async(Runnable runnable) {
        ProxyServer.getInstance().getScheduler().runAsync(PlugManBungee.getInstance(), runnable);
    }

    @Override
    public void sync(Runnable runnable) {
        ProxyServer.getInstance().getScheduler().schedule(PlugManBungee.getInstance(), runnable, 0L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void syncRepeating(Runnable runnable, long delay, long period) {
        ProxyServer.getInstance().getScheduler().schedule(PlugManBungee.getInstance(), runnable, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void asyncRepeating(Runnable runnable, long delay, long period) {
        var scheduler = ProxyServer.getInstance().getScheduler();

        scheduler.schedule(PlugManBungee.getInstance(), () -> scheduler.runAsync(PlugManBungee.getInstance(), runnable), delay, period, TimeUnit.MILLISECONDS);
    }
}
