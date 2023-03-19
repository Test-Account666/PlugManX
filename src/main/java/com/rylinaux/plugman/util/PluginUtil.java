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
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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
public class PluginUtil {

    private static Field commandMapField;
    private static Field knownCommandsField;
    private static String nmsVersion = null;

    private static final Class<?> pluginClassLoader;
    private static final Field pluginClassLoaderPlugin;

    static {
        try {
            pluginClassLoader = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
            pluginClassLoaderPlugin = pluginClassLoader.getDeclaredField("plugin");
            pluginClassLoaderPlugin.setAccessible(true);
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
    public static File download(URL url) throws IOException {
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
                Logger.getLogger(PluginUtil.class.getName()).severe("Could not delete temp file " + tmp.getAbsolutePath());
                tmp.deleteOnExit();
            }
        }
    }

    /**
     * Enable a plugin.
     *
     * @param plugin the plugin to enable
     */
    public static void enable(Plugin plugin) {
        if (plugin != null && !plugin.isEnabled()) Bukkit.getPluginManager().enablePlugin(plugin);
    }

    /**
     * Enable all plugins.
     */
    public static void enableAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!PluginUtil.isIgnored(plugin))
                PluginUtil.enable(plugin);
    }

    /**
     * Disable a plugin.
     *
     * @param plugin the plugin to disable
     */
    public static void disable(Plugin plugin) {
        if (plugin != null && plugin.isEnabled()) Bukkit.getPluginManager().disablePlugin(plugin);
    }

    /**
     * Disable all plugins.
     */
    public static void disableAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!PluginUtil.isIgnored(plugin))
                PluginUtil.disable(plugin);
    }

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin the plugin to format
     * @return the formatted name
     */
    public static String getFormattedName(Plugin plugin) {
        return PluginUtil.getFormattedName(plugin, false);
    }

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin          the plugin to format
     * @param includeVersions whether to include the version
     * @return the formatted name
     */
    public static String getFormattedName(Plugin plugin, boolean includeVersions) {
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
    public static Plugin getPluginByName(String[] args, int start) {
        return PluginUtil.getPluginByName(StringUtil.consolidateStrings(args, start));
    }

    /**
     * Returns a plugin from a String.
     *
     * @param name the name of the plugin
     * @return the plugin
     */
    public static Plugin getPluginByName(String name) {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (name.equalsIgnoreCase(plugin.getName())) return plugin;
        return null;
    }

    /**
     * Returns a List of plugin names.
     *
     * @return list of plugin names
     */
    public static List<String> getPluginNames(boolean fullName) {
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
    public static List<String> getDisabledPluginNames(boolean fullName) {
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
    public static List<String> getEnabledPluginNames(boolean fullName) {
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
    public static String getPluginVersion(String name) {
        Plugin plugin = PluginUtil.getPluginByName(name);
        if (plugin != null && plugin.getDescription() != null) return plugin.getDescription().getVersion();
        return null;
    }

    /**
     * Returns the commands a plugin has registered.
     *
     * @param plugin the plugin to deal with
     * @return the commands registered
     */
    public static String getUsages(Plugin plugin) {
        Map<String, Command> knownCommands = PluginUtil.getKnownCommands();
        String parsedCommands = knownCommands.entrySet().stream()
                .filter(s -> {
                    if (s.getKey().contains(":")) {
                        return s.getKey().split(":")[0].equalsIgnoreCase(plugin.getName());
                    } else {
                        ClassLoader cl = s.getValue().getClass().getClassLoader();
                        try {
                            return cl.getClass() == pluginClassLoader && pluginClassLoaderPlugin.get(cl) == plugin;
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
    public static List<String> findByCommand(String command) {
        List<String> plugins = new ArrayList<>();

        List<String> pls = new ArrayList<>();
        for (Map.Entry<String, Command> s : PluginUtil.getKnownCommands().entrySet()) {
            ClassLoader cl = s.getValue().getClass().getClassLoader();
            if (cl.getClass() != pluginClassLoader) {
                String[] parts = s.getKey().split(":");
                if (parts.length == 2 && parts[1].equalsIgnoreCase(command)) {
                    Plugin plugin = Bukkit.getPluginManager().getPlugin(parts[0]);
                    if (plugin != null) pls.add(plugin.getName());
                }
                continue;
            }

            try {
                JavaPlugin plugin = (JavaPlugin) pluginClassLoaderPlugin.get(cl);
                pls.add(plugin.getName());
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
    public static boolean isIgnored(Plugin plugin) {
        return PluginUtil.isIgnored(plugin.getName());
    }

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    public static boolean isIgnored(String plugin) {
        for (String name : PlugMan.getInstance().getIgnoredPlugins()) if (name.equalsIgnoreCase(plugin)) return true;
        return false;
    }

    public static PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        if (file == null)
            throw new InvalidDescriptionException("File cannot be null");

        JarFile jar = null;
        InputStream stream = null;

        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry("plugin.yml");

            if (entry == null) {
                throw new InvalidDescriptionException(new FileNotFoundException("Jar does not contain plugin.yml"));
            }

            stream = jar.getInputStream(entry);

            return new PluginDescriptionFile(stream);

        } catch (IOException ex) {
            throw new InvalidDescriptionException(ex);
        } catch (YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException e) {
                }
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Loads and enables a plugin.
     *
     * @param plugin plugin to load
     * @return status message
     */
    private static String load(Plugin plugin) {
        return PluginUtil.load(plugin.getName());
    }

    /**
     * Loads and enables a plugin.
     *
     * @param name plugin's name
     * @return status message
     */
    public static String load(String name) {

        Plugin target = null;
        boolean paperLoaded = false;

        File pluginDir = new File("plugins");

        if (!pluginDir.isDirectory())
            return PlugMan.getInstance().getMessageFormatter().format("load.plugin-directory");

        File pluginFile = new File(pluginDir, name + ".jar");

        if (!pluginFile.isFile()) for (File f : pluginDir.listFiles())
            if (f.getName().endsWith(".jar")) try {
                PluginDescriptionFile desc = getPluginDescription(f);
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
        } catch (Exception ignore) { } // Paper most likely not loaded

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
            Bukkit.getScheduler().runTaskLater(PlugMan.getInstance(), () -> {
                Map<String, Command> knownCommands = PluginUtil.getKnownCommands();
                List<Map.Entry<String, Command>> commands = knownCommands.entrySet().stream()
                        .filter(s -> {
                            if (s.getKey().contains(":")) {
                                return s.getKey().split(":")[0].equalsIgnoreCase(finalTarget.getName());
                            } else {
                                ClassLoader cl = s.getValue().getClass().getClassLoader();
                                try {
                                    return cl.getClass() == pluginClassLoader && pluginClassLoaderPlugin.get(cl) == finalTarget;
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
                    for (Player player : Bukkit.getOnlinePlayers()) player.updateCommands();
            }, 10L);

            PlugMan.getInstance().getFilePluginMap().put(pluginFile.getName(), target.getName());
        }

        return PlugMan.getInstance().getMessageFormatter().format("load.loaded", target.getName());

    }

    public static Map<String, Command> getKnownCommands() {
        if (PluginUtil.commandMapField == null) try {
            PluginUtil.commandMapField = Class.forName("org.bukkit.craftbukkit." + PluginUtil.getNmsVersion() + ".CraftServer").getDeclaredField("commandMap");
            PluginUtil.commandMapField.setAccessible(true);
        } catch (NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        SimpleCommandMap commandMap;
        try {
            commandMap = (SimpleCommandMap) PluginUtil.commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (PluginUtil.knownCommandsField == null) try {
            PluginUtil.knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            PluginUtil.knownCommandsField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }

        Map<String, Command> knownCommands;

        try {
            knownCommands = (Map<String, Command>) PluginUtil.knownCommandsField.get(commandMap);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }

        return knownCommands;
    }

    private static String getNmsVersion() {
        if (PluginUtil.nmsVersion == null) try {
            PluginUtil.nmsVersion = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            PluginUtil.nmsVersion = null;
        }
        return PluginUtil.nmsVersion;
    }


    /**
     * Reload a plugin.
     *
     * @param plugin the plugin to reload
     */
    public static void reload(Plugin plugin) {
        if (plugin != null) {
            PluginUtil.unload(plugin);
            PluginUtil.load(plugin);
        }
    }

    /**
     * Reload all plugins.
     */
    public static void reloadAll() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins())
            if (!PluginUtil.isIgnored(plugin))
                PluginUtil.reload(plugin);
    }

    /**
     * Unload a plugin.
     *
     * @param plugin the plugin to unload
     * @return the message to send to the user.
     */
    public static String unload(Plugin plugin) {
        String name = plugin.getName();

        if (!PlugManAPI.getGentleUnloads().containsKey(plugin)) {
            if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrap_Useless)) {
                Map<String, Command> knownCommands = PluginUtil.getKnownCommands();
                List<Map.Entry<String, Command>> commands = knownCommands.entrySet().stream()
                        .filter(s -> {
                            if (s.getKey().contains(":")) {
                                return s.getKey().split(":")[0].equalsIgnoreCase(plugin.getName());
                            } else {
                                ClassLoader cl = s.getValue().getClass().getClassLoader();
                                try {
                                    return cl.getClass() == pluginClassLoader && pluginClassLoaderPlugin.get(cl) == plugin;
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
                                Logger.getLogger(PluginUtil.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
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
                Logger.getLogger(PluginUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

            try {

                ((URLClassLoader) cl).close();
            } catch (IOException ex) {
                Logger.getLogger(PluginUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        try {

            Class paper = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            Object paperPluginManagerImpl = paper.getMethod("getInstance").invoke(null);

            Field instanceManagerF = paperPluginManagerImpl.getClass().getDeclaredField("instanceManager");
            instanceManagerF.setAccessible(true);
            Object instanceManager = instanceManagerF.get(paperPluginManagerImpl);

            Field lookupNamesF = instanceManager.getClass().getDeclaredField("lookupNames");
            lookupNamesF.setAccessible(true);
            Map<String, Object> lookupNames = (Map<String, Object>) lookupNamesF.get(instanceManager);

            Method disableMethod = instanceManager.getClass().getMethod("disablePlugin", Plugin.class);
            disableMethod.setAccessible(true);
            disableMethod.invoke(instanceManager, plugin);

            lookupNames.remove(plugin.getName().toLowerCase());

            Field pluginListF = instanceManager.getClass().getDeclaredField("plugins");
            pluginListF.setAccessible(true);
            List<Plugin> pluginList = (List<Plugin>) pluginListF.get(instanceManager);
            pluginList.remove(plugin);

        } catch (Exception ignore) { } // Paper most likely not loaded

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);

    }
}
