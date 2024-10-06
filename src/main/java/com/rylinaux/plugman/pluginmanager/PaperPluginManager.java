package com.rylinaux.plugman.pluginmanager;

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
import com.rylinaux.plugman.api.GentleUnload;
import com.rylinaux.plugman.api.PlugManAPI;
import com.rylinaux.plugman.api.event.PreLoadPluginEvent;
import com.rylinaux.plugman.api.event.PreReloadPluginEvent;
import com.rylinaux.plugman.api.event.PreUnloadPluginEvent;
import com.rylinaux.plugman.util.BukkitCommandWrapUseless;
import com.rylinaux.plugman.util.StringUtil;
import com.tcoded.folialib.FoliaLib;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilities for managing paper plugins.
 *
 * @author rylinaux
 */
public class PaperPluginManager implements PluginManager {
    //TODO: Clean this class up, I don't like how it currently looks

    private final BukkitPluginManager _bukkitPluginManager;

    public PaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        this._bukkitPluginManager = bukkitPluginManager;

        try {
            Class<?> pluginClassLoader = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
            Field pluginClassLoaderPlugin = pluginClassLoader.getDeclaredField("plugin");
            pluginClassLoaderPlugin.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    //TODO: Make it look better
    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        try {
            Field instanceField = Class.forName("io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler").getField("INSTANCE");

            instanceField.setAccessible(true);

            Object instance = instanceField.get(null);

            Method getMethod = Arrays.stream(instance.getClass().getDeclaredMethods())
                    .filter(method -> method.getName().equals("get"))
                    .findFirst()
                    .orElse(null);

            if (getMethod == null)
                return false;

            Field pluginField = Class.forName("io.papermc.paper.plugin.entrypoint.Entrypoint").getDeclaredField("PLUGIN");

            Object providerStorage = getMethod.invoke(instance, pluginField.get(null));

            if (providerStorage == null)
                return false;

            Method getRegisteredProvidersMethod = providerStorage.getClass().getMethod("getRegisteredProviders");


            List providers = (List) getRegisteredProvidersMethod.invoke(providerStorage);

            for (Object provider : providers)
                try {
                    Method getMetaMethod = provider.getClass().getMethod("getMeta");

                    PluginMeta configuration = (PluginMeta) getMetaMethod.invoke(provider);

                    if (!configuration.getName().equalsIgnoreCase(plugin.getName()))
                        continue;

                    return Class.forName("io.papermc.paper.plugin.provider.type.paper.PaperPluginParent$PaperServerPluginProvider").isAssignableFrom(provider.getClass());
                } catch (Throwable ignored) {
                    return false;
                }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return false;
    }

    /**
     * Enable a plugin.
     * Currently unsupported, probably needs fixing
     *
     * @param plugin the plugin to enable
     */
    @Override
    public void enable(Plugin plugin) {
        this._bukkitPluginManager.enable(plugin);
    }

    /**
     * Enable all plugins.
     * Currently unsupported, probably needs fixing
     */
    @Override
    public void enableAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!this.isIgnored(plugin) && !this.isPaperPlugin(plugin))
                this.enable(plugin);
    }

    /**
     * Disable a plugin.
     * Currently unsupported, probably needs fixing
     *
     * @param plugin the plugin to disable
     */
    @Override
    public void disable(Plugin plugin) {
        this._bukkitPluginManager.disable(plugin);
    }

    /**
     * Disable all plugins.
     * Currently unsupported, probably needs fixing
     */
    @Override
    public void disableAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!this.isIgnored(plugin) && !this.isPaperPlugin(plugin))
                this.disable(plugin);
    }

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin the plugin to format
     * @return the formatted name
     */
    @Override
    public String getFormattedName(Plugin plugin) {
        return this.getFormattedName(plugin, false);
    }

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin          the plugin to format
     * @param includeVersions whether to include the version
     * @return the formatted name
     */
    @Override
    public String getFormattedName(Plugin plugin, boolean includeVersions) {
        return this._bukkitPluginManager.getFormattedName(plugin, includeVersions);
    }

    /**
     * Returns a plugin from an array of Strings.
     *
     * @param args  the array
     * @param start the index to start at
     * @return the plugin
     */
    @Override
    public Plugin getPluginByName(String[] args, int start) {
        return this.getPluginByName(StringUtil.consolidateStrings(args, start));
    }

    /**
     * Returns a plugin from a String.
     *
     * @param name the name of the plugin
     * @return the plugin
     */
    @Override
    public Plugin getPluginByName(String name) {
        return this._bukkitPluginManager.getPluginByName(name);
    }

    /**
     * Returns a List of plugin names.
     *
     * @return list of plugin names
     */
    @Override
    public List<String> getPluginNames(boolean fullName) {
        return this._bukkitPluginManager.getPluginNames(fullName);
    }

    /**
     * Returns a List of disabled plugin names.
     *
     * @return list of disabled plugin names
     */
    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        return this._bukkitPluginManager.getDisabledPluginNames(fullName);
    }

    /**
     * Returns a List of enabled plugin names.
     *
     * @return list of enabled plugin names
     */
    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        return this._bukkitPluginManager.getEnabledPluginNames(fullName);
    }

    /**
     * Get the version of another plugin.
     *
     * @param name the name of the other plugin.
     * @return the version.
     */
    @Override
    public String getPluginVersion(String name) {
        return this._bukkitPluginManager.getPluginVersion(name);
    }

    /**
     * Returns the commands a plugin has registered.
     * Currently unsupported, probably needs fixing
     *
     * @param plugin the plugin to deal with
     * @return the commands registered
     */
    @Override
    public String getUsages(Plugin plugin) {
        return this._bukkitPluginManager.getUsages(plugin);
    }

    /**
     * Find which plugin has a given command registered.
     * Currently unsupported, probably needs fixing
     *
     * @param command the command.
     * @return the plugin.
     */
    @Override
    public List<String> findByCommand(String command) {
        return this._bukkitPluginManager.findByCommand(command);
    }

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    @Override
    public boolean isIgnored(Plugin plugin) {
        return this.isIgnored(plugin.getName());
    }

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    @Override
    public boolean isIgnored(String plugin) {
        return this._bukkitPluginManager.isIgnored(plugin);
    }

    public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        if (file == null)
            throw new InvalidDescriptionException("File cannot be null");

        JarFile jar = null;
        InputStream stream = null;

        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry("plugin.yml");

            if (entry == null)
                throw new InvalidDescriptionException(new FileNotFoundException("Jar does not contain plugin.yml"));

            stream = jar.getInputStream(entry);

            return new PluginDescriptionFile(stream);

        } catch (IOException | YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        } finally {
            if (jar != null) try {
                jar.close();
            } catch (IOException ignored) {
            }
            if (stream != null) try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Loads and enables a plugin.
     *
     * @param plugin plugin to load
     * @return status message
     */
    private String load(Plugin plugin) {
        return this.load(plugin.getName());
    }

    /**
     * Loads and enables a plugin.
     *
     * @param name plugin's name
     * @return status message
     */
    @Override
    public String load(String name) {
        Plugin target = null;
        boolean paperLoaded = false;

        File pluginDir = new File("plugins");

        if (!pluginDir.isDirectory())
            return PlugMan.getInstance().getMessageFormatter().format("load.plugin-directory");

        File pluginFile = new File(pluginDir, name + ".jar");
        PluginDescriptionFile description = null;

        if (!pluginFile.isFile())
            for (File f : pluginDir.listFiles())
                if (f.getName().endsWith(".jar")) try {
                    PluginDescriptionFile desc = this.getPluginDescription(f);
                    if (desc.getName().equalsIgnoreCase(name)) {
                        pluginFile = f;
                        description = desc;
                        break;
                    }
                } catch (InvalidDescriptionException e) {
                    return PlugMan.getInstance().getMessageFormatter().format("load.cannot-find");
                }

        if (description == null) {
            try {
                description = this.getPluginDescription(pluginFile);
            } catch (InvalidDescriptionException e) {
                return PlugMan.getInstance().getMessageFormatter().format("load.cannot-find");
            }
        }

        PreLoadPluginEvent preloadEvent = new PreLoadPluginEvent(pluginFile.toPath(), description);
        Bukkit.getPluginManager().callEvent(preloadEvent);
        if (preloadEvent.isCancelled())
            return null;

        try {
            Class paper = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            Object paperPluginManagerImpl = paper.getMethod("getInstance").invoke(null);

            Field instanceManagerF = paperPluginManagerImpl.getClass().getDeclaredField("instanceManager");
            instanceManagerF.setAccessible(true);
            Object instanceManager = instanceManagerF.get(paperPluginManagerImpl);

            Method loadMethod = instanceManager.getClass().getMethod("loadPlugin", Path.class);
            loadMethod.setAccessible(true);
            target = (Plugin) loadMethod.invoke(instanceManager, pluginFile.toPath());

            Method enableMethod = instanceManager.getClass().getMethod("enablePlugin", Plugin.class);
            enableMethod.setAccessible(true);
            enableMethod.invoke(instanceManager, target);

            paperLoaded = true;
        } catch (Exception ignore) {
        } // Paper most likely not loaded

        if (!paperLoaded) {
            try {
                target = Bukkit.getPluginManager().loadPlugin(pluginFile);
            } catch (InvalidDescriptionException e) {
                e.printStackTrace();
                return PlugMan.getInstance().getMessageFormatter().format("load.invalid-description");
            } catch (InvalidPluginException e) {
                e.printStackTrace();
                return PlugMan.getInstance().getMessageFormatter().format("load.invalid-plugin");
            }

            target.onLoad();
            Bukkit.getPluginManager().enablePlugin(target);
        }

        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrapUseless)) {
            Plugin finalTarget = target;

            if (this.isFolia()) {
                FoliaLib foliaLib = new FoliaLib(PlugMan.getInstance());

                foliaLib.getImpl().runLater(() -> {
                    this.loadCommands(finalTarget);
                }, 500, TimeUnit.MILLISECONDS);
            } else Bukkit.getScheduler().runTaskLater(PlugMan.getInstance(), () -> {
                this.loadCommands(finalTarget);
            }, 10L);

            PlugMan.getInstance().getFilePluginMap().put(pluginFile.getName(), target.getName());
        }

        return PlugMan.getInstance().getMessageFormatter().format("load.loaded", target.getName());
    }

    private void loadCommands(Plugin target) {
        this._bukkitPluginManager.loadCommands(target);
    }

    protected void unloadCommands(Plugin target) {
        this._bukkitPluginManager.unloadCommands(target);
    }

    @Override
    public Map<String, Command> getKnownCommands() {
        return this._bukkitPluginManager.getKnownCommands();
    }

    @Override
    public void setKnownCommands(Map<String, Command> knownCommands) {
        this._bukkitPluginManager.setKnownCommands(knownCommands);
    }

    /**
     * Reload a plugin.
     *
     * @param plugin the plugin to reload
     */
    @Override
    public void reload(Plugin plugin) {
        if (plugin != null) {
            PreReloadPluginEvent preReloadEvent = new PreReloadPluginEvent(plugin);
            Bukkit.getPluginManager().callEvent(preReloadEvent);
            if (preReloadEvent.isCancelled()) return;
            this.unload(plugin);
            this.load(plugin);
        }
    }

    /**
     * Reload all plugins.
     */
    @Override
    public void reloadAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!this.isIgnored(plugin) && !this.isPaperPlugin(plugin))
                this.reload(plugin);
    }

    /**
     * Unload a plugin.
     *
     * @param plugin the plugin to unload
     * @return the message to send to the user.
     */
    @Override
    public String unload(Plugin plugin) {
        PreUnloadPluginEvent preUnloadEvent = new PreUnloadPluginEvent(plugin);
        Bukkit.getPluginManager().callEvent(preUnloadEvent);
        if (preUnloadEvent.isCancelled()) return null;

        String name = plugin.getName();

        if (PlugManAPI.getGentleUnloads().containsKey(plugin)) {
            GentleUnload gentleUnload = PlugManAPI.getGentleUnloads().get(plugin);
            if (!gentleUnload.askingForGentleUnload())
                return name + "did not want to unload";
        } else {
            if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrapUseless))
                this.unloadCommands(plugin);

            org.bukkit.plugin.PluginManager pluginManager = Bukkit.getPluginManager();

            SimpleCommandMap commandMap = null;

            List<Plugin> plugins = null;

            Map<String, Plugin> names = null;
            Map<String, Command> commands = null;
            Map<Event, SortedSet<RegisteredListener>> listeners = null;

            boolean reloadlisteners = true;

            if (pluginManager != null) {
                pluginManager.disablePlugin(plugin);

                try {
                    Field pluginsField = Bukkit.getPluginManager().getClass().getDeclaredField("plugins");
                    pluginsField.setAccessible(true);
                    plugins = (List<Plugin>) pluginsField.get(pluginManager);

                    Field lookupNamesField = Bukkit.getPluginManager().getClass().getDeclaredField("lookupNames");
                    lookupNamesField.setAccessible(true);
                    names = (Map<String, Plugin>) lookupNamesField.get(pluginManager);

                    try {
                        Field listenersField = Bukkit.getPluginManager().getClass().getDeclaredField("listeners");
                        listenersField.setAccessible(true);
                        listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(pluginManager);
                    } catch (Exception e) {
                        reloadlisteners = false;
                    }

                    Field commandMapField = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
                    commandMapField.setAccessible(true);
                    commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);

                    Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                    knownCommandsField.setAccessible(true);
                    commands = (Map<String, Command>) knownCommandsField.get(commandMap);

                } catch (NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                    return PlugMan.getInstance().getMessageFormatter().format("unload.failed", name);
                }

            }

            pluginManager.disablePlugin(plugin);

            if (listeners != null && reloadlisteners)
                for (SortedSet<RegisteredListener> set : listeners.values())
                    set.removeIf(value -> value.getPlugin() == plugin);

            if (commandMap != null) {
                Map<String, Command> modifiedKnownCommands = new HashMap<>(commands);

                for (Map.Entry<String, Command> entry : new HashMap<>(commands).entrySet()) {
                    if (entry.getValue() instanceof PluginCommand) {
                        PluginCommand c = (PluginCommand) entry.getValue();
                        if (c.getPlugin() == plugin) {
                            c.unregister(commandMap);
                            modifiedKnownCommands.remove(entry.getKey());
                        }
                        continue;
                    }

                    try {
                        this._bukkitPluginManager.unregisterNonPluginCommands(plugin, commandMap, modifiedKnownCommands, entry);
                    } catch (IllegalStateException e) {
                        if (e.getMessage().equalsIgnoreCase("zip file closed")) {
                            if (PlugMan.getInstance().isNotifyOnBrokenCommandRemoval())
                                Logger.getLogger(PaperPluginManager.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
                            entry.getValue().unregister(commandMap);
                            modifiedKnownCommands.remove(entry.getKey());
                        }
                    }
                }

                this.setKnownCommands(modifiedKnownCommands);
            }

            if (plugins != null)
                plugins.remove(plugin);

            if (names != null)
                names.remove(name);
        }

        // Attempt to close the classloader to unlock any handles on the plugin's jar file.
        this.closeClassLoader(plugin);

        try {
            Class paper = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            Object paperPluginManagerImpl = paper.getMethod("getInstance").invoke(null);

            Field instanceManagerField = paperPluginManagerImpl.getClass().getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManagerImpl);

            Field lookupNamesField = instanceManager.getClass().getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map<String, Object> lookupNames = (Map<String, Object>) lookupNamesField.get(instanceManager);

            Method disableMethod = instanceManager.getClass().getMethod("disablePlugin", Plugin.class);
            disableMethod.setAccessible(true);
            disableMethod.invoke(instanceManager, plugin);

            lookupNames.remove(plugin.getName().toLowerCase());

            Field pluginListField = instanceManager.getClass().getDeclaredField("plugins");
            pluginListField.setAccessible(true);
            List<Plugin> pluginList = (List<Plugin>) pluginListField.get(instanceManager);
            pluginList.remove(plugin);

        } catch (Exception ignore) {
        } // Paper most likely not loaded

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);
    }

    protected void closeClassLoader(Plugin plugin) {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        if (!(classLoader instanceof URLClassLoader)) return;

        try {
            Field pluginField = classLoader.getClass().getDeclaredField("plugin");
            pluginField.setAccessible(true);
            pluginField.set(classLoader, null);

            Field pluginInitField = classLoader.getClass().getDeclaredField("pluginInit");
            pluginInitField.setAccessible(true);
            pluginInitField.set(classLoader, null);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(PaperPluginManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            ((URLClassLoader) classLoader).close();
        } catch (IOException ex) {
            Logger.getLogger(PaperPluginManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}