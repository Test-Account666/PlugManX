package paper.com.rylinaux.plugman.pluginmanager;

/*-
 * #%L
 * PlugManX Core
 * %%
 * Copyright (C) 2010 - 2025 plugman-core
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
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import core.com.rylinaux.plugman.util.reflection.MethodAccessor;
import io.papermc.paper.plugin.entrypoint.Entrypoint;
import io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler;
import io.papermc.paper.plugin.provider.PluginProvider;
import io.papermc.paper.plugin.storage.SimpleProviderStorage;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;

public class ModernPaperPluginManager extends PaperPluginManager {
    //TODO: Add paper-plugin support

    public ModernPaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        super(bukkitPluginManager);
    }

    @Override
    public PluginResult unload(Plugin plugin) {
        var result = unloadWithPaper(plugin);
        if (!result.result.success()) return result.result;

        var unloadData = setupUnloadData(result.data);
        if (unloadData == null) return new PluginResult(false, "unload.failed");

        cleanupEventExecutors(plugin, unloadData);
        removeFromPluginLists(plugin, unloadData);

        if (!cleanupSafeClassDefiner(plugin)) return new PluginResult(false, "unload.failed");

        scheduleCleanupTask();

        syncCommands();
        closeClassLoader(plugin);
        System.gc();

        return new PluginResult(true, "unload.unloaded");
    }

    @SneakyThrows
    private Object getInstanceManager() {
        var paper = ClassAccessor.getClass("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
        var paperPluginManagerImpl = MethodAccessor.invoke(paper, "getInstance", null);

        return FieldAccessor.getValue(paperPluginManagerImpl.getClass(), "instanceManager", paperPluginManagerImpl);
    }


    private ModernUnloadData setupUnloadData(CommonUnloadData commonData) {
        try {
            var instanceManager = getInstanceManager();

            var lookupNames = FieldAccessor.<Map<String, org.bukkit.plugin.Plugin>>getValue(instanceManager.getClass(), "lookupNames", instanceManager);
            var pluginList = FieldAccessor.<List<org.bukkit.plugin.Plugin>>getValue(instanceManager.getClass(), "plugins", instanceManager);

            var eventExecutorMap = FieldAccessor.<Map<Method, Class<?>>>getValue(EventExecutor.class, "eventExecutorMap", null);

            return new ModernUnloadData(commonData, lookupNames, eventExecutorMap, pluginList);
        } catch (Exception exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to setup unload data", exception);
            return null;
        }
    }

    private void removeFromPluginLists(Plugin plugin, ModernUnloadData data) {
        var instanceManager = getInstanceManager();

        // The plugin can only be removed from the lookup names and the plugin list AFTER the commands are unregistered, to avoid issues with commands created via Paper's Brigadier API
        data.lookupNames.remove(plugin.getName().replace(" ", "_").toLowerCase());
        data.pluginList.removeIf(otherPlugin -> otherPlugin.getName().equalsIgnoreCase(plugin.getName()));

        try {
            FieldAccessor.setValue("lookupNames", instanceManager, data.lookupNames);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        removeFromPluginLists(plugin, data.commonData);
        removeFromProviderStorage(plugin);
    }

    private void removeFromProviderStorage(Plugin plugin) {
        try {
            var storage = getPluginStorage();
            if (storage == null) {
                PlugManBukkit.getInstance().getLogger().warning("Could not get plugin storage for provider removal");
                return;
            }

            var providers = cloneProvidersList(storage);
            removeMatchingProviders(plugin, storage, providers);
        } catch (Exception exception) {
            var message = "Error in removeFromProviderStorage for plugin " + plugin.getName();
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, message, exception);
        }
    }

    private Object getPluginStorage() {
        return LaunchEntryPointHandler.INSTANCE.get(Entrypoint.PLUGIN);
    }

    private List<PluginProvider<JavaPlugin>> cloneProvidersList(Object storage) {
        var providersIterable = ((SimpleProviderStorage) storage).getRegisteredProviders();
        var clonedList = new ArrayList<PluginProvider<JavaPlugin>>();
        for (var provider : providersIterable) clonedList.add((PluginProvider<JavaPlugin>) provider);
        return clonedList;
    }

    private void removeMatchingProviders(Plugin plugin, Object storage, List<PluginProvider<JavaPlugin>> providers) {
        for (var provider : providers) {
            if (!provider.getMeta().getName().equalsIgnoreCase(plugin.getName())) continue;

            try {
                removeProviderFromStorage(plugin, storage, provider);
                cleanupProviderCaches(plugin, storage);
            } catch (Exception exception) {
                var message = "Error removing provider for plugin " + plugin.getName();
                PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, message, exception);
            }
        }
    }

    private void removeProviderFromStorage(Plugin plugin, Object storage, PluginProvider<JavaPlugin> provider) {
        try {
            var providers = FieldAccessor.<List<?>>getValue(SimpleProviderStorage.class, "providers", storage);
            var removed = providers.remove(provider);

            if (removed) {
                var message = "Successfully removed provider for plugin: " + plugin.getName();
                PlugManBukkit.getInstance().getLogger().info(message);
            } else {
                var message = "Failed to remove provider for plugin: " + plugin.getName();
                PlugManBukkit.getInstance().getLogger().warning(message);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove provider from storage", e);
        }
    }

    private void cleanupProviderCaches(Plugin plugin, Object storage) {
        cleanupProviderContext(plugin, storage);
        cleanupProviderIdentifiers(plugin, storage);
    }

    private void cleanupProviderContext(Plugin plugin, Object storage) {
        cleanupMapField(storage, "providerContext",
                entry -> entry.getKey().toString().contains(plugin.getName()),
                "Could not clear provider context for " + plugin.getName());
    }

    private void cleanupProviderIdentifiers(Plugin plugin, Object storage) {
        cleanupMapField(storage, "identifiers",
                entry -> entry.getKey().toString().contains(plugin.getName()) ||
                        entry.getValue().toString().contains(plugin.getName()),
                "Could not clear provider identifiers for " + plugin.getName());
    }

    private void cleanupMapField(Object storage, String fieldName,
                                 Predicate<Map.Entry<?, ?>> removalCondition,
                                 String errorMessage) {
        try {
            var field = FieldAccessor.getField(storage.getClass(), fieldName);
            if (field != null) {
                var map = FieldAccessor.getValue(storage.getClass(), fieldName, storage);
                if (map instanceof Map) ((Map<?, ?>) map).entrySet().removeIf(entry -> {
                    try {
                        return removalCondition.test(entry);
                    } catch (Exception e) {
                        return false;
                    }
                });
            }
        } catch (Exception exception) {
            var message = errorMessage + ": " + exception.getMessage();
            PlugManBukkit.getInstance().getLogger().fine(message);
        }
    }

    private void cleanupEventExecutors(Plugin plugin, ModernUnloadData data) {
        if (data.eventExecutorMap() == null) return;

        var loader = plugin.getHandle().getClass().getClassLoader();
        var iterator = data.eventExecutorMap().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().getDeclaringClass().getClassLoader() == loader)
                synchronized (entry.getKey()) { // paper also synchronizes over the method
                    iterator.remove();
                }
        }
    }

    private boolean cleanupSafeClassDefiner(Plugin plugin) {
        try {
            // Try to unload from SafeClassDefiner
            var cls = ClassAccessor.getClass("com.destroystokyo.paper.event.executor.asm.SafeClassDefiner");
            if (cls == null) return true;

            var instance = FieldAccessor.getValue(cls, "INSTANCE", null);
            var loaders = FieldAccessor.<Map<?, ?>>getValue(instance.getClass(), "loaders", instance);
            loaders.remove(plugin.getHandle().getClass().getClassLoader());
            return true;
        } catch (NoClassDefFoundError ignored) { // ignore this, if SafeClassDefiner doesn't exist
            return true;
        } catch (Exception exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to cleanup SafeClassDefiner for plugin: " + plugin.getName(), exception);
            return false;
        }
    }

    private void scheduleCleanupTask() {
        if (isFolia()) {
            var foliaLib = new com.tcoded.folialib.FoliaLib(PlugManBukkit.getInstance());
            foliaLib.getScheduler().runAsync((task) -> {
                // attempt to do the same thing for folia
            });
        } else new BukkitRunnable() {
            @Override
            public void run() {
                // schedule an empty BukkitRunnable to clear/reset the "head" field in CraftScheduler.
                // that field can keep plugin classes loaded, and scheduling an empty runnable
                // seems nicer and less harmful than clearing that field with reflection
            }
        }.runTask(PlugManBukkit.getInstance());
    }

    private record ModernUnloadData(@Delegate CommonUnloadData commonData, Map<String, org.bukkit.plugin.Plugin> lookupNames,
                                    Map<Method, Class<?>> eventExecutorMap, List<org.bukkit.plugin.Plugin> pluginList) {
    }
}
