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
import com.rylinaux.plugman.util.BukkitCommandWrap;
import com.rylinaux.plugman.util.BukkitCommandWrapUseless;
import com.rylinaux.plugman.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utilities for managing plugins.
 *
 * @author rylinaux
 */
public class BukkitPluginManager implements PluginManager {
    //TODO: Clean this class up, I don't like how it currently looks

    private final Class<?> pluginClassLoader;
    private final Field pluginClassLoaderPlugin;
    private Field commandMapField;
    private Field knownCommandsField;
    private final String nmsVersion = null;

    public BukkitPluginManager() {
        try {
            this.pluginClassLoader = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
            this.pluginClassLoaderPlugin = this.pluginClassLoader.getDeclaredField("plugin");
            this.pluginClassLoaderPlugin.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Enable a plugin.
     *
     * @param plugin the plugin to enable
     */
    @Override
    public void enable(Plugin plugin) {
        if (plugin != null && !plugin.isEnabled()) Bukkit.getPluginManager().enablePlugin(plugin);
    }

    /**
     * Enable all plugins.
     */
    @Override
    public void enableAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!this.isIgnored(plugin) && !this.isPaperPlugin(plugin))
                this.enable(plugin);
    }

    /**
     * Disable a plugin.
     *
     * @param plugin the plugin to disable
     */
    @Override
    public void disable(Plugin plugin) {
        if (plugin != null && plugin.isEnabled()) Bukkit.getPluginManager().disablePlugin(plugin);
    }

    /**
     * Disable all plugins.
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
        ChatColor color = plugin.isEnabled() ? ChatColor.GREEN : ChatColor.RED;
        String pluginName = color + plugin.getName();
        if (includeVersions) pluginName += " (" + plugin.getDescription().getVersion() + ")";
        return pluginName;
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
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (name.equalsIgnoreCase(plugin.getName())) return plugin;
        return null;
    }

    /**
     * Returns a List of plugin names.
     *
     * @return list of plugin names
     */
    @Override
    public List<String> getPluginNames(boolean fullName) {
        List<String> plugins = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            plugins.add(fullName ? plugin.getDescription().getFullName() : plugin.getName());
        return plugins;
    }

    /**
     * Returns a List of disabled plugin names.
     *
     * @return list of disabled plugin names
     */
    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        List<String> plugins = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!plugin.isEnabled())
                plugins.add(fullName ? plugin.getDescription().getFullName() : plugin.getName());
        return plugins;
    }

    /**
     * Returns a List of enabled plugin names.
     *
     * @return list of enabled plugin names
     */
    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        List<String> plugins = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (plugin.isEnabled())
                plugins.add(fullName ? plugin.getDescription().getFullName() : plugin.getName());
        return plugins;
    }

    /**
     * Get the version of another plugin.
     *
     * @param name the name of the other plugin.
     * @return the version.
     */
    @Override
    public String getPluginVersion(String name) {
        Plugin plugin = this.getPluginByName(name);
        if (plugin != null && plugin.getDescription() != null) return plugin.getDescription().getVersion();
        return null;
    }

    /**
     * Returns the commands a plugin has registered.
     *
     * @param plugin the plugin to deal with
     * @return the commands registered
     */
    @Override
    public String getUsages(Plugin plugin) {
        String parsedCommands = this.getCommandsFromPlugin(plugin).stream().map(s -> {
            String[] parts = s.getKey().split(":");
            // parts length equals 1 means that the key is the command
            return parts.length == 1 ? parts[0] : parts[1];
        }).collect(Collectors.joining(", "));



        if (parsedCommands.isEmpty())
            return "No commands registered.";

        return parsedCommands;

    }

    private List<Map.Entry<String, Command>> getCommandsFromPlugin(Plugin plugin) {
        Map<String, Command> knownCommands = this.getKnownCommands();
        return knownCommands.entrySet().stream()
                            .filter(s -> {
                                                 if (s.getKey().contains(":")) return s.getKey().split(":")[0].equalsIgnoreCase(plugin.getName());
                                                 else {
                                                     ClassLoader cl = s.getValue().getClass().getClassLoader();
                                                     try {
                                                         return cl.getClass() == this.pluginClassLoader && this.pluginClassLoaderPlugin.get(cl) == plugin;
                                                     } catch (IllegalAccessException e) {
                                                         return false;
                                                     }
                                                 }
                                             })
                            .collect(Collectors.toList());
    }

    /**
     * Find which plugin has a given command registered.
     *
     * @param command the command.
     * @return the plugin.
     */
    @Override
    public List<String> findByCommand(String command) {
        List<String> plugins = new ArrayList<>();

        for (Map.Entry<String, Command> s : this.getKnownCommands().entrySet()) {
            ClassLoader cl = s.getValue().getClass().getClassLoader();
            if (cl.getClass() != this.pluginClassLoader) {
                String[] parts = s.getKey().split(":");

                if (parts.length == 2 && parts[1].equalsIgnoreCase(command)) {
                    Plugin plugin = Arrays.stream(Bukkit.getPluginManager().getPlugins()).
                            filter(pl -> pl.getName().equalsIgnoreCase(parts[0])).
                            findFirst().orElse(null);

                    if (plugin != null)
                        plugins.add(plugin.getName());
                }
                continue;
            }

            try {
                String[] parts = s.getKey().split(":");
                String cmd = parts[parts.length - 1];

                if (!cmd.equalsIgnoreCase(command))
                    continue;

                JavaPlugin plugin = (JavaPlugin) this.pluginClassLoaderPlugin.get(cl);

                if (plugins.contains(plugin.getName()))
                    continue;

                plugins.add(plugin.getName());
            } catch (IllegalAccessException ignored) {
            }
        }

        return plugins;

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
        for (String name : PlugMan.getInstance().getIgnoredPlugins()) if (name.equalsIgnoreCase(plugin)) return true;
        return false;
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
        Plugin target;

        File pluginDir = new File("plugins");

        if (!pluginDir.isDirectory())
            return PlugMan.getInstance().getMessageFormatter().format("load.plugin-directory");

        File pluginFile = new File(pluginDir, name + ".jar");
        PluginDescriptionFile description = null;

        if (!pluginFile.isFile()) for (File f : pluginDir.listFiles())
            if (f.getName().endsWith(".jar")) try {
                PluginDescriptionFile desc = PlugMan.getInstance().getPluginLoader().getPluginDescription(f);
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
                description = PlugMan.getInstance().getPluginLoader().getPluginDescription(pluginFile);
            } catch (InvalidDescriptionException e) {
                return PlugMan.getInstance().getMessageFormatter().format("load.cannot-find");
            }
        }

        PreLoadPluginEvent preloadEvent = new PreLoadPluginEvent(pluginFile.toPath(), description);
        Bukkit.getPluginManager().callEvent(preloadEvent);
        if (preloadEvent.isCancelled()) return preloadEvent.getCancelledReason();

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

        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrapUseless)) {
            Plugin finalTarget = target;
            Bukkit.getScheduler().runTaskLater(PlugMan.getInstance(), () -> {
                this.loadCommands(finalTarget);
            }, 10L);

            PlugMan.getInstance().getFilePluginMap().put(pluginFile.getName(), target.getName());
        }

        return PlugMan.getInstance().getMessageFormatter().format("load.loaded", target.getName());

    }

    @Override
    public Map<String, Command> getKnownCommands() {
        if (!this.fetchCommandMapField())
            throw new RuntimeException("Cannot find command map");

        SimpleCommandMap commandMap;
        try {
            commandMap = (SimpleCommandMap) this.commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (!this.fetchKnownCommandsField())
            throw new RuntimeException("Unable to find known commands");

        Map<String, Command> knownCommands;

        try {
            knownCommands = (Map<String, Command>) this.knownCommandsField.get(commandMap);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }

        return knownCommands;
    }

    private boolean fetchCommandMapField() {
        if (this.commandMapField != null) return true;
        try {
            this.commandMapField = Class.forName(BukkitCommandWrap.getCraftBukkitPrefix("CraftServer")).getDeclaredField("commandMap");
            this.commandMapField.setAccessible(true);
            return true;
        } catch (NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean fetchKnownCommandsField() {
        if (this.knownCommandsField != null) return true;

        try {
            this.knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            this.knownCommandsField.setAccessible(true);
            return true;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setKnownCommands(Map<String, Command> knownCommands) {
        if (!this.fetchCommandMapField())
            throw new RuntimeException("Cannot find command map");

        SimpleCommandMap commandMap;
        try {
            commandMap = (SimpleCommandMap) this.commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (!this.fetchKnownCommandsField())
            throw new RuntimeException("Unable to find known commands");

        try {
            this.knownCommandsField.set(commandMap, knownCommands);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
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
    public synchronized String unload(Plugin plugin) {
        PreUnloadPluginEvent preUnloadEvent = new PreUnloadPluginEvent(plugin);
        Bukkit.getPluginManager().callEvent(preUnloadEvent);
        if (preUnloadEvent.isCancelled()) return preUnloadEvent.getCancelledReason();

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
                        this.unregisterNonPluginCommands(plugin, commandMap, modifiedKnownCommands, entry);
                    } catch (IllegalStateException e) {
                        if (e.getMessage().equalsIgnoreCase("zip file closed")) {
                            if (PlugMan.getInstance().isNotifyOnBrokenCommandRemoval())
                                Logger.getLogger(BukkitPluginManager.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
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
                Logger.getLogger(BukkitPluginManager.class.getName()).log(Level.SEVERE, null, ex);
            }

            try {

                ((URLClassLoader) cl).close();
            } catch (IOException ex) {
                Logger.getLogger(BukkitPluginManager.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);

    }

    protected void unregisterNonPluginCommands(Plugin plugin, CommandMap commandMap, Map<String, Command> commands,
                                               Map.Entry<String, Command> entry) {
        Field pluginField = Arrays.stream(((Map.Entry<String, ? extends Command>) entry).getValue().getClass().getDeclaredFields())
                                  .filter(field -> Plugin.class.isAssignableFrom(field.getType()))
                                  .findFirst()
                                  .orElse(null);
        if (pluginField == null) return;

        Plugin owningPlugin;
        try {
            pluginField.setAccessible(true);
            owningPlugin = (Plugin) pluginField.get(((Map.Entry<String, ? extends Command>) entry).getValue());
            if (owningPlugin.getName().equalsIgnoreCase(plugin.getName())) {
                ((Map.Entry<String, ? extends Command>) entry).getValue().unregister(commandMap);
                commands.remove(entry.getKey());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        return false;
    }

    protected void loadCommands(Plugin plugin) {
        List<Map.Entry<String, Command>> commands = this.getCommandsFromPlugin(plugin);

        for (Map.Entry<String, Command> entry : commands) {
            String alias = entry.getKey();
            Command command = entry.getValue();
            PlugMan.getInstance().getBukkitCommandWrap().wrap(command, alias);
        }

        PlugMan.getInstance().getBukkitCommandWrap().sync();
    }

    protected synchronized void unloadCommands(Plugin plugin) {
        Map<String, Command> knownCommands = this.getKnownCommands();
        List<Map.Entry<String, Command>> commands = this.getCommandsFromPlugin(plugin);

        for (Map.Entry<String, Command> entry : commands) {
            String alias = entry.getKey();
            PlugMan.getInstance().getBukkitCommandWrap().unwrap(alias);
        }

        for (Map.Entry<String, Command> entry : knownCommands.entrySet().stream().filter(stringCommandEntry -> Plugin.class.isAssignableFrom(stringCommandEntry.getValue().getClass())).filter(stringCommandEntry -> {
            Field pluginField = Arrays.stream(stringCommandEntry.getValue().getClass().getDeclaredFields()).filter(field -> Plugin.class.isAssignableFrom(field.getType())).findFirst().orElse(null);
            if (pluginField != null) {
                Plugin owningPlugin;
                try {
                    owningPlugin = (Plugin) pluginField.get(stringCommandEntry.getValue());
                    return owningPlugin.getName().equalsIgnoreCase(plugin.getName());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            return false;
        }).collect(Collectors.toList())) {
            String alias = entry.getKey();
            PlugMan.getInstance().getBukkitCommandWrap().unwrap(alias);
        }

        PlugMan.getInstance().getBukkitCommandWrap().sync();
    }
}
