package bukkit.com.rylinaux.plugman.pluginmanager;

/*-
 * #%L
 * PlugManX Core
 * %%
 * Copyright (C) 2010 - 2025 plugmanx-core
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

import bukkit.com.rylinaux.plugman.PlugMan;
import bukkit.com.rylinaux.plugman.api.PlugManAPI;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.ApiStatus;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class containing common functionality shared across plugin managers.
 */
public abstract class BasePluginManager implements PluginManager {

    /**
     * Handles gentle unload logic common to all plugin managers.
     */
    protected boolean handleGentleUnload(Plugin plugin) {
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        if (!PlugManAPI.getGentleUnloads().containsKey(bukkitPlugin)) return true;
        var gentleUnload = PlugManAPI.getGentleUnloads().get(bukkitPlugin);
        return gentleUnload.askingForGentleUnload();
    }

    /**
     * Common listener cleanup logic.
     */
    protected void cleanupListeners(Plugin plugin, Map<Event, SortedSet<RegisteredListener>> listeners, boolean reloadListeners) {
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        if (listeners != null && reloadListeners) listeners.values().forEach(set -> set.removeIf(value -> value.getPlugin() == bukkitPlugin));
    }

    /**
     * Common plugin list removal logic.
     */
    @ApiStatus.Internal
    public void removeFromPluginLists(Plugin plugin, CommonUnloadData data) {
        if (data.plugins() != null) data.plugins().removeIf(otherPlugin -> otherPlugin.getName().equalsIgnoreCase(plugin.getName()));
        if (data.names() != null) data.names().remove(plugin.getName());
    }

    /**
     * Common class loader closing logic.
     */
    protected void closeClassLoader(Plugin plugin) {
        var classLoader = plugin.getHandle().getClass().getClassLoader();
        if (!(classLoader instanceof URLClassLoader)) return;

        try {
            FieldAccessor.setValue("plugin", classLoader, null);

            FieldAccessor.setValue("pluginInit", classLoader, null);
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException exception) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error removing class load from plugin", exception);
        }

        try {
            ((Closeable) classLoader).close();
        } catch (IOException exception) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error closing plugin classloader", exception);
        }
    }

    /**
     * Common plugin file finding logic.
     */
    protected File findPluginFile(String name) {
        var pluginDir = new File("plugins");
        if (!pluginDir.isDirectory()) return null;

        var pluginFile = new File(pluginDir, name + ".jar");
        if (pluginFile.isFile()) return pluginFile;

        // Search for plugin by name in all jar files
        for (var f : pluginDir.listFiles())
            if (f.getName().endsWith(".jar")) try {
                var desc = PlugMan.getInstance().getPluginLoader().getPluginDescription(f);
                if (desc.getName().equalsIgnoreCase(name)) return f;
            } catch (Exception exception) {
                PlugMan.getInstance().getLogger().warning("Failed to read descriptor for " + f.getName() + " - skipping");
            }

        return null;
    }

    /**
     * Common plugin command handling logic.
     */
    protected void handlePluginCommand(Plugin plugin, SimpleCommandMap commandMap,
                                       Map<String, Command> modifiedKnownCommands,
                                       Map.Entry<String, Command> entry) {
        var command = (PluginCommand) entry.getValue();
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        if (command.getPlugin() != bukkitPlugin) return;
        command.unregister(commandMap);
        modifiedKnownCommands.remove(entry.getKey());
    }

    /**
     * Common broken command removal logic.
     */
    protected void handleBrokenCommand(Map.Entry<String, Command> entry, SimpleCommandMap commandMap,
                                       Map<String, Command> modifiedKnownCommands, String loggerName) {
        var config = PlugMan.getInstance().<PlugManConfigurationManager>get(PlugManConfigurationManager.class);

        if (config.shouldNotifyOnBrokenCommandRemoval())
            Logger.getLogger(loggerName).info("Removing broken command '" + entry.getValue().getName() + "'!");
        entry.getValue().unregister(commandMap);
        modifiedKnownCommands.remove(entry.getKey());
    }

    /**
     * Common command loading logic.
     */
    protected void scheduleCommandLoading() {
        Bukkit.getScheduler().runTaskLater(PlugMan.getInstance(), this::syncCommands, 10L);
    }


    /**
     * Abstract method for reloading commands.
     */
    protected abstract void syncCommands();

    /**
     * Common data structure for unload operations.
     */
    public record CommonUnloadData(org.bukkit.plugin.PluginManager pluginManager, SimpleCommandMap commandMap, List<org.bukkit.plugin.Plugin> plugins,
                                   Map<String, org.bukkit.plugin.Plugin> names, Map<String, org.bukkit.command.Command> commands,
                                   Map<Event, SortedSet<RegisteredListener>> listeners,
                                   boolean reloadListeners) {
    }
}
