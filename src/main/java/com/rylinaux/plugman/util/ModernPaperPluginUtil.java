package com.rylinaux.plugman.util;

import com.rylinaux.plugman.PlugMan;
import com.rylinaux.plugman.api.GentleUnload;
import com.rylinaux.plugman.api.PlugManAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModernPaperPluginUtil extends PaperPluginUtil {
    public ModernPaperPluginUtil(BukkitPluginUtil bukkitPluginUtil) {
        super(bukkitPluginUtil);
    }
    @Override
    public String unload(Plugin plugin) {
        String name = plugin.getName();

        if (!PlugManAPI.getGentleUnloads().containsKey(plugin)) {
            PluginManager pluginManager = Bukkit.getPluginManager();
            SimpleCommandMap commandMap;
            List<Plugin> plugins;
            Map<String, Plugin> names;
            Map<String, Command> commands;
            Map<Event, SortedSet<RegisteredListener>> listeners = null;
            Map<String, Command> bukkitBrigMap;
            boolean reloadlisteners = true;

            pluginManager.disablePlugin(plugin);
            try {
                Class<?> paper = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
                Object paperPluginManagerImpl = paper.getMethod("getInstance").invoke(null);

                Field instanceManagerField = paperPluginManagerImpl.getClass().getDeclaredField("instanceManager");
                instanceManagerField.setAccessible(true);
                Object instanceManager = instanceManagerField.get(paperPluginManagerImpl);

                Field lookupNamesField = instanceManager.getClass().getDeclaredField("lookupNames");
                lookupNamesField.setAccessible(true);
                Map<String, Object> lookupNames = (Map<String, Object>) lookupNamesField.get(instanceManager);

                lookupNames.remove(plugin.getName().toLowerCase());

                Field pluginsField = instanceManager.getClass().getDeclaredField("plugins");
                pluginsField.setAccessible(true);
                List<Plugin> pluginList = (List<Plugin>) pluginsField.get(instanceManager);
                pluginList.remove(plugin);

                pluginsField = Bukkit.getPluginManager().getClass().getDeclaredField("plugins");
                pluginsField.setAccessible(true);
                plugins = (List<Plugin>) pluginsField.get(pluginManager);

                lookupNamesField = Bukkit.getPluginManager().getClass().getDeclaredField("lookupNames");
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

                Class<?> bukkitBrigMapC = Class.forName("io.papermc.paper.command.brigadier.bukkit.BukkitBrigForwardingMap");
                Field bukkitMapInsF = bukkitBrigMapC.getDeclaredField("INSTANCE");
                bukkitMapInsF.setAccessible(true);
                Constructor<?> bukkitMapCt = bukkitBrigMapC.getConstructor();
                bukkitBrigMap = (Map<String, Command>) bukkitMapCt.newInstance();

            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException |
                     InvocationTargetException | InstantiationException e) {
                e.printStackTrace();
                return PlugMan.getInstance().getMessageFormatter().format("unload.failed", name);
            }

            if (listeners != null && reloadlisteners) {
                for (SortedSet<RegisteredListener> set : listeners.values()) {
                    set.removeIf(value -> value.getPlugin() == plugin);
                }
            }
            if (commandMap != null) {
                for (Map.Entry<String, Command> entry : new HashSet<>(commands.entrySet())) {
                    if (entry.getValue() instanceof PluginCommand) {
                        PluginCommand c = (PluginCommand) entry.getValue();
                        if (c.getPlugin().equals(plugin)) {
                            c.unregister(commandMap);
                            commands.remove(entry.getKey());
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
                                    commands.remove(entry.getKey());
                                }
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IllegalStateException e) {
                        if (e.getMessage().equalsIgnoreCase("zip file closed")) {
                            if (PlugMan.getInstance().isNotifyOnBrokenCommandRemoval()) {
                                Logger.getLogger(PaperPluginUtil.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
                            }
                            entry.getValue().unregister(commandMap);
                            commands.remove(entry.getKey());
                        }
                    }
                }
            }
            if (plugins != null) {
                plugins.remove(plugin);
            }
            if (names != null) {
                names.remove(name);
            }
        } else {
            GentleUnload gentleUnload = PlugManAPI.getGentleUnloads().get(plugin);
            if (!gentleUnload.askingForGentleUnload()) {
                return name + "did not want to unload";
            }
        }
        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrap_Useless)) {
            this.unloadCommands(plugin);
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

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);

    }
}
