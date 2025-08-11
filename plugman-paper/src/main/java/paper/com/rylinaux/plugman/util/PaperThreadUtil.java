package paper.com.rylinaux.plugman.util;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2015 PlugMan
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

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.util.BukkitThreadUtil;
import paper.com.rylinaux.plugman.pluginmanager.ModernPaperPluginManager;

import java.util.concurrent.TimeUnit;

/**
 * Paper-specific utility class for threading with Folia support.
 *
 * @author rylinaux
 */
public class PaperThreadUtil extends BukkitThreadUtil {

    /**
     * Check if we should use Folia scheduling for the given plugin manager
     */
    private boolean shouldUseFolia() {
        var pluginManager = PlugManBukkit.getInstance().getPluginManager();

        return pluginManager instanceof ModernPaperPluginManager paperPluginManager && paperPluginManager.isFolia();
    }

    /**
     * Run a task in a separate thread with Folia support.
     */
    @Override
    public void async(Runnable runnable) {
        if (!shouldUseFolia()) {
            super.async(runnable);
            return;
        }

        var foliaLib = new com.tcoded.folialib.FoliaLib(PlugManBukkit.getInstance());
        foliaLib.getScheduler().runAsync((ignored) -> runnable.run());
    }

    /**
     * Run a task in the main thread with Folia support.
     */
    @Override
    public void sync(Runnable runnable) {
        if (!shouldUseFolia()) {
            super.sync(runnable);
            return;
        }

        var foliaLib = new com.tcoded.folialib.FoliaLib(PlugManBukkit.getInstance());
        foliaLib.getScheduler().runLater(runnable, 0L);
    }

    /**
     * Run a repeating sync task with Folia support.
     */
    @Override
    public void syncRepeating(Runnable runnable, long delay, long period) {
        if (!shouldUseFolia()) {
            super.syncRepeating(runnable, delay, period);
            return;
        }

        var foliaLib = new com.tcoded.folialib.FoliaLib(PlugManBukkit.getInstance());
        foliaLib.getScheduler().runTimer(runnable, delay, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Run a repeating async task with Folia support.
     */
    @Override
    public void asyncRepeating(Runnable runnable, long delay, long period) {
        if (!shouldUseFolia()) {
            super.asyncRepeating(runnable, delay, period);
            return;
        }

        var foliaLib = new com.tcoded.folialib.FoliaLib(PlugManBukkit.getInstance());
        foliaLib.getScheduler().runTimerAsync(runnable, delay, period, TimeUnit.MILLISECONDS);
    }
}