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
import com.rylinaux.plugman.api.GentleUnload;
import com.rylinaux.plugman.api.PlugManAPI;
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
import java.net.URL;
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
public class PaperPluginUtil implements PluginUtil {
    //TODO: Clean this class up, I don't like how it currently looks

    private final Class<?> pluginClassLoader;
    private final Field pluginClassLoaderPlugin;
    private final BukkitPluginUtil bukkitPluginUtil;
    private Field commandMapField;
    private Field knownCommandsField;
    private String nmsVersion = null;

    {
        try {
            this.pluginClassLoader = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
            this.pluginClassLoaderPlugin = this.pluginClassLoader.getDeclaredField("plugin");
            this.pluginClassLoaderPlugin.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public PaperPluginUtil(BukkitPluginUtil bukkitPluginUtil) {
        this.bukkitPluginUtil = bukkitPluginUtil;
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
     * Download a plugin from a URL.
     *
     * @param url the URL to download from
     * @return downloaded plugin
     * @throws IOException if an error occurs
     */
    @Override
    public File download(URL url) throws IOException {
        return this.bukkitPluginUtil.download(url);
    }

    /**
     * Enable a plugin.
     * Currently unsupported, probably needs fixing
     *
     * @param plugin the plugin to enable
     */
    @Override
    public void enable(Plugin plugin) {
        this.bukkitPluginUtil.enable(plugin);
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
        this.bukkitPluginUtil.disable(plugin);
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
        return this.bukkitPluginUtil.getFormattedName(plugin, includeVersions);
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
        return this.bukkitPluginUtil.getPluginByName(name);
    }

    /**
     * Returns a List of plugin names.
     *
     * @return list of plugin names
     */
    @Override
    public List<String> getPluginNames(boolean fullName) {
        return this.bukkitPluginUtil.getPluginNames(fullName);
    }

    /**
     * Returns a List of disabled plugin names.
     *
     * @return list of disabled plugin names
     */
    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        return this.bukkitPluginUtil.getDisabledPluginNames(fullName);
    }

    /**
     * Returns a List of enabled plugin names.
     *
     * @return list of enabled plugin names
     */
    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        return this.bukkitPluginUtil.getEnabledPluginNames(fullName);
    }

    /**
     * Get the version of another plugin.
     *
     * @param name the name of the other plugin.
     * @return the version.
     */
    @Override
    public String getPluginVersion(String name) {
        return this.bukkitPluginUtil.getPluginVersion(name);
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
        return this.bukkitPluginUtil.getUsages(plugin);
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
        return this.bukkitPluginUtil.findByCommand(command);
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
        return this.bukkitPluginUtil.isIgnored(plugin);
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

        if (!pluginFile.isFile())
            for (File f : pluginDir.listFiles())
                if (f.getName().endsWith(".jar")) try {
                    PluginDescriptionFile desc = this.getPluginDescription(f);
                    if (desc.getName().equalsIgnoreCase(name)) {
                        pluginFile = f;
                        break;
                    }
                } catch (InvalidDescriptionException e) {
                    return PlugMan.getInstance().getMessageFormatter().format("load.cannot-find");
                }

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

        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrap_Useless)) {
            Plugin finalTarget = target;

            if (this.isFolia()) {
                com.tcoded.folialib.FoliaLib foliaLib = new com.tcoded.folialib.FoliaLib(PlugMan.getInstance());

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
        this.bukkitPluginUtil.loadCommands(target);
    }

    protected void unloadCommands(Plugin target) {
        this.bukkitPluginUtil.unloadCommands(target);
    }

    @Override
    public Map<String, Command> getKnownCommands() {
        if (this.commandMapField == null) try {
            this.commandMapField = Class.forName("org.bukkit.craftbukkit." + this.getNmsVersion() + ".CraftServer").getDeclaredField("commandMap");
            this.commandMapField.setAccessible(true);
        } catch (NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        SimpleCommandMap commandMap;
        try {
            commandMap = (SimpleCommandMap) this.commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (this.knownCommandsField == null) try {
            this.knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            this.knownCommandsField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }

        Map<String, Command> knownCommands;

        try {
            knownCommands = (Map<String, Command>) this.knownCommandsField.get(commandMap);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }

        return knownCommands;
    }

    private String getNmsVersion() {
        if (this.nmsVersion == null) try {
            this.nmsVersion = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            this.nmsVersion = null;
        }
        return this.nmsVersion;
    }


    /**
     * Reload a plugin.
     *
     * @param plugin the plugin to reload
     */
    @Override
    public void reload(Plugin plugin) {
        if (plugin != null) {
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
        String name = plugin.getName();

        if (!PlugManAPI.getGentleUnloads().containsKey(plugin)) {
            if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrap_Useless))
                this.unloadCommands(plugin);

            PluginManager pluginManager = Bukkit.getPluginManager();

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

            if (commandMap != null)
                for (Iterator<Map.Entry<String, Command>> it = commands.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Command> entry = it.next();
                    if (entry.getValue() instanceof PluginCommand) {
                        PluginCommand c = (PluginCommand) entry.getValue();
                        if (c.getPlugin() == plugin) {
                            c.unregister(commandMap);
                            it.remove();
                        }
                    } else try {
                        Field pluginField = Arrays.stream(entry.getValue().getClass().getDeclaredFields()).filter(field -> Plugin.class.isAssignableFrom(field.getType())).findFirst().orElse(null);
                        if (pluginField != null) {
                            Plugin owningPlugin;
                            try {
                                pluginField.setAccessible(true);
                                owningPlugin = (Plugin) pluginField.get(entry.getValue());
                                if (owningPlugin.getName().equalsIgnoreCase(plugin.getName())) {
                                    entry.getValue().unregister(commandMap);
                                    it.remove();
                                }
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IllegalStateException e) {
                        if (e.getMessage().equalsIgnoreCase("zip file closed")) {
                            if (PlugMan.getInstance().isNotifyOnBrokenCommandRemoval())
                                Logger.getLogger(PaperPluginUtil.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
                            entry.getValue().unregister(commandMap);
                            it.remove();
                        }
                    }
                }

            if (plugins != null && plugins.contains(plugin))
                plugins.remove(plugin);

            if (names != null && names.containsKey(name))
                names.remove(name);
        } else {
            GentleUnload gentleUnload = PlugManAPI.getGentleUnloads().get(plugin);
            if (!gentleUnload.askingForGentleUnload())
                return name + "did not want to unload";
        }

        // Attempt to close the classloader to unlock any handles on the plugin's jar file.
        ClassLoader cl = plugin.getClass().getClassLoader();
        if (cl instanceof URLClassLoader) {
            try {

                Field pluginField = cl.getClass().getDeclaredField("plugin");
                pluginField.setAccessible(true);
                pluginField.set(cl, null);

                Field pluginInitField = cl.getClass().getDeclaredField("pluginInit");
                pluginInitField.setAccessible(true);
                pluginInitField.set(cl, null);

            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(PaperPluginUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

            try {

                ((URLClassLoader) cl).close();
            } catch (IOException ex) {
                Logger.getLogger(PaperPluginUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

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
}