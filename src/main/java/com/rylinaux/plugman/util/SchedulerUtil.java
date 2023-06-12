package com.rylinaux.plugman.util;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.rylinaux.plugman.PlugMan;
import org.bukkit.Bukkit;

public class SchedulerUtil {

    /**
     * @return server software is Folia
     */
    public static boolean isFoliaServer() {
        try {
            Bukkit.getAsyncScheduler();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Run a task in a separate thread.
     *
     * @param runnable the task.
     */
    public static void runAsync(Runnable runnable) {
        if (isFoliaServer()) {
            Bukkit.getAsyncScheduler().runNow(PlugMan.getInstance(), task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(PlugMan.getInstance(), runnable);
        }
    }

    public static void runAsyncTimer(Runnable runnable, long delay, long interval) {
        if (isFoliaServer()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(PlugMan.getInstance(), task -> runnable.run(), delay, interval);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(PlugMan.getInstance(), runnable, delay, interval);
        }
    }

    /**
     * Run a task in the main thread.
     *
     * @param runnable the task.
     */
    public static void runSync(Runnable runnable) {
        if (isFoliaServer()) {
            Bukkit.getGlobalRegionScheduler().run(PlugMan.getInstance(), task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(PlugMan.getInstance(), runnable);
        }
    }

    public static void runSyncTaskLater(Runnable runnable, long delay) {
        if (isFoliaServer()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(PlugMan.getInstance(), task -> runnable.run(), delay);
        } else {
            Bukkit.getScheduler().runTaskLater(PlugMan.getInstance(), task -> runnable.run(), delay);
        }
    }


}
