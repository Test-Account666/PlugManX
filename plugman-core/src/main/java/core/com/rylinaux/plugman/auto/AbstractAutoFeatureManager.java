package core.com.rylinaux.plugman.auto;

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

import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.file.PlugManFileManager;
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.io.File;
import java.util.HashSet;

/**
 * Abstract base implementation for auto-load, auto-unload, and auto-reload features.
 * Contains platform-independent logic that can be reused across different platforms.
 * Uses ServiceRegistry for dependency injection.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public abstract class AbstractAutoFeatureManager implements AutoFeatureManager {
    @Delegate

    protected final ServiceRegistry serviceRegistry;

    private boolean warningShown = false;
    private PlugManFileManager fileManager;

    protected PlugManFileManager getFileManager() {
        if (fileManager != null) return fileManager;

        fileManager = new PlugManFileManager(getLogger());
        return fileManager;
    }

    protected ThreadUtil getThreadUtil() {
        return serviceRegistry.get(ThreadUtil.class);
    }

    protected PlugManConfigurationManager getConfigurationManager() {
        return serviceRegistry.get(PlugManConfigurationManager.class);
    }

    protected PluginLogger getLogger() {
        return serviceRegistry.get(PluginLogger.class);
    }

    @Override
    public void setupAutoFeatures() {
        var config = getConfigurationManager().getPlugManConfig();

        if (config.getAutoLoad().isEnabled()) {
            showAutoFeatureWarningIfNeeded();
            setupAutoLoad();
        }

        if (config.getAutoUnload().isEnabled()) {
            showAutoFeatureWarningIfNeeded();
            setupAutoUnload();
        }

        if (config.getAutoReload().isEnabled()) {
            showAutoFeatureWarningIfNeeded();
            setupAutoReload();
        }
    }

    protected void showAutoFeatureWarningIfNeeded() {
        if (warningShown) return;
        getLogger().warning("!!! The auto (re/un)load feature can break plugins, use with caution !!!");
        getLogger().warning("If anything breaks, a restart will probably fix it!");
        warningShown = true;
    }

    protected void setupAutoLoad() {
        var config = getConfigurationManager().getPlugManConfig();
        var interval = config.getAutoLoad().getCheckEverySeconds() * 1000; // Convert to milliseconds for platform compatibility
        getThreadUtil().asyncRepeating(() -> {
            if (!new File("plugins").isDirectory()) return;

            for (var file : getFileManager().getJarFiles()) {
                if (getFileManager().isFileTracked(file.getName())) continue;
                getThreadUtil().sync(() -> {
                    var pluginName = file.getName().replace(".jar", "");
                    var message = getPluginManager().load(pluginName);
                    getLogger().info(getMessageFormatter().formatMessage(message.messageId(), pluginName));
                });

                getFileManager().trackFile(file);
            }
        }, interval, interval);
    }

    protected void setupAutoUnload() {
        var config = getConfigurationManager().getPlugManConfig();
        var interval = config.getAutoUnload().getCheckEverySeconds() * 1000; // Convert to milliseconds for platform compatibility
        getThreadUtil().asyncRepeating(() -> {
            if (!new File("plugins").isDirectory()) return;

            for (var fileName : new HashSet<>(getFileManager().getTrackedFileNames())) {
                if (new File("plugins", fileName).exists()) continue;

                var pluginName = getFileManager().getPluginNameForFile(fileName);
                if (pluginName == null) {
                    getFileManager().untrackFile(fileName);
                    continue;
                }

                var pluginInstance = getPluginManager().getPluginByName(pluginName);
                if (pluginInstance == null) {
                    getFileManager().untrackFile(fileName);
                    continue;
                }

                if (getPluginManager().isIgnored(pluginInstance)) continue;

                getFileManager().untrackFile(fileName);
                getThreadUtil().sync(() -> {
                    var message = getPluginManager().unload(pluginInstance);
                    getLogger().info(getMessageFormatter().formatMessage(message.messageId(), pluginInstance.getName()));
                });
            }
        }, interval, interval);
    }

    protected void setupAutoReload() {
        var config = getConfigurationManager().getPlugManConfig();
        var interval = config.getAutoReload().getCheckEverySeconds() * 1000; // Convert to milliseconds for platform compatibility
        getThreadUtil().asyncRepeating(() -> {
            if (!new File("plugins").isDirectory()) return;

            for (var file : getFileManager().getJarFiles()) {
                if (!getFileManager().isFileTracked(file.getName())) continue;

                if (getFileManager().hasFileChanged(file)) {
                    var pluginName = getFileManager().getPluginNameForFile(file.getName());
                    if (pluginName == null) {
                        getFileManager().untrackFile(file.getName());
                        continue;
                    }

                    var pluginInstance = getPluginManager().getPluginByName(pluginName);
                    if (pluginInstance == null) {
                        getFileManager().untrackFile(file.getName());
                        continue;
                    }

                    if (getPluginManager().isIgnored(pluginInstance)) continue;

                    getFileManager().updateFileHash(file);

                    getThreadUtil().sync(() -> {
                        var unloadMessage = getPluginManager().unload(pluginInstance);
                        getLogger().info(getMessageFormatter().formatMessage(unloadMessage.messageId(), pluginInstance.getName()));

                        if (!unloadMessage.success()) return;

                        var loadMessage = getPluginManager().load(pluginInstance.getName());
                        getLogger().info(getMessageFormatter().formatMessage(loadMessage.messageId(), pluginInstance.getName()));
                    });
                }
            }
        }, interval, interval);
    }
}