package com.rylinaux.plugman.pluginmanager;

import com.rylinaux.plugman.PlugMan;
import com.rylinaux.plugman.api.GentleUnload;
import com.rylinaux.plugman.api.PlugManAPI;
import com.rylinaux.plugman.api.event.PreUnloadPluginEvent;
import com.rylinaux.plugman.util.BukkitCommandWrapUseless;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Logger;

public class ModernPaperPluginManager extends PaperPluginManager {

    public ModernPaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        super(bukkitPluginManager);
    }

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
            org.bukkit.plugin.PluginManager pluginManager = Bukkit.getPluginManager();
            SimpleCommandMap commandMap;
            List<Plugin> plugins;
            Map<String, Plugin> names;
            Map<String, Command> commands;
            Map<Event, SortedSet<RegisteredListener>> listeners = null;
            Map<String, Object> lookupNames;
            List<Plugin> pluginList;
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
                lookupNames = (Map<String, Object>) lookupNamesField.get(instanceManager);


                Field pluginsField = instanceManager.getClass().getDeclaredField("plugins");
                pluginsField.setAccessible(true);
                pluginList = (List<Plugin>) pluginsField.get(instanceManager);

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

            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                return PlugMan.getInstance().getMessageFormatter().format("unload.failed", name);
            }

            if (listeners != null && reloadlisteners)
                for (SortedSet<RegisteredListener> set : listeners.values())
                    set.removeIf(value -> value.getPlugin() == plugin);

            if (commandMap != null)
                for (Map.Entry<String, Command> entry : new HashSet<>(commands.entrySet())) {
                    if (entry.getValue() instanceof PluginCommand) {
                        PluginCommand c = (PluginCommand) entry.getValue();
                        if (c.getPlugin().equals(plugin)) {
                            c.unregister(commandMap);
                            commands.remove(entry.getKey());
                        }
                        continue;
                    }

                    try {
                        Field pluginField = Arrays.stream(entry.getValue().getClass().getDeclaredFields())
                                                  .filter(field -> Plugin.class.isAssignableFrom(field.getType()))
                                                  .findFirst()
                                                  .orElse(null);
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
                            if (PlugMan.getInstance().isNotifyOnBrokenCommandRemoval())
                                Logger.getLogger(PaperPluginManager.class.getName()).info("Removing broken command '" + entry.getValue().getName() + "'!");
                            entry.getValue().unregister(commandMap);
                            commands.remove(entry.getKey());
                        }
                    }
                }

            // The plugin can only be removed from the lookup names and the plugin list AFTER the commands are unregistered, to avoid issues with commands created via Paper's Brigadier API
            lookupNames.remove(plugin.getName().toLowerCase());
            pluginList.remove(plugin);

            if (plugins != null)
                plugins.remove(plugin);
            if (names != null)
                names.remove(name);
        }

        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrapUseless))
            this.unloadCommands(plugin);

        // Attempt to close the classloader to unlock any handles on the plugin's jar file.
        this.closeClassLoader(plugin);

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);

    }
}
