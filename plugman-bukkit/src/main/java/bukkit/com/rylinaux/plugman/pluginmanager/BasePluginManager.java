package bukkit.com.rylinaux.plugman.pluginmanager;

/*-
 * #%L
 * PlugManX Core
 * %%
 * Copyright (C) 2010 - 2025 plugman-core
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

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.api.PlugManAPI;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Command;
import core.com.rylinaux.plugman.plugins.CommandMapWrap;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.ThreadUtil;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.ApiStatus;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yaml.snakeyaml.Yaml;

/**
 * Base class containing common functionality shared across plugin managers.
 */
public abstract class BasePluginManager implements PluginManager {
    private static final String BUKKIT_PLUGIN_YML = "plugin.yml";
    private static final String PAPER_PLUGIN_YML = "paper-plugin.yml";
    private final Set<String> loadingPlugins = ConcurrentHashMap.newKeySet();
    private int commandUpdateBatchDepth = 0;
    private boolean commandSyncPending = false;

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
     * Common permission cleanup logic.
     */
    protected void cleanupPermissions(Plugin plugin) {
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        var permissionNames = new ArrayList<String>();

        for (var permission : bukkitPlugin.getDescription().getPermissions()) {
            permissionNames.add(permission.getName());
            permissionNames.addAll(permission.getChildren().keySet());
        }

        var pluginPrefix = bukkitPlugin.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        Bukkit.getPluginManager().getPermissions().stream()
                .map(org.bukkit.permissions.Permission::getName)
                .filter(permissionName -> permissionName.toLowerCase().startsWith(pluginPrefix + "."))
                .forEach(permissionNames::add);

        permissionNames.stream()
                .distinct()
                .forEach(permissionName -> {
                    try {
                        Bukkit.getPluginManager().removePermission(permissionName);
                    } catch (IllegalArgumentException ignored) {
                        // Permission was already removed or never registered.
                    }
                });
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
            clearClassLoaderField(classLoader, "plugin");
            clearClassLoaderField(classLoader, "pluginInit");
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException exception) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error removing class load from plugin", exception);
        }

        try {
            ((Closeable) classLoader).close();
        } catch (IOException exception) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error closing plugin classloader", exception);
        }
    }

    private void clearClassLoaderField(ClassLoader classLoader, String fieldName) throws IllegalAccessException {
        if (FieldAccessor.getField(classLoader.getClass(), fieldName) == null) return;
        FieldAccessor.setValue(fieldName, classLoader, null);
    }

    /**
     * Reports server registrations that still reference an unloaded plugin.
     */
    protected void reportUnloadLeaks(Plugin plugin) {
        var pluginHandle = plugin.<org.bukkit.plugin.Plugin>getHandle();
        var pluginLoader = pluginHandle.getClass().getClassLoader();
        var leaks = new LinkedHashMap<String, List<String>>();

        addLeakCategory(leaks, "tasks", collectTaskLeaks(pluginHandle));
        addLeakCategory(leaks, "services", collectServiceLeaks(pluginHandle));
        addLeakCategory(leaks, "listeners", collectListenerLeaks(pluginHandle));
        addLeakCategory(leaks, "commands", collectCommandLeaks(pluginHandle, pluginLoader));
        addLeakCategory(leaks, "threads", collectThreadLeaks(pluginLoader));

        if (leaks.isEmpty()) return;

        var logger = PlugManBukkit.getInstance().getLogger();
        logger.warning("[UnloadLeakDetector] " + plugin.getName()
                + " still has references after unload; its old classloader may not be garbage-collected.");
        leaks.forEach((category, details) -> logger.warning("[UnloadLeakDetector] " + plugin.getName() + " "
                + category + " (" + details.size() + "): " + summarizeLeakDetails(details)));
    }

    private List<String> collectTaskLeaks(org.bukkit.plugin.Plugin plugin) {
        try {
            var tasks = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            Bukkit.getScheduler().getPendingTasks().stream()
                    .filter(task -> task.getOwner() == plugin)
                    .map(task -> "#" + task.getTaskId() + " pending")
                    .forEach(tasks::add);
            Bukkit.getScheduler().getActiveWorkers().stream()
                    .filter(worker -> worker.getOwner() == plugin)
                    .map(worker -> "#" + worker.getTaskId() + " active on " + worker.getThread().getName())
                    .forEach(tasks::add);
            return List.copyOf(tasks);
        } catch (RuntimeException | LinkageError exception) {
            logLeakCheckFailure("tasks", exception);
            return List.of();
        }
    }

    private List<String> collectServiceLeaks(org.bukkit.plugin.Plugin plugin) {
        try {
            return Bukkit.getServicesManager().getRegistrations(plugin).stream()
                    .map(registration -> registration.getService().getName() + " -> "
                            + registration.getProvider().getClass().getName())
                    .toList();
        } catch (RuntimeException | LinkageError exception) {
            logLeakCheckFailure("services", exception);
            return List.of();
        }
    }

    private List<String> collectListenerLeaks(org.bukkit.plugin.Plugin plugin) {
        try {
            var listeners = new ArrayList<String>();
            for (var handlerList : HandlerList.getHandlerLists()) {
                for (var registration : handlerList.getRegisteredListeners()) {
                    if (registration.getPlugin() == plugin) {
                        listeners.add(registration.getListener().getClass().getName());
                    }
                }
            }
            return listeners.stream().distinct().sorted().toList();
        } catch (RuntimeException | LinkageError exception) {
            logLeakCheckFailure("listeners", exception);
            return List.of();
        }
    }

    private List<String> collectCommandLeaks(org.bukkit.plugin.Plugin plugin, ClassLoader pluginLoader) {
        try {
            var knownCommands = getKnownCommands();
            if (knownCommands == null) return List.of();

            var pluginPrefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
            return knownCommands.asMap().entrySet().stream()
                    .filter(entry -> commandReferencesPlugin(entry.getKey(), entry.getValue().getHandle(),
                            plugin, pluginLoader, pluginPrefix))
                    .map(Map.Entry::getKey)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (RuntimeException | LinkageError exception) {
            logLeakCheckFailure("commands", exception);
            return List.of();
        }
    }

    private boolean commandReferencesPlugin(String key,
                                            Object command,
                                            org.bukkit.plugin.Plugin plugin,
                                            ClassLoader pluginLoader,
                                            String pluginPrefix) {
        if (key.toLowerCase(Locale.ROOT).startsWith(pluginPrefix)) return true;
        if (command instanceof PluginCommand pluginCommand && pluginCommand.getPlugin() == plugin) return true;
        return command != null && command.getClass().getClassLoader() == pluginLoader;
    }

    private List<String> collectThreadLeaks(ClassLoader pluginLoader) {
        try {
            var threads = new ArrayList<String>();
            for (var thread : Thread.getAllStackTraces().keySet()) {
                if (!thread.isAlive()) continue;
                if (thread.getClass().getClassLoader() != pluginLoader
                        && thread.getContextClassLoader() != pluginLoader) continue;
                threads.add(thread.getName() + " [" + thread.getState() + "]");
            }
            return threads.stream().distinct().sorted().toList();
        } catch (RuntimeException | LinkageError exception) {
            logLeakCheckFailure("threads", exception);
            return List.of();
        }
    }

    private void addLeakCategory(Map<String, List<String>> leaks, String category, Collection<String> details) {
        if (!details.isEmpty()) leaks.put(category, List.copyOf(details));
    }

    private String summarizeLeakDetails(List<String> details) {
        var visibleDetails = details.stream().limit(10).toList();
        var summary = String.join(", ", visibleDetails);
        return details.size() > visibleDetails.size()
                ? summary + " (and " + (details.size() - visibleDetails.size()) + " more)"
                : summary;
    }

    private void logLeakCheckFailure(String category, Throwable throwable) {
        PlugManBukkit.getInstance().getLogger().log(Level.FINE,
                "[UnloadLeakDetector] Could not inspect " + category, throwable);
    }

    /**
     * Common plugin file finding logic.
     */
    protected File findPluginFile(String name) {
        var pluginDir = new File("plugins");
        if (!pluginDir.isDirectory()) return null;

        var pluginFile = new File(pluginDir, name + ".jar");
        if (pluginFile.isFile()) return pluginFile;

        var files = pluginDir.listFiles();
        if (files == null) return null;

        for (var file : files) {
            var found = findPluginFileFromJar(file, name);
            if (found != null) return found;
        }

        return null;
    }

    private File findPluginFileFromJar(File file, String name) {
        if (!file.getName().endsWith(".jar")) return null;

        var pluginName = getPluginName(file);
        if (pluginName != null && pluginName.equalsIgnoreCase(name)) return file;
        if (pluginName != null) return null;

        PlugManBukkit.getInstance().getLogger().warning("Failed to read descriptor for " + file.getName() + " - skipping");
        return null;
    }

    protected String getPluginName(File file) {
        var pluginName = getBukkitPluginName(file);
        return pluginName != null ? pluginName : getPaperPluginName(file);
    }

    protected PluginLoadPreflight preflightPluginLoad(String name) {
        var pluginDir = new File("plugins");
        if (!pluginDir.isDirectory()) return PluginLoadPreflight.failed(new PluginResult(false, "load.plugin-dir"));

        var pluginFile = findPluginFile(name);
        if (pluginFile == null || !pluginFile.isFile()) return PluginLoadPreflight.failed(new PluginResult(false, "load.cannot-find"));

        var descriptor = readPluginDescriptor(pluginFile);
        if (descriptor == null) return PluginLoadPreflight.failed(new PluginResult(false, "load.invalid-plugin", pluginFile.getName()));
        if (descriptor.name() == null || descriptor.name().isBlank())
            return PluginLoadPreflight.failed(new PluginResult(false, "load.invalid-description", pluginFile.getName()));

        var loadedPlugin = getPluginByName(descriptor.name());
        if (loadedPlugin != null) return PluginLoadPreflight.failed(new PluginResult(false, "load.already-loaded", loadedPlugin.getName()));

        var unavailableDependencies = descriptor.requiredDependencies().stream()
                .filter(dependency -> !isDependencySatisfied(dependency))
                .filter(dependency -> getPluginByName(dependency) == null)
                .filter(dependency -> findPluginFile(dependency) == null)
                .toList();
        if (!unavailableDependencies.isEmpty()) {
            return PluginLoadPreflight.failed(new PluginResult(false, "load.missing-dependencies",
                    descriptor.name(), String.join(", ", unavailableDependencies)));
        }

        return new PluginLoadPreflight(pluginFile, descriptor, new PluginResult(true, "validation.success"));
    }

    protected boolean beginPluginLoad(PluginDescriptor descriptor) {
        return loadingPlugins.add(descriptor.name());
    }

    protected void finishPluginLoad(PluginDescriptor descriptor) {
        loadingPlugins.remove(descriptor.name());
    }

    protected PluginResult loadRequiredDependencies(PluginDescriptor descriptor) {
        for (var dependency : descriptor.requiredDependencies()) {
            var loadedDependency = getPluginByName(dependency);
            if (loadedDependency != null && !loadedDependency.isEnabled()) {
                var enableResult = enable(loadedDependency);
                if (!enableResult.success()) return enableResult;
                continue;
            }

            if (isDependencySatisfied(dependency)) continue;
            if (loadingPlugins.contains(dependency)) {
                return new PluginResult(false, "load.missing-dependencies", descriptor.name(), dependency);
            }

            var result = load(dependency);
            if (result.success()) continue;

            return result;
        }

        return new PluginResult(true, "validation.success");
    }

    private boolean isDependencySatisfied(String dependency) {
        var dependencyPlugin = getPluginByName(dependency);
        if (dependencyPlugin != null) return dependencyPlugin.isEnabled();

        return Arrays.stream(org.bukkit.Bukkit.getPluginManager().getPlugins())
                .filter(org.bukkit.plugin.Plugin::isEnabled)
                .anyMatch(candidate -> candidate.getDescription().getProvides().stream()
                        .anyMatch(providedName -> providedName.equalsIgnoreCase(dependency)));
    }

    private String getBukkitPluginName(File file) {
        var description = readBukkitPluginDescription(file);
        return description == null ? null : description.getName();
    }

    private String getPaperPluginName(File file) {
        try (var jar = new JarFile(file)) {
            var entry = jar.getJarEntry(PAPER_PLUGIN_YML);
            if (entry == null) return null;

            try (var input = jar.getInputStream(entry)) {
                var data = new Yaml().load(input);
                if (!(data instanceof Map<?, ?> map)) return null;

                var name = map.get("name");
                return name == null ? null : name.toString();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private PluginDescriptor readPluginDescriptor(File file) {
        var bukkitDescriptor = readBukkitPluginDescriptor(file);
        var paperDescriptor = readPaperPluginDescriptor(file);
        if (bukkitDescriptor == null) return paperDescriptor;
        if (paperDescriptor == null) return bukkitDescriptor;

        var requiredDependencies = new ArrayList<>(bukkitDescriptor.requiredDependencies());
        addMissingDependencies(requiredDependencies, paperDescriptor.requiredDependencies());

        var name = paperDescriptor.name() == null || paperDescriptor.name().isBlank()
                ? bukkitDescriptor.name()
                : paperDescriptor.name();
        return new PluginDescriptor(name, true, requiredDependencies);
    }

    private PluginDescriptor readBukkitPluginDescriptor(File file) {
        var description = readBukkitPluginDescription(file);
        return description == null ? null : new PluginDescriptor(description.getName(), false, new ArrayList<>(description.getDepend()));
    }

    private PluginDescriptionFile readBukkitPluginDescription(File file) {
        var description = readBukkitPluginDescriptionFromJar(file);
        return description == null ? readBukkitPluginDescriptionWithLegacyLoader(file) : description;
    }

    private PluginDescriptionFile readBukkitPluginDescriptionFromJar(File file) {
        try (var jar = new JarFile(file)) {
            var entry = jar.getJarEntry(BUKKIT_PLUGIN_YML);
            if (entry == null) return null;

            try (var input = jar.getInputStream(entry)) {
                return new PluginDescriptionFile(input);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private PluginDescriptionFile readBukkitPluginDescriptionWithLegacyLoader(File file) {
        try {
            var plugin = PlugManBukkit.getInstance();
            var getPluginLoader = plugin.getClass().getMethod("getPluginLoader");
            var pluginLoader = getPluginLoader.invoke(plugin);
            var getPluginDescription = pluginLoader.getClass().getMethod("getPluginDescription", File.class);
            var description = getPluginDescription.invoke(pluginLoader, file);
            return description instanceof PluginDescriptionFile pluginDescription ? pluginDescription : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private PluginDescriptor readPaperPluginDescriptor(File file) {
        try (var jar = new JarFile(file)) {
            var entry = jar.getJarEntry(PAPER_PLUGIN_YML);
            if (entry == null) return null;

            try (var input = jar.getInputStream(entry)) {
                var data = new Yaml().load(input);
                if (!(data instanceof Map<?, ?> map)) return null;

                var name = map.get("name");
                var requiredDependencies = new ArrayList<String>();
                addListValues(requiredDependencies, map.get("depend"));
                addRequiredPaperDependencies(requiredDependencies, map.get("dependencies"));
                addRequiredPaperDependencies(requiredDependencies, map.get("serverDependencies"));
                addRequiredPaperDependencies(requiredDependencies, map.get("bootstrapDependencies"));

                return new PluginDescriptor(name == null ? null : name.toString(), true, requiredDependencies);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addListValues(List<String> target, Object value) {
        if (!(value instanceof List<?> list)) return;

        list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(dependency -> !dependency.isBlank())
                .forEach(target::add);
    }

    private void addMissingDependencies(List<String> target, List<String> dependencies) {
        for (var dependency : dependencies) {
            if (dependency == null || dependency.isBlank()) continue;
            if (containsDependency(target, dependency)) continue;
            target.add(dependency);
        }
    }

    private boolean containsDependency(List<String> dependencies, String dependency) {
        return dependencies.stream().anyMatch(existing -> existing.equalsIgnoreCase(dependency));
    }

    private void addRequiredPaperDependencies(List<String> target, Object value) {
        if (value instanceof List<?> dependencyList) {
            addPaperDependencyList(target, dependencyList);
            return;
        }

        if (!(value instanceof Map<?, ?> dependencies)) return;

        for (var entry : dependencies.entrySet()) {
            if (entry.getKey() instanceof String dependencyName && isPaperDependencySettings(entry.getValue())) {
                addRequiredPaperDependency(target, dependencyName, entry.getValue());
                continue;
            }

            if (entry.getValue() instanceof Map<?, ?> dependencySection) {
                addPaperDependencySection(target, dependencySection);
            }
        }
    }

    private void addPaperDependencyList(List<String> target, List<?> dependencyList) {
        for (var dependency : dependencyList) {
            if (dependency instanceof String dependencyName) {
                addRequiredPaperDependency(target, dependencyName, null);
                continue;
            }

            if (dependency instanceof Map<?, ?> dependencySettings) {
                var name = dependencySettings.get("name");
                if (name instanceof String dependencyName) {
                    addRequiredPaperDependency(target, dependencyName, dependencySettings);
                }
            }
        }
    }

    private void addPaperDependencySection(List<String> target, Map<?, ?> dependencySection) {
        for (var entry : dependencySection.entrySet()) {
            if (!(entry.getKey() instanceof String dependencyName)) continue;
            addRequiredPaperDependency(target, dependencyName, entry.getValue());
        }
    }

    private void addRequiredPaperDependency(List<String> target, String dependencyName, Object dependencySettings) {
        if (dependencyName.isBlank()) return;
        if (!isRequiredPaperDependency(dependencySettings)) return;
        if (containsDependency(target, dependencyName)) return;
        target.add(dependencyName);
    }

    private boolean isPaperDependencySettings(Object value) {
        if (!(value instanceof Map<?, ?> dependencySettings)) return true;
        return dependencySettings.containsKey("load")
                || dependencySettings.containsKey("required")
                || dependencySettings.containsKey("join-classpath");
    }

    private boolean isRequiredPaperDependency(Object value) {
        if (!(value instanceof Map<?, ?> dependencySettings)) return true;
        var required = dependencySettings.get("required");
        return !(required instanceof Boolean booleanValue) || booleanValue;
    }

    /**
     * Common plugin command handling logic.
     */
    protected void handlePluginCommand(Plugin plugin, SimpleCommandMap commandMap,
                                       CommandMapWrap<org.bukkit.command.Command> modifiedKnownCommands,
                                       Map.Entry<String, Command> entry) {
        var command = (PluginCommand) entry.getValue().<org.bukkit.command.Command>getHandle();
        var bukkitPlugin = plugin.<org.bukkit.plugin.Plugin>getHandle();
        if (command.getPlugin() != bukkitPlugin) return;
        command.unregister(commandMap);
        modifiedKnownCommands.remove(entry.getKey());
    }

    /**
     * Common broken command removal logic.
     */
    protected void handleBrokenCommand(Map.Entry<String, Command> entry, SimpleCommandMap commandMap,
                                       CommandMapWrap<org.bukkit.command.Command> modifiedKnownCommands, String loggerName) {
        var config = PlugManBukkit.getInstance().<PlugManConfigurationManager>get(PlugManConfigurationManager.class);

        var handle = entry.getValue().<org.bukkit.command.Command>getHandle();
        if (config.shouldNotifyOnBrokenCommandRemoval()) Logger.getLogger(loggerName).info("Removing broken command '" + handle.getName() + "'!");
        handle.unregister(commandMap);
        modifiedKnownCommands.remove(entry.getKey());
    }

    /**
     * Common command loading logic.
     */
    protected synchronized void scheduleCommandLoading() {
        if (deferCommandSyncIfBatching()) return;

        PlugManBukkit.getInstance().get(ThreadUtil.class).syncLater(this::syncCommands, 500L);
    }

    @Override
    public synchronized void beginCommandUpdateBatch() {
        commandUpdateBatchDepth++;
    }

    @Override
    public synchronized void endCommandUpdateBatch() {
        if (commandUpdateBatchDepth > 0) commandUpdateBatchDepth--;
        if (commandUpdateBatchDepth > 0 || !commandSyncPending) return;

        commandSyncPending = false;
        syncCommands();
    }

    protected synchronized boolean deferCommandSyncIfBatching() {
        if (commandUpdateBatchDepth <= 0) return false;

        commandSyncPending = true;
        return true;
    }


    /**
     * Abstract method for reloading commands.
     */
    protected abstract void syncCommands();

    /**
     * Common data structure for unload operations.
     */
    public record CommonUnloadData(org.bukkit.plugin.PluginManager pluginManager, SimpleCommandMap commandMap, List<org.bukkit.plugin.Plugin> plugins,
                                   Map<String, org.bukkit.plugin.Plugin> names, CommandMapWrap<org.bukkit.command.Command> commands,
                                   Map<Event, SortedSet<RegisteredListener>> listeners,
                                   boolean reloadListeners) {
    }

    protected record PluginLoadPreflight(File pluginFile, PluginDescriptor descriptor, PluginResult result) {
        private static PluginLoadPreflight failed(PluginResult result) {
            return new PluginLoadPreflight(null, null, result);
        }
    }

    protected record PluginDescriptor(String name, boolean paperPlugin, List<String> requiredDependencies) {
    }
}
