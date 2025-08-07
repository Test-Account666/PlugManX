package paper.com.rylinaux.plugman.pluginmanager;

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

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.plugin.BukkitPlugin;
import bukkit.com.rylinaux.plugman.pluginmanager.BasePluginManager;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import core.com.rylinaux.plugman.util.reflection.MethodAccessor;
import io.papermc.paper.plugin.configuration.PluginMeta;
import lombok.experimental.Delegate;
import manifold.ext.rt.api.auto;
import org.bukkit.command.PluginCommand;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Utilities for managing paper plugins.
 *
 * @author rylinaux
 */
public class PaperPluginManager extends BasePluginManager {
    @Delegate
    private final BukkitPluginManager _bukkitPluginManager;

    public PaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        _bukkitPluginManager = bukkitPluginManager;

        try {
            var pluginClassLoader = ClassAccessor.getClass("org.bukkit.plugin.java.PluginClassLoader");
            if (pluginClassLoader == null) throw new ClassNotFoundException("PluginClassLoader not found");
            var pluginClassLoaderPlugin = FieldAccessor.getField(pluginClassLoader, "plugin");
            if (pluginClassLoaderPlugin == null) throw new NoSuchFieldException("plugin field not found");
        } catch (ClassNotFoundException | NoSuchFieldException exception) {
            throw new RuntimeException(exception);
        }
    }

    public boolean isPaperPlugin(File file) {
        if (file == null) return false;

        JarFile jar = null;

        try {
            jar = new JarFile(file);
            var entry = jar.getJarEntry("paper-plugin.yml");

            return entry != null;
        } catch (IOException | YAMLException ex) {
            return false;
        } finally {
            if (jar != null) try {
                jar.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        try {
            var launchEntryPointHandlerClass = ClassAccessor.getClass("io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler");
            if (launchEntryPointHandlerClass == null) return false;

            var instance = FieldAccessor.getValue(launchEntryPointHandlerClass, "INSTANCE", null);

            var getMethod = MethodAccessor.findMethodByName(instance.getClass(), "get");

            if (getMethod == null) return false;

            var entrypointClass = ClassAccessor.getClass("io.papermc.paper.plugin.entrypoint.Entrypoint");
            if (entrypointClass == null) return false;

            var pluginFieldValue = FieldAccessor.getValue(entrypointClass, "PLUGIN", null);

            var providerStorage = getMethod.invoke(instance, pluginFieldValue);

            if (providerStorage == null) return false;

            var providers = MethodAccessor.<List<?>>invoke(ClassAccessor.getClass("io.papermc.paper.plugin.storage.SimpleProviderStorage"),
                    "getRegisteredProviders", providerStorage);

            for (var provider : providers)
                try {
                    var meta = MethodAccessor.<PluginMeta>invoke(provider.getClass(), "getMeta", provider);
                    if (!meta.getName().equalsIgnoreCase(plugin.getName())) continue;

                    return ClassAccessor.assignableFrom("io.papermc.paper.plugin.provider.type.paper.PaperPluginParent$PaperServerPluginProvider", provider.getClass());
                } catch (Throwable ignored) {
                    return false;
                }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return false;
    }

    public boolean isFolia() {
        return ClassAccessor.classExists("io.papermc.paper.threadedregions.RegionizedServer");
    }

    /**
     * Loads and enables a plugin.
     *
     * @param name plugin's name
     * @return status message
     */
    @Override
    public PluginResult load(String name) {
        var pluginFile = findPluginFile(name);
        if (pluginFile == null) return new PluginResult(false, "load.cannot-find");

        var validationResult = validatePluginFile(pluginFile);
        if (!validationResult.success()) return validationResult;

        PlugManBukkit.getInstance().getLogger().info("Attempting to load " + pluginFile.getPath());

        var target = loadPluginWithPaper(pluginFile);
        if (target == null) {
            target = loadAndEnablePlugin(pluginFile);
            if (target == null) return new PluginResult(false, "load.invalid-plugin");
        }

        scheduleCommandLoading();
        PlugManBukkit.getInstance().getFilePluginMap().put(pluginFile.getName(), target.getName());

        return new PluginResult(true, "load.loaded");
    }


    private PluginResult validatePluginFile(File pluginFile) {
        var pluginDir = new File("plugins");
        if (!pluginDir.isDirectory()) return new PluginResult(false, "load.plugin-directory");

        if (!pluginFile.isFile()) return new PluginResult(false, "load.cannot-find");

        if (isPaperPlugin(pluginFile)) return new PluginResult(false, "error.paper-plugin");

        return new PluginResult(true, "validation.success");
    }

    private Plugin loadPluginWithPaper(File pluginFile) {
        try {
            var paper = ClassAccessor.getClass("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            if (paper == null) return null;

            var paperPluginManagerImpl = MethodAccessor.invoke(paper, "getInstance", null);

            var instanceManager = FieldAccessor.getValue(paperPluginManagerImpl.getClass(), "instanceManager", paperPluginManagerImpl);

            var target = MethodAccessor.<org.bukkit.plugin.Plugin>invoke(instanceManager.getClass(), "loadPlugin", instanceManager, new Class<?>[]{Path.class}, pluginFile.toPath());

            MethodAccessor.invoke(instanceManager.getClass(), "enablePlugin", instanceManager, new Class<?>[]{org.bukkit.plugin.Plugin.class}, target);

            return new BukkitPlugin(target);
        } catch (Exception ignore) {
            // Paper most likely not loaded
            return null;
        }
    }


    @Override
    protected void scheduleCommandLoading() {
        if (isFolia()) {
            var foliaLib = new com.tcoded.folialib.FoliaLib(PlugManBukkit.getInstance());
            foliaLib.getScheduler().runLater(this::syncCommands, 500, TimeUnit.MILLISECONDS);
        } else super.scheduleCommandLoading();
    }


    /**
     * Unload a plugin.
     *
     * @param plugin the plugin to unload
     * @return the message to send to the user.
     */
    @Override
    public PluginResult unload(Plugin plugin) {
        var out = unloadWithPaper(plugin);
        if (!out.result.success()) return out.result;

        closeClassLoader(plugin);
        cleanupPaperPluginManager(plugin);
        System.gc();

        return new PluginResult(true, "unload.unloaded");
    }

    public auto unloadWithPaper(Plugin plugin) {
        if (!handleGentleUnload(plugin)) return data:(CommonUnloadData) null, result:new PluginResult(false, "unload.gentle-failed");

        var unloadData = setupUnloadData(plugin);
        if (unloadData == null) return data:unloadData, result:new PluginResult(false, "unload.failed");

        cleanupListeners(plugin, unloadData);
        cleanupCommands(plugin, unloadData);
        removeFromPluginLists(plugin, unloadData);

        return data:unloadData, result:new PluginResult(true, "unload.common-success");
    }


    private CommonUnloadData setupUnloadData(Plugin plugin) {
        syncCommands();
        return _bukkitPluginManager.extractPluginManagerData(plugin);
    }

    private void cleanupListeners(Plugin plugin, CommonUnloadData data) {
        if (data.listeners() == null || !data.reloadListeners()) return;
        data.listeners().values().forEach(set -> set.removeIf(value -> value.getPlugin() == plugin.getHandle()));
    }

    private void cleanupCommands(Plugin plugin, CommonUnloadData data) {
        if (data.commandMap() == null) return;

        var modifiedKnownCommands = new HashMap<>(data.commands());

        for (var entry : new HashMap<>(data.commands()).entrySet()) {
            if (entry.getValue() instanceof PluginCommand command) {
                if (command.getPlugin() == plugin.getHandle()) {
                    command.unregister(data.commandMap());
                    modifiedKnownCommands.remove(entry.getKey());
                }
                continue;
            }

            try {
                unregisterNonPluginCommands(plugin, data.commandMap(), modifiedKnownCommands, entry);
            } catch (IllegalStateException exception) {
                if (exception.getMessage().equalsIgnoreCase("zip file closed")) {
                    var config = PlugManBukkit.getInstance().<PlugManConfigurationManager>get(PlugManConfigurationManager.class);
                    if (config.shouldNotifyOnBrokenCommandRemoval())
                        Logger.getLogger(PaperPluginManager.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
                    entry.getValue().unregister(data.commandMap());
                    modifiedKnownCommands.remove(entry.getKey());
                }
            }
        }

        var plugManCommands = convertBukkitCommands(modifiedKnownCommands);
        setKnownCommands(plugManCommands);
    }

    private void cleanupPaperPluginManager(Plugin plugin) {
        try {
            var paper = ClassAccessor.getClass("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            if (paper == null) return;

            var paperPluginManagerImpl = MethodAccessor.invoke(paper, "getInstance", null);

            var instanceManager = FieldAccessor.getValue(paperPluginManagerImpl.getClass(), "instanceManager", paperPluginManagerImpl);

            var lookupNames = FieldAccessor.<Map<String, org.bukkit.plugin.Plugin>>getValue(instanceManager.getClass(), "lookupNames", instanceManager);

            MethodAccessor.invoke(instanceManager.getClass(), "disablePlugin", instanceManager, new Class<?>[]{org.bukkit.plugin.Plugin.class}, plugin);

            lookupNames.remove(plugin.getName().toLowerCase());

            var pluginList = FieldAccessor.<List<org.bukkit.plugin.Plugin>>getValue(instanceManager.getClass(), "plugins", instanceManager);
            pluginList.remove(plugin.<org.bukkit.plugin.Plugin>getHandle());
        } catch (Exception ignore) {
            // Paper most likely not loaded
        }
    }
}