package bungee.com.rylinaux.plugman.pluginmanager;

import bungee.com.rylinaux.plugman.PlugManBungee;
import bungee.com.rylinaux.plugman.plugin.BungeeCommand;
import bungee.com.rylinaux.plugman.plugin.BungeePlugin;
import com.google.common.collect.Multimap;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Command;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import lombok.SneakyThrows;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.yaml.snakeyaml.Yaml;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class BungeePluginManager implements PluginManager {


    @Override
    public PluginResult enable(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, "error.invalid-plugin");
        if (plugin.isEnabled()) return new PluginResult(false, "enable.already-enabled");
        var bungeePlugin = plugin.<net.md_5.bungee.api.plugin.Plugin>getHandle();
        try {
            bungeePlugin.onEnable();
            return new PluginResult(true, "enable.enabled");
        } catch (Exception e) {
            PlugManBungee.getInstance().getLogger().log(Level.SEVERE, "Error enabling plugin " + plugin.getName(), e);
            return new PluginResult(false, "enable.failed");
        }
    }

    @Override
    public PluginResult enableAll() {
        var plugins = getPlugins();
        var results = plugins.stream()
                .filter(plugin -> !isIgnored(plugin))
                .map(this::enable)
                .toList();
        var allSuccessful = results.stream().allMatch(PluginResult::success);
        return new PluginResult(allSuccessful, "plugins.enabled-all");
    }

    @Override
    public PluginResult disable(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, "plugin.null");
        if (!plugin.isEnabled()) return new PluginResult(false, "plugin.already-disabled");
        var result = unload(plugin);
        if (result.success()) return new PluginResult(true, "plugin.disabled");
        return result;
    }

    @Override
    public PluginResult disableAll() {
        var plugins = getPlugins();
        var results = plugins.stream()
                .filter(plugin -> !isIgnored(plugin))
                .map(this::disable)
                .toList();
        var allSuccessful = results.stream().allMatch(PluginResult::success);
        return new PluginResult(allSuccessful, "plugins.disabled-all");
    }

    @Override
    public String getFormattedName(Plugin plugin) {
        return getFormattedName(plugin, false);
    }

    @Override
    public String getFormattedName(Plugin plugin, boolean includeVersions) {
        var name = plugin.getName();
        if (includeVersions) name += " v" + plugin.getVersion();
        return plugin.isEnabled()? "§a" + name : "§c" + name;
    }

    @Override
    public Plugin getPluginByName(String[] args, int start) {
        if (args.length <= start) return null;
        var name = String.join(" ", Arrays.copyOfRange(args, start, args.length));
        return getPluginByName(name);
    }

    @Override
    public Plugin getPluginByName(String name) {
        name = name.trim();

        var bungeePlugin = ProxyServer.getInstance().getPluginManager().getPlugin(name);
        return bungeePlugin != null? new BungeePlugin(bungeePlugin) : null;
    }

    @Override
    public List<String> getPluginNames(boolean fullName) {
        return ProxyServer.getInstance().getPluginManager().getPlugins().stream()
                .map(p -> fullName? getFormattedName(new BungeePlugin(p), true) : p.getDescription().getName())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        // Bungee doesn't have disabled plugins - they're either loaded or not
        return new ArrayList<>();
    }

    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        return getPluginNames(fullName);
    }

    @Override
    public String getPluginVersion(String name) {
        var plugin = getPluginByName(name);
        return plugin != null? plugin.getVersion() : null;
    }

    @SneakyThrows
    @Override
    public String getUsages(Plugin plugin) {
        var pluginManager = ProxyServer.getInstance().getPluginManager();
        var commandsByPlugin = FieldAccessor.<Multimap<net.md_5.bungee.api.plugin.Plugin, net.md_5.bungee.api.plugin.Command>>getValue(
                net.md_5.bungee.api.plugin.PluginManager.class, "commandsByPlugin", pluginManager);

        var commands = commandsByPlugin.get(plugin.getHandle());

        var usages = "";

        for (var command : commands)
            usages = Arrays.stream(command.getAliases()).map(alias -> alias + " ")
                    .collect(Collectors.joining("", command.getName() + " ", ""));

        return usages.trim().replace(" ", ", ");
    }

    @Override
    public List<String> findByCommand(String command) {
        var plugins = new ArrayList<String>();

        try {
            var pluginManager = ProxyServer.getInstance().getPluginManager();

            // Get the commandMap to find the command
            var commandMap = FieldAccessor.<Map<String, net.md_5.bungee.api.plugin.Command>>getValue(
                    net.md_5.bungee.api.plugin.PluginManager.class, "commandMap", pluginManager);

            // Get the commandsByPlugin multimap to find which plugin owns the command
            var commandsByPlugin = FieldAccessor.<Multimap<net.md_5.bungee.api.plugin.Plugin, net.md_5.bungee.api.plugin.Command>>getValue(
                    net.md_5.bungee.api.plugin.PluginManager.class, "commandsByPlugin", pluginManager);

            if (commandMap == null || commandsByPlugin == null) return plugins;

            var foundCommand = commandMap.get(command.toLowerCase());

            if (foundCommand != null) for (var entry : commandsByPlugin.entries()) {
                if (!entry.getValue().equals(foundCommand)) continue;
                var pluginName = entry.getKey().getDescription().getName();
                if (!plugins.contains(pluginName)) plugins.add(pluginName);
            }

            for (var entry : commandMap.entrySet()) {
                var commandName = entry.getKey();
                if (!commandName.contains(":")) continue;
                var parts = commandName.split(":");
                if (parts.length != 2 || !parts[1].equalsIgnoreCase(command)) continue;
                if (!plugins.contains(parts[0])) plugins.add(parts[0]);
            }
        } catch (Exception exception) {
            PlugManBungee.getInstance().getLogger().log(Level.WARNING, "Failed to find command: " + command, exception);
        }

        return plugins;
    }

    @Override
    public boolean isIgnored(Plugin plugin) {
        return isIgnored(plugin.getName());
    }

    @Override
    public boolean isIgnored(String plugin) {
        return PlugManBungee.getInstance().<PlugManConfigurationManager>get(PlugManConfigurationManager.class).getIgnoredPlugins().contains(plugin);
    }

    @Override
    public PluginResult load(String name) {
        var file = findPluginFile(name);
        if (file == null) return new PluginResult(false, "load.cannot-find");
        var result = loadPluginFromFile(file);
        if (result.success()) return new PluginResult(true, "load.loaded");
        return new PluginResult(false, "load.invalid-plugin");
    }

    @Override
    public Map<String, Command> getKnownCommands() {
        try {
            var pluginManager = ProxyServer.getInstance().getPluginManager();
            var commandMap = FieldAccessor.<Map<String, net.md_5.bungee.api.plugin.Command>>getValue(
                    net.md_5.bungee.api.plugin.PluginManager.class, "commandMap", pluginManager);

            if (commandMap == null) return new HashMap<>();

            return convertBungeeCommands(commandMap);
        } catch (Exception exception) {
            PlugManBungee.getInstance().getLogger().log(Level.SEVERE, "Failed to get known commands", exception);
            return new HashMap<>();
        }
    }

    @Override
    public void setKnownCommands(Map<String, Command> knownCommands) {
        try {
            var pluginManager = ProxyServer.getInstance().getPluginManager();
            var bungeeCommands = convertPlugManCommands(knownCommands);

            FieldAccessor.setValue(net.md_5.bungee.api.plugin.PluginManager.class, "commandMap", pluginManager, bungeeCommands);
        } catch (Exception exception) {
            PlugManBungee.getInstance().getLogger().log(Level.SEVERE, "Failed to set known commands", exception);
            throw new RuntimeException("Failed to set known commands", exception);
        }
    }

    private Map<String, Command> convertBungeeCommands(Map<String, net.md_5.bungee.api.plugin.Command> bungeeCommands) {
        var plugManCommands = new HashMap<String, Command>();
        for (var entry : bungeeCommands.entrySet()) {
            var commandName = entry.getKey();
            var command = entry.getValue();

            plugManCommands.put(commandName, new BungeeCommand(command));
        }

        return plugManCommands;
    }

    private Map<String, net.md_5.bungee.api.plugin.Command> convertPlugManCommands(Map<String, Command> plugManCommands) {
        var bungeeCommands = new HashMap<String, net.md_5.bungee.api.plugin.Command>();
        for (var entry : plugManCommands.entrySet()) {
            var commandName = entry.getKey();
            var command = entry.getValue();

            bungeeCommands.put(commandName, command.getHandle());
        }

        return bungeeCommands;
    }

    @Override
    public PluginResult unload(Plugin plugin) {
        var bungeePlugin = plugin.<net.md_5.bungee.api.plugin.Plugin>getHandle();
        return unloadBungeePlugin(bungeePlugin);
    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        // Bungee plugins are not Paper plugins
        return false;
    }

    @Override
    public Set<Plugin> getPlugins() {
        return ProxyServer.getInstance().getPluginManager().getPlugins().stream()
                .map(BungeePlugin::new)
                .collect(Collectors.toSet());
    }

    // Helper methods adapted from the original static methods

    private File findPluginFile(String name) {
        var pluginDir = new File("plugins");
        if (!pluginDir.isDirectory()) return null;
        if (!name.toLowerCase().endsWith(".jar")) name += ".jar";

        var pluginFile = new File(pluginDir, name);
        if (pluginFile.isFile()) return pluginFile;

        // Search for plugin by name in all jar files
        for (var f : pluginDir.listFiles())
            if (f.getName().endsWith(".jar")) try (var jar = new JarFile(f)) {
                var pdf = jar.getJarEntry("bungee.yml");
                if (pdf != null) try (var in = jar.getInputStream(pdf)) {
                    var yaml = new Yaml();
                    var desc = yaml.loadAs(in, PluginDescription.class);
                    if (desc.getName().equalsIgnoreCase(name)) return f;
                }
            } catch (Exception e) {
                // Ignore and continue
            }
        return null;
    }

    private PluginResult loadPluginFromFile(File file) {
        var pluginManager = ProxyServer.getInstance().getPluginManager();

        Yaml yaml;
        try {
            yaml = FieldAccessor.getValue(net.md_5.bungee.api.plugin.PluginManager.class, "yaml", pluginManager);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return new PluginResult(false, "load.invalid-plugin");
        }

        if (yaml == null) return new PluginResult(false, "load.invalid-plugin");

        HashMap<String, PluginDescription> toLoad;
        try {
            toLoad = FieldAccessor.getValue(net.md_5.bungee.api.plugin.PluginManager.class, "toLoad", pluginManager);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return new PluginResult(false, "load.invalid-plugin");
        }

        if (toLoad == null) toLoad = new HashMap<>();

        if (file.isFile()) {
            PluginDescription desc;

            try (var jar = new JarFile(file)) {
                var pdf = jar.getJarEntry("bungee.yml");
                if (pdf == null) pdf = jar.getJarEntry("plugin.yml");

                if (pdf == null)
                    return new PluginResult(false, "load.invalid-plugin");

                try (var in = jar.getInputStream(pdf)) {
                    desc = yaml.loadAs(in, PluginDescription.class);

                    if (desc.getName() == null)
                        return new PluginResult(false, "load.invalid-plugin");

                    if (desc.getMain() == null)
                        return new PluginResult(false, "load.invalid-plugin");

                    if (pluginManager.getPlugin(desc.getName()) != null)
                        return new PluginResult(false, "load.invalid-plugin");

                    desc.setFile(file);
                    toLoad.put(desc.getName(), desc);
                }

                try {
                    FieldAccessor.setValue(net.md_5.bungee.api.plugin.PluginManager.class, "toLoad", pluginManager, toLoad);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return new PluginResult(false, "load.invalid-plugin");
                }
                pluginManager.loadPlugins();

                var plugin = pluginManager.getPlugin(desc.getName());
                if (plugin == null)
                    return new PluginResult(false, "load.invalid-plugin");
                plugin.onEnable();
            } catch (Exception ex) {
                ProxyServer.getInstance().getLogger().log(Level.WARNING, "Could not load plugin from file " + file, ex);
                return new PluginResult(false, "load.invalid-plugin");
            }
        }
        return new PluginResult(true, "load.loaded");
    }

    private PluginResult unloadBungeePlugin(net.md_5.bungee.api.plugin.Plugin plugin) {
        var pluginManager = ProxyServer.getInstance().getPluginManager();
        try {
            plugin.onDisable();
            for (var handler : plugin.getLogger().getHandlers()) handler.close();
        } catch (Throwable t) {
            PlugManBungee.getInstance().getLogger().log(Level.SEVERE, "Exception disabling plugin '" + plugin.getDescription().getName() + "'", t);
        }

        pluginManager.unregisterCommands(plugin);
        pluginManager.unregisterListeners(plugin);
        ProxyServer.getInstance().getScheduler().cancel(plugin);
        plugin.getExecutorService().shutdownNow();

        Map<String, net.md_5.bungee.api.plugin.Plugin> plugins;
        try {
            plugins = FieldAccessor.getValue(net.md_5.bungee.api.plugin.PluginManager.class, "plugins", pluginManager);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return new PluginResult(false, "unload.failed");
        }

        if (plugins == null)
            return new PluginResult(false, "unload.failed");

        plugins.remove(plugin.getDescription().getName());

        var cl = plugin.getClass().getClassLoader();

        if (cl instanceof URLClassLoader) {
            try {
                FieldAccessor.setValue(cl.getClass(), "plugin", cl, null);
                FieldAccessor.setValue(cl.getClass(), "desc", cl, null);

                var allLoaders = FieldAccessor.<Set<?>>getValue(cl.getClass(), "allLoaders", cl);
                if (allLoaders != null) allLoaders.remove(cl);

            } catch (IllegalAccessException ex) {
                PlugManBungee.getInstance().getLogger().log(Level.SEVERE, null, ex);
                return new PluginResult(false, "unload.failed");
            }

            try {
                ((Closeable) cl).close();
            } catch (IOException ex) {
                PlugManBungee.getInstance().getLogger().log(Level.SEVERE, null, ex);
                return new PluginResult(false, "unload.failed");
            }
        }

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();
        return new PluginResult(true, "unload.unloaded");
    }

}