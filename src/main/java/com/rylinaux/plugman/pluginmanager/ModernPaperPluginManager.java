package com.rylinaux.plugman.pluginmanager;

import com.destroystokyo.paper.event.executor.asm.SafeClassDefiner;
import com.rylinaux.plugman.PlugMan;
import com.rylinaux.plugman.api.GentleUnload;
import com.rylinaux.plugman.api.PlugManAPI;
import com.rylinaux.plugman.api.events.PlugmanUnloadPluginEvent;
import com.rylinaux.plugman.util.BukkitCommandWrapUseless;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ModernPaperPluginManager extends PaperPluginManager {

    public ModernPaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        super(bukkitPluginManager);
    }

    @Override
    public String unload(Plugin plugin) {
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
            Map<Method, Class<?>> eventExecutorMap = null;
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

                try {
                    Field eventExecutorMapField = EventExecutor.class.getDeclaredField("eventExecutorMap");
                    eventExecutorMapField.setAccessible(true);
                    eventExecutorMap = (Map<Method, Class<?>>) eventExecutorMapField.get(null);
                } catch (Exception ignored) {
                }

            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException |
                     InvocationTargetException e) {
                e.printStackTrace();
                return PlugMan.getInstance().getMessageFormatter().format("unload.failed", name);
            }

            if (listeners != null && reloadlisteners)
                for (SortedSet<RegisteredListener> set : listeners.values())
                    set.removeIf(value -> value.getPlugin() == plugin);

            if (commandMap != null) {
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
            }

            // The plugin can only be removed from the lookup names and the plugin list AFTER the commands are unregistered, to avoid issues with commands created via Paper's Brigadier API
            lookupNames.remove(plugin.getName().toLowerCase());
            pluginList.remove(plugin);

            if (plugins != null)
                plugins.remove(plugin);
            if (names != null)
                names.remove(name);
            if (eventExecutorMap != null) {
                ClassLoader loader = plugin.getClass().getClassLoader();
                Iterator<Map.Entry<Method, Class<?>>> iterator = eventExecutorMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Method, Class<?>> entry = iterator.next();
                    if (entry.getKey().getDeclaringClass().getClassLoader() == loader) {
                        synchronized (entry.getKey()) { // paper also synchronizes over the method
                            iterator.remove();
                        }
                    }
                }
            }
            try {
                // Try to unload from SafeClassDefiner
                Class<?> cls = SafeClassDefiner.class;
                Field instanceField = cls.getDeclaredField("INSTANCE");
                instanceField.setAccessible(true);
                instanceField.setAccessible(true);
                Object instance = instanceField.get(null);
                Field loadersField = instance.getClass().getDeclaredField("loaders");
                loadersField.setAccessible(true);
                Map<?, ?> loaders = (Map<?, ?>) loadersField.get(instance);
                loaders.remove(plugin.getClass().getClassLoader());
            } catch (NoClassDefFoundError ignored) { // ignore this, if SafeClassDefiner doesn't exist
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                return PlugMan.getInstance().getMessageFormatter().format("unload.failed", name);
            }
            if (this.isFolia()) {
                com.tcoded.folialib.FoliaLib foliaLib = new com.tcoded.folialib.FoliaLib(PlugMan.getInstance());
                foliaLib.getImpl().runAsync(() -> {
                    // attempt to do the same thing for folia
                });
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // schedule an empty BukkitRunnable to clear/reset the "head" field in CraftScheduler.
                        // that field can keep plugin classes loaded, and scheduling an empty runnable
                        // seems nicer and less harmful than clearing that field with reflection
                    }
                }.runTask(PlugMan.getInstance());
            }

        }

        if (!(PlugMan.getInstance().getBukkitCommandWrap() instanceof BukkitCommandWrapUseless))
            this.unloadCommands(plugin);

        // Attempt to close the classloader to unlock any handles on the plugin's jar file.
        this.closeClassLoader(plugin);

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        PlugmanUnloadPluginEvent event = new PlugmanUnloadPluginEvent(plugin, true);
        Bukkit.getPluginManager().callEvent(event);

        return PlugMan.getInstance().getMessageFormatter().format("unload.unloaded", name);

    }
}
