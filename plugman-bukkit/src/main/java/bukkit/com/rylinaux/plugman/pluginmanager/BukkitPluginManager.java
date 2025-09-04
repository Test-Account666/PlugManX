package bukkit.com.rylinaux.plugman.pluginmanager;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.plugin.BukkitCommand;
import bukkit.com.rylinaux.plugman.plugin.BukkitPlugin;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Command;
import core.com.rylinaux.plugman.plugins.CommandMapWrap;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.util.StringUtil;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Utilities for managing plugins.
 *
 * @author rylinaux
 */
public class BukkitPluginManager extends BasePluginManager {
    private static Runnable syncCommandsRunnable;

    static {
        try {
            // Get the server class and syncCommands method
            var serverClass = Bukkit.getServer().getClass();
            var lookup = MethodHandles.lookup();
            var syncCommandsHandle = lookup.findVirtual(serverClass, "syncCommands", MethodType.methodType(void.class));

            // Create a lambda using LambdaMetaFactory
            syncCommandsRunnable = (Runnable) LambdaMetafactory.metafactory(
                    lookup,
                    "run",
                    MethodType.methodType(Runnable.class, serverClass),
                    MethodType.methodType(void.class),
                    syncCommandsHandle,
                    MethodType.methodType(void.class)
            ).getTarget().invoke(Bukkit.getServer());

        } catch (Throwable throwable) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to initialize syncCommandsRunnable", throwable);

            // Fallback to empty method
            syncCommandsRunnable = () -> {
            };
        }
    }

    private final Class<?> pluginClassLoaderClass;
    private final Function<ClassLoader, org.bukkit.plugin.Plugin> getPluginFromClassLoader;

    public BukkitPluginManager() {
        pluginClassLoaderClass = ClassAccessor.getClass("org.bukkit.plugin.java.PluginClassLoader");

        getPluginFromClassLoader = instance -> {
            try {
                return FieldAccessor.getValue("plugin", instance);
            } catch (IllegalAccessException exception) {
                throw new RuntimeException(exception);
            }
        };
    }

    /**
     * Enable a plugin.
     *
     * @param plugin the plugin to enable
     */
    @Override
    public PluginResult enable(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, "error.invalid-plugin");
        if (plugin.isEnabled()) return new PluginResult(false, "enable.already-enabled");
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        Bukkit.getPluginManager().enablePlugin(bukkitPlugin);
        return new PluginResult(true, "enable.enabled");
    }

    /**
     * Enable all plugins.
     */
    @Override
    public PluginResult enableAll() {
        var results = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(bukkitPlugin -> !isIgnored(bukkitPlugin.getName()) && !isPaperPlugin(new BukkitPlugin(bukkitPlugin)))
                .map(bukkitPlugin -> enable(new BukkitPlugin(bukkitPlugin)))
                .toList();

        //TODO: Show error message, if not successful
        var allSuccessful = results.stream().allMatch(PluginResult::success);
        return new PluginResult(allSuccessful, "plugins.enabled-all");
    }

    /**
     * Disable a plugin.
     *
     * @param plugin the plugin to disable
     */
    @Override
    public PluginResult disable(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, "plugin.null");
        if (!plugin.isEnabled()) return new PluginResult(false, "plugin.already-disabled");
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        Bukkit.getPluginManager().disablePlugin(bukkitPlugin);
        return new PluginResult(true, "plugin.disabled");
    }

    /**
     * Disable all plugins.
     */
    @Override
    public PluginResult disableAll() {
        var results = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(bukkitPlugin -> !isIgnored(bukkitPlugin.getName()) && !isPaperPlugin(new BukkitPlugin(bukkitPlugin)))
                .map(bukkitPlugin -> disable(new BukkitPlugin(bukkitPlugin)))
                .toList();
        var allSuccessful = results.stream().allMatch(PluginResult::success);
        return new PluginResult(allSuccessful, "plugins.disabled-all");
    }

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin the plugin to format
     * @return the formatted name
     */
    @Override
    public String getFormattedName(Plugin plugin) {
        return getFormattedName(plugin, false);
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
        var color = plugin.isEnabled()? ChatColor.GREEN : ChatColor.RED;
        var pluginName = color + plugin.getName();
        if (includeVersions) pluginName += " (" + plugin.getVersion() + ")";
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
        return getPluginByName(StringUtil.consolidateStrings(args, start));
    }

    /**
     * Returns a plugin from a String.
     *
     * @param name the name of the plugin
     * @return the plugin
     */
    @Override
    public Plugin getPluginByName(String name) {
        var bukkitPlugin = Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(plugin -> name.equalsIgnoreCase(plugin.getName())).findFirst().orElse(null);
        return bukkitPlugin != null? new BukkitPlugin(bukkitPlugin) : null;
    }

    /**
     * Returns a List of plugin names.
     *
     * @return list of plugin names
     */
    @Override
    public List<String> getPluginNames(boolean fullName) {
        var plugins = new ArrayList<String>();
        for (var plugin : Bukkit.getPluginManager().getPlugins()) plugins.add(fullName? plugin.getDescription().getFullName() : plugin.getName());
        return plugins;
    }

    /**
     * Returns a List of disabled plugin names.
     *
     * @return list of disabled plugin names
     */
    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        var plugins = new ArrayList<String>();
        for (var plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) continue;
            plugins.add(fullName? plugin.getDescription().getFullName() : plugin.getName());
        }
        return plugins;
    }

    /**
     * Returns a List of enabled plugin names.
     *
     * @return list of enabled plugin names
     */
    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        var plugins = new ArrayList<String>();
        for (var plugin : Bukkit.getPluginManager().getPlugins()) {
            if (!plugin.isEnabled()) continue;
            plugins.add(fullName? plugin.getDescription().getFullName() : plugin.getName());
        }
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
        var plugin = getPluginByName(name);
        return plugin != null? plugin.getVersion() : null;
    }

    /**
     * Returns the commands a plugin has registered.
     *
     * @param plugin the plugin to deal with
     * @return the commands registered
     */
    @Override
    public String getUsages(Plugin plugin) {
        var parsedCommands = getCommandsFromPlugin(plugin).stream().map(s -> {
            var parts = s.getKey().split(":");
            // parts length equals 1 means that the key is the command
            return parts.length == 1? parts[0] : parts[1];
        }).collect(Collectors.joining(", "));


        if (parsedCommands.isEmpty()) return "usage.no-commands";

        return parsedCommands;
    }

    private List<Map.Entry<String, Command>> getCommandsFromPlugin(Plugin plugin) {
        var knownCommands = getKnownCommands();

        return knownCommands.entrySet().stream()
                .filter(entry -> {
                    var name = entry.getKey();
                    if (name.contains(":")) return name.split(":")[0].equalsIgnoreCase(plugin.getName());
                    else {
                        var classLoader = entry.getValue().getHandle().getClass().getClassLoader();
                        return classLoader.getClass() == pluginClassLoaderClass && getPluginFromClassLoader.apply(classLoader) == plugin.getHandle();
                    }
                }).collect(Collectors.toList());
    }

    /**
     * Find which plugin has a given command registered.
     *
     * @param command the command.
     * @return the plugin.
     */
    @Override
    public List<String> findByCommand(String command) {
        var plugins = new ArrayList<String>();

        for (var entry : getKnownCommands().entrySet()) {
            var cl = entry.getValue().getHandle().getClass().getClassLoader();

            if (cl.getClass() != pluginClassLoaderClass) handleNonPluginClassLoaderCommand(entry, command, plugins);
            else handlePluginClassLoaderCommand(entry, command, plugins, cl);
        }

        return plugins;
    }

    private void handleNonPluginClassLoaderCommand(Map.Entry<String, Command> entry, String command, List<String> plugins) {
        var parts = entry.getKey().split(":");

        if (parts.length != 2 || !parts[1].equalsIgnoreCase(command)) return;
        Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(pl -> pl.getName().equalsIgnoreCase(parts[0]))
                .findFirst().ifPresent(plugin -> plugins.add(plugin.getName()));
    }

    private void handlePluginClassLoaderCommand(Map.Entry<String, Command> entry, String command, List<String> plugins, ClassLoader classLoader) {
        var parts = entry.getKey().split(":");
        var cmd = parts[parts.length - 1];

        if (!cmd.equalsIgnoreCase(command)) return;

        var plugin = (JavaPlugin) getPluginFromClassLoader.apply(classLoader);

        if (!plugins.contains(plugin.getName())) plugins.add(plugin.getName());
    }

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    @Override
    public boolean isIgnored(Plugin plugin) {
        return isIgnored(plugin.getName());
    }

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    @Override
    public boolean isIgnored(String plugin) {
        return PlugManBukkit.getInstance().<PlugManConfigurationManager>get(PlugManConfigurationManager.class).getIgnoredPlugins()
                .stream().anyMatch(name -> name.equalsIgnoreCase(plugin));
    }

    /**
     * Loads and enables a plugin.
     *
     * @param name plugin's name
     * @return status message
     */
    @Override
    public PluginResult load(String name) {
        var pluginFile = findPluginFile(name);
        if (pluginFile == null) return new PluginResult(false, "load.cannot-find");

        var target = loadAndEnablePlugin(pluginFile);
        if (target == null) return new PluginResult(false, "load.invalid-plugin");

        scheduleCommandLoading();
        PlugManBukkit.getInstance().getFilePluginMap().put(pluginFile.getName(), target.getName());

        return new PluginResult(true, "load.loaded");
    }

    @ApiStatus.Internal
    public Plugin loadAndEnablePlugin(File pluginFile) {
        try {
            var target = Bukkit.getPluginManager().loadPlugin(pluginFile);
            if (target == null) return null;

            target.onLoad();
            Bukkit.getPluginManager().enablePlugin(target);
            return new BukkitPlugin(target);
        } catch (InvalidDescriptionException | InvalidPluginException exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to load and enable plugin: " + pluginFile.getName(), exception);
            return null;
        }
    }

    public SimpleCommandMap getCommandMap() {
        try {
            var craftBukkitPrefix = Bukkit.getServer().getClass().getPackage().getName();
            var craftServerClass = ClassAccessor.getClass(craftBukkitPrefix + ".CraftServer");
            return FieldAccessor.getValue(craftServerClass, "commandMap", Bukkit.getServer());
        } catch (Exception exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to get command map", exception);
            return null;
        }
    }

    @Override
    public CommandMapWrap<org.bukkit.command.Command> getKnownCommands() {
        try {
            var commandMap = getCommandMap();
            var knownCommands = FieldAccessor.<Map<String, org.bukkit.command.Command>>getValue(SimpleCommandMap.class, "knownCommands", commandMap);

            return new CommandMapWrap<>(knownCommands, BukkitCommand::new);
        } catch (Exception exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to get known commands", exception);
            return null;
        }
    }

    /**
     * Unload a plugin.
     *
     * @param plugin the plugin to unload
     * @return the message to send to the user.
     */
    @Override
    public synchronized PluginResult unload(Plugin plugin) {
        if (!handleGentleUnload(plugin)) return new PluginResult(false, "unload.gentle-failed");

        //syncCommands();

        var unloadData = extractPluginManagerData(plugin);
        if (unloadData == null) return new PluginResult(false, "unload.failed");

        disableAndCleanupPlugin(plugin, unloadData);
        closeClassLoader(plugin);

        // Will not work on processes started with the -XX:+DisableExplicitGC flag, but lets try it anyway.
        // This tries to get around the issue where Windows refuses to unlock jar files that were previously loaded into the JVM.
        System.gc();

        return new PluginResult(true, "unload.unloaded");
    }

    @SneakyThrows
    @ApiStatus.Internal
    public CommonUnloadData extractPluginManagerData(Plugin plugin) {
        var pluginManager = Bukkit.getPluginManager();
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();

        pluginManager.disablePlugin(bukkitPlugin);

        try {
            var plugins = FieldAccessor.<List<org.bukkit.plugin.Plugin>>getValue(pluginManager.getClass(), "plugins", pluginManager);
            var names = FieldAccessor.<Map<String, org.bukkit.plugin.Plugin>>getValue(pluginManager.getClass(), "lookupNames", pluginManager);

            Map<Event, SortedSet<RegisteredListener>> listeners = null;
            var reloadlisteners = true;
            try {
                listeners = FieldAccessor.getValue(pluginManager.getClass(), "listeners", pluginManager);
            } catch (Exception exception) {
                reloadlisteners = false;
            }

            var commandMap = getCommandMap();//FieldAccessor.<SimpleCommandMap>getValue(pluginManager.getClass(), "commandMap", pluginManager);
            var commands = getKnownCommands(); //FieldAccessor.<Map<String, org.bukkit.command.Command>>getValue(SimpleCommandMap.class, "knownCommands", commandMap);

            pluginManager.disablePlugin(bukkitPlugin);

            return new CommonUnloadData(pluginManager, commandMap, plugins, names, commands, listeners, reloadlisteners);
        } catch (Exception exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to extract plugin manager data for plugin: " + plugin.getName(), exception);
            return null;
        }
    }

    private void disableAndCleanupPlugin(Plugin plugin, CommonUnloadData data) {
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        data.pluginManager().disablePlugin(bukkitPlugin);

        cleanupListeners(plugin, data.listeners(), data.reloadListeners());
        cleanupCommands(plugin, data);
        syncCommands();
        removeFromPluginLists(plugin, data);
    }

    private void cleanupCommands(Plugin plugin, CommonUnloadData data) {
        if (data.commandMap() == null) return;

        var modifiedKnownCommands = data.commands();

        for (var entry : modifiedKnownCommands.asMap().entrySet())
            if (entry.getValue().getHandle() instanceof PluginCommand) handlePluginCommand(plugin, data.commandMap(), modifiedKnownCommands, entry);
            else handleNonPluginCommand(plugin, data.commandMap(), modifiedKnownCommands, entry);

        syncCommands();
    }

    @ApiStatus.Internal
    public Map<String, Command> convertBukkitCommands(Map<String, org.bukkit.command.Command> commands) {
        var plugManCommands = new HashMap<String, Command>();
        for (var entry : commands.entrySet()) {
            var commandName = entry.getKey();
            var command = entry.getValue();

            plugManCommands.put(commandName, new BukkitCommand(command));
        }

        return plugManCommands;
    }

    @ApiStatus.Internal
    public void handleNonPluginCommand(Plugin plugin, SimpleCommandMap commandMap,
                                       CommandMapWrap<org.bukkit.command.Command> modifiedKnownCommands,
                                       Map.Entry<String, Command> entry) {
        try {
            unregisterNonPluginCommands(plugin, commandMap, modifiedKnownCommands, entry);
        } catch (IllegalStateException exception) {
            if (!exception.getMessage().equalsIgnoreCase("zip file closed")) return;
            handleBrokenCommand(entry, commandMap, modifiedKnownCommands, BukkitPluginManager.class.getName());
        }
    }

    @ApiStatus.Internal
    public void unregisterNonPluginCommands(Plugin plugin, CommandMap commandMap, CommandMapWrap<org.bukkit.command.Command> commands,
                                            Map.Entry<String, Command> entry) {
        var command = entry.getValue();
        var handle = command.<org.bukkit.command.Command>getHandle();

        var pluginField = FieldAccessor.getFirstFieldName(handle.getClass(), Plugin.class);

        try {
            var owningPlugin = FieldAccessor.<org.bukkit.plugin.Plugin>getValue(handle.getClass(), pluginField, handle);
            if (owningPlugin != null && owningPlugin.getName().equalsIgnoreCase(plugin.getName())) {
                handle.unregister(commandMap);
                commands.remove(entry.getKey());
            }
        } catch (IllegalAccessException exception) {
            PlugManBukkit.getInstance().getLogger().log(Level.SEVERE, "Failed to unregister command for plugin: " + plugin.getName(), exception);
        }
    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        return false;
    }

    @Override
    public Set<Plugin> getPlugins() {
        return Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(BukkitPlugin::new).collect(Collectors.toSet());
    }

    @ApiStatus.Internal
    @Override
    public synchronized void syncCommands() {
        syncCommandsRunnable.run();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }
}
