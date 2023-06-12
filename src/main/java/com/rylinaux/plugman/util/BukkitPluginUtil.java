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
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utilities for managing plugins.
 *
 * @author rylinaux
 */
public class BukkitPluginUtil implements PluginUtil {
    //TODO: Clean this class up, I don't like how it currently looks

    private final Class<?> pluginClassLoader;
    private final Field pluginClassLoaderPlugin;
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

    /**
     * Download a plugin from a URL.
     *
     * @param url the URL to download from
     * @return downloaded plugin
     * @throws IOException if an error occurs
     */
    @Override
    public File download(URL url) throws IOException {
        File tmp = File.createTempFile("plugman", ".jar");
        try {
            // download the plugin from the specified URL
            FileUtils.copyURLToFile(url, tmp);

            // extract the plugin name and version from the plugin.yml
            ZipFile zipFile = new ZipFile(tmp);
            ZipEntry entry = zipFile.getEntry("plugin.yml");
            if (entry == null) throw new IOException("No plugin.yml found in the jar.");

            YamlConfiguration conf = new YamlConfiguration();
            try {
                conf.load(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
            } catch (InvalidConfigurationException e) {
                throw new IOException("Invalid plugin.yml", e);
            }

            String name = conf.getString("name");
            String version = conf.getString("version");
            if (name == null) throw new IOException("Could not find name in plugin.yml");
            if (version == null) throw new IOException("Could not find version in plugin.yml");

            // move the temp file to the plugins folder
            File pluginPath = new File("./plugins/" + name + "-" + version + ".jar");
            if (pluginPath.exists()) throw new IOException("Plugin already exists");

            FileUtils.copyFile(tmp, pluginPath);
            return pluginPath;
        } finally {
            if (!tmp.delete() && tmp.exists()) {
                Logger.getLogger(BukkitPluginUtil.class.getName()).severe("Could not delete temp file " + tmp.getAbsolutePath());
                tmp.deleteOnExit();
            }
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
        Map<String, Command> knownCommands = this.getKnownCommands();
        String parsedCommands = knownCommands.entrySet().stream()
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
                .map(s -> {
                    String[] parts = s.getKey().split(":");
                    // parts length equals 1 means that the key is the command
                    return parts.length == 1 ? parts[0] : parts[1];
                })
                .collect(Collectors.joining(", "));

        if (parsedCommands.isEmpty())
            return "No commands registered.";

        return parsedCommands;

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
                    Plugin plugin = Bukkit.getPluginManager().getPlugin(parts[0]);
                    if (plugin != null) plugins.add(plugin.getName());
                }
                continue;
            }

            try {
                JavaPlugin plugin = (JavaPlugin) this.pluginClassLoaderPlugin.get(cl);
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

        Plugin target = null;

        File pluginDir = new File("plugins");

        if (!pluginDir.isDirectory())
            return PlugMan.getInstance().getMessageFormatter().format("load.plugin-directory");

        File pluginFile = new File(pluginDir, name + ".jar");

        if (!pluginFile.isFile()) for (File f : pluginDir.listFiles())
            if (f.getName().endsWith(".jar")) try {
                PluginDescriptionFile desc = PlugMan.getInstance().getPluginLoader().getPluginDescription(f);
                if (desc.getName().equalsIgnoreCase(name)) {
                    pluginFile = f;
                    break;
                }
            } catch (InvalidDescriptionException e) {
                return PlugMan.getInstance().getMessageFormatter().format("load.cannot-find");
            }

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

        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrap_Useless)) {
            Plugin finalTarget = target;
            SchedulerUtil.runSyncTaskLater(() -> {
                this.loadCommands(finalTarget);
            }, 10L);

            PlugMan.getInstance().getFilePluginMap().put(pluginFile.getName(), target.getName());
        }

        return PlugMan.getInstance().getMessageFormatter().format("load.loaded", target.getName());

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
                                Logger.getLogger(BukkitPluginUtil.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
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
                Logger.getLogger(BukkitPluginUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

            try {

                ((URLClassLoader) cl).close();
            } catch (IOException ex) {
                Logger.getLogger(BukkitPluginUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);

    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        return false;
    }

    protected void loadCommands(Plugin plugin) {
        Map<String, Command> knownCommands = this.getKnownCommands();
        List<Map.Entry<String, Command>> commands = knownCommands.entrySet().stream()
                .filter(s -> {
                    if (s.getKey().contains(":"))
                        return s.getKey().split(":")[0].equalsIgnoreCase(plugin.getName());
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

        for (Map.Entry<String, Command> entry : commands) {
            String alias = entry.getKey();
            Command command = entry.getValue();
            PlugMan.getInstance().getBukkitCommandWrap().wrap(command, alias);
        }

        PlugMan.getInstance().getBukkitCommandWrap().sync();

        if (Bukkit.getOnlinePlayers().size() >= 1)
            for (Player player : Bukkit.getOnlinePlayers())
                player.updateCommands();
    }

    protected void unloadCommands(Plugin plugin) {
        Map<String, Command> knownCommands = this.getKnownCommands();
        List<Map.Entry<String, Command>> commands = knownCommands.entrySet().stream()
                .filter(s -> {
                    if (s.getKey().contains(":"))
                        return s.getKey().split(":")[0].equalsIgnoreCase(plugin.getName());
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

        if (Bukkit.getOnlinePlayers().size() >= 1)
            for (Player player : Bukkit.getOnlinePlayers()) player.updateCommands();
    }
}
