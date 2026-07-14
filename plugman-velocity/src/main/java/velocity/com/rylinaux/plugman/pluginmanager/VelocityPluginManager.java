package velocity.com.rylinaux.plugman.pluginmanager;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Command;
import core.com.rylinaux.plugman.plugins.CommandMapWrap;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import lombok.SneakyThrows;
import velocity.com.rylinaux.plugman.PlugManVelocity;
import velocity.com.rylinaux.plugman.config.VelocityPlugManConfigurationManager;
import velocity.com.rylinaux.plugman.plugin.VelocityCommand;
import velocity.com.rylinaux.plugman.plugin.VelocityPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Velocity implementation of PluginManager.
 * Runtime load and unload are experimental because Velocity has no supported lifecycle API.
 */
public class VelocityPluginManager implements PluginManager {
    private static final String UNKNOWN_PLUGIN = "Unknown";
    private static final String LOAD_INVALID_PLUGIN = "load.invalid-plugin";
    private static final Set<String> PROTECTED_PLUGIN_IDS = Set.of("velocity", "plugmanvelocity");

    private final ExperimentalVelocityRuntime runtime = ExperimentalVelocityRuntime.detect();
    private final Map<String, File> unloadedPluginFiles = new ConcurrentHashMap<>();
    private final Map<String, VelocityPlugin> unloadedPlugins = new ConcurrentHashMap<>();

    private ProxyServer getServer() {
        return PlugManVelocity.getInstance().getServer();
    }

    @Override
    public PluginResult enable(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, "error.invalid-plugin");
        if (getServer().getPluginManager().isLoaded(plugin.getName())) {
            return new PluginResult(false, "enable.already-enabled", plugin.getName());
        }
        var result = load(plugin);
        return result.success()
                ? new PluginResult(true, "velocity.enabled", plugin.getName())
                : result;
    }

    @Override
    public PluginResult enableAll() {
        var successful = true;
        for (var entry : new ArrayList<>(unloadedPluginFiles.entrySet())) {
            if (!loadPluginFromFile(entry.getValue()).success()) successful = false;
        }
        return new PluginResult(successful, "plugins.enabled-all");
    }

    @Override
    public PluginResult disable(Plugin plugin) {
        var result = unload(plugin);
        return result.success()
                ? new PluginResult(true, "velocity.disabled", plugin.getName())
                : result;
    }

    @Override
    public PluginResult disableAll() {
        var successful = true;
        for (var plugin : new ArrayList<>(getPlugins())) {
            if (isProtected(plugin) || isIgnored(plugin)) continue;
            if (!unload(plugin).success()) successful = false;
        }
        return new PluginResult(successful, "plugins.disabled-all");
    }

    @Override
    public String getFormattedName(Plugin plugin) {
        return getFormattedName(plugin, false);
    }

    @Override
    public String getFormattedName(Plugin plugin, boolean includeVersions) {
        return includeVersions ? plugin.getName() + " (" + plugin.getVersion() + ")" : plugin.getName();
    }

    @Override
    public Plugin getPluginByName(String[] args, int start) {
        if (args.length <= start) return null;
        return getPluginByName(String.join(" ", Arrays.copyOfRange(args, start, args.length)));
    }

    @Override
    public Plugin getPluginByName(String name) {
        var direct = getServer().getPluginManager().getPlugin(name);
        if (direct.isPresent()) return wrap(direct.get());

        return getServer().getPluginManager().getPlugins().stream()
                .filter(container -> container.getDescription().getName()
                        .map(value -> value.equalsIgnoreCase(name)).orElse(false))
                .findFirst().map(this::wrap).orElse(null);
    }

    @Override
    public Plugin getDisabledPluginByName(String[] args, int start) {
        if (args.length <= start) return null;
        var name = String.join(" ", Arrays.copyOfRange(args, start, args.length));
        return unloadedPlugins.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public List<String> getPluginNames(boolean fullName) {
        return getPlugins().stream()
                .map(plugin -> fullName ? getFormattedName(plugin, true) : plugin.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        return unloadedPlugins.values().stream()
                .map(plugin -> fullName ? getFormattedName(plugin, true) : plugin.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        return getPluginNames(fullName);
    }

    @Override
    public String getPluginVersion(String name) {
        return getServer().getPluginManager().getPlugin(name)
                .flatMap(container -> container.getDescription().getVersion())
                .orElse(UNKNOWN_PLUGIN);
    }

    @Override
    public String getUsages(Plugin plugin) {
        var aliases = getServer().getCommandManager().getAliases().stream()
                .filter(alias -> findByCommand(alias).stream()
                        .anyMatch(plugin.getName()::equalsIgnoreCase))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return String.join(", ", aliases);
    }

    @Override
    public List<String> findByCommand(String command) {
        var meta = getServer().getCommandManager().getCommandMeta(command);
        if (meta == null) return Collections.emptyList();

        var owner = meta.getPlugin();
        if (owner instanceof PluginContainer container) {
            return List.of(container.getDescription().getId());
        }
        if (owner == null) return Collections.emptyList();

        return getServer().getPluginManager().fromInstance(owner)
                .map(container -> List.of(container.getDescription().getId()))
                .orElseGet(Collections::emptyList);
    }

    @Override
    public boolean isIgnored(Plugin plugin) {
        return plugin != null && (isProtected(plugin) || isIgnored(plugin.getName()));
    }

    @Override
    public boolean isIgnored(String plugin) {
        if (isProtected(plugin)) return true;
        var configManager = PlugManVelocity.getInstance().get(PlugManConfigurationManager.class);
        return configManager != null && configManager.getIgnoredPlugins().stream()
                .anyMatch(ignored -> ignored.equalsIgnoreCase(plugin));
    }

    @Override
    public PluginResult load(String name) {
        if (!runtimeAvailable()) return new PluginResult(false, LOAD_INVALID_PLUGIN, name);
        var file = findPluginFile(name);
        if (file == null) return new PluginResult(false, "load.cannot-find", name);
        return loadPluginFromFile(file);
    }

    @Override
    public PluginResult load(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, LOAD_INVALID_PLUGIN);
        var id = plugin.getName().toLowerCase(Locale.ROOT);
        var file = unloadedPluginFiles.get(id);
        if (file == null) file = plugin.getFile();
        return loadPluginFromFile(file);
    }

    @SneakyThrows
    @Override
    public CommandMapWrap<com.velocitypowered.api.command.CommandMeta> getKnownCommands() {
        var commandManager = getServer().getCommandManager();
        var commandMetas = FieldAccessor
                .<Map<String, com.velocitypowered.api.command.CommandMeta>>getValue("commandMetas", commandManager);
        return new CommandMapWrap<>(commandMetas, VelocityCommand::new);
    }

    @Override
    public PluginResult unload(Plugin plugin) {
        if (!runtimeAvailable() || !(plugin instanceof VelocityPlugin velocityPlugin)) {
            return new PluginResult(false, "unload.failed", plugin == null ? UNKNOWN_PLUGIN : plugin.getName());
        }
        if (isProtected(plugin)) return new PluginResult(false, "error.ignored", plugin.getName());

        var container = velocityPlugin.pluginContainer();
        var file = plugin.getFile();
        var startedAt = System.nanoTime();
        debug("Starting unload for {}", plugin.getName());
        try {
            runtime.unload(getServer(), container, debugConsumer());
            if (file != null) {
                var id = plugin.getName().toLowerCase(Locale.ROOT);
                unloadedPluginFiles.put(id, file);
                unloadedPlugins.put(id, velocityPlugin);
            }
            debug("Completed unload for {} in {} ms", plugin.getName(), elapsedMillis(startedAt));
            return new PluginResult(true, "unload.unloaded", plugin.getName());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            debug("Unload for {} failed after {} ms: {}", plugin.getName(), elapsedMillis(startedAt), exception);
            logFailure("unload", plugin.getName(), exception);
            return new PluginResult(false, "unload.failed", plugin.getName());
        }
    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        return false;
    }

    @Override
    public List<String> getPluginListMessageKeys() {
        return List.of("list.velocity");
    }

    @Override
    public String getPluginListMessageKey(Plugin plugin) {
        return "list.velocity";
    }

    @Override
    public Set<Plugin> getPlugins() {
        return getServer().getPluginManager().getPlugins().stream()
                .map(this::wrap)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public File findPluginFile(String name) {
        var loaded = getPluginByName(name);
        if (loaded != null) return loaded.getFile();

        var remembered = unloadedPluginFiles.get(name.toLowerCase(Locale.ROOT));
        if (remembered != null && remembered.isFile()) return remembered;

        var pluginsDirectory = getPluginsDirectory();
        if (!Files.isDirectory(pluginsDirectory)) return null;

        try (Stream<Path> files = Files.list(pluginsDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> hasPluginId(path, name))
                    .map(Path::toFile)
                    .findFirst().orElse(null);
        } catch (IOException exception) {
            PlugManVelocity.getInstance().getLogger().error("Failed to scan Velocity plugin files", exception);
            return null;
        }
    }

    public PluginResult loadPluginFromFile(File file) {
        if (!runtimeAvailable() || file == null || !file.isFile()) {
            return new PluginResult(false, LOAD_INVALID_PLUGIN, file == null ? UNKNOWN_PLUGIN : file.getName());
        }

        var startedAt = System.nanoTime();
        debug("Starting load for {}", file.getName());
        try {
            var description = runtime.readDescription(getServer(), file.toPath());
            debug("Read plugin description for {} after {} ms", description.getId(), elapsedMillis(startedAt));
            if (isProtected(description.getId()) || isIgnored(description.getId())) {
                return new PluginResult(false, "error.ignored", description.getId());
            }
            if (getServer().getPluginManager().isLoaded(description.getId())) {
                return new PluginResult(false, "load.already-loaded", description.getId());
            }

            var missingDependencies = findMissingDependencies(description);
            if (!missingDependencies.isEmpty()) {
                return new PluginResult(false, "load.missing-dependencies",
                        description.getId(), String.join(", ", missingDependencies));
            }

            var container = runtime.load(getServer(), file.toPath(), debugConsumer());
            var id = container.getDescription().getId().toLowerCase(Locale.ROOT);
            unloadedPluginFiles.remove(id);
            unloadedPlugins.remove(id);
            debug("Completed load for {} in {} ms", container.getDescription().getId(), elapsedMillis(startedAt));
            return new PluginResult(true, "load.loaded", container.getDescription().getId());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            debug("Load for {} failed after {} ms: {}", file.getName(), elapsedMillis(startedAt), exception);
            logFailure("load", file.getName(), exception);
            return new PluginResult(false, LOAD_INVALID_PLUGIN, file.getName());
        }
    }

    private List<String> findMissingDependencies(PluginDescription description) {
        return description.getDependencies().stream()
                .filter(dependency -> !dependency.isOptional())
                .map(dependency -> dependency.getId())
                .filter(dependency -> !getServer().getPluginManager().isLoaded(dependency))
                .toList();
    }

    private boolean hasPluginId(Path file, String expectedName) {
        try {
            var description = runtime.readDescription(getServer(), file);
            return description.getId().equalsIgnoreCase(expectedName)
                    || description.getName().map(name -> name.equalsIgnoreCase(expectedName)).orElse(false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private Path getPluginsDirectory() {
        var dataDirectory = PlugManVelocity.getInstance().getDataDirectory();
        return dataDirectory.getParent() == null ? Path.of("plugins") : dataDirectory.getParent();
    }

    private VelocityPlugin wrap(PluginContainer container) {
        return new VelocityPlugin(container, container.getInstance().orElse(null));
    }

    private boolean runtimeAvailable() {
        return runtime != null;
    }

    public boolean isExperimentalRuntimeAvailable() {
        return runtimeAvailable();
    }

    public String getExperimentalRuntimeAdapterName() {
        return runtimeAvailable() ? runtime.adapterName() : "unavailable";
    }

    public String getExperimentalRuntimeCompatibilityWarning() {
        return runtimeAvailable() ? runtime.compatibilityWarning() : null;
    }

    private void debug(String message, Object... arguments) {
        if (!isVelocityReloadDebugEnabled()) return;
        PlugManVelocity.getInstance().getLogger().info("[VelocityReloadDebug] " + message, arguments);
    }

    private boolean isVelocityReloadDebugEnabled() {
        var configurationManager = PlugManVelocity.getInstance().get(PlugManConfigurationManager.class);
        return configurationManager instanceof VelocityPlugManConfigurationManager velocityConfig
                && velocityConfig.isVelocityReloadDebugEnabled();
    }

    private Consumer<String> debugConsumer() {
        return isVelocityReloadDebugEnabled() ? this::debug : null;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private boolean isProtected(Plugin plugin) {
        return plugin != null && isProtected(plugin.getName());
    }

    private boolean isProtected(String id) {
        return id != null && PROTECTED_PLUGIN_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    private void logFailure(String operation, String plugin, Throwable throwable) {
        var cause = throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : throwable;
        PlugManVelocity.getInstance().getLogger().error(
                "Experimental Velocity plugin {} failed for {}", operation, plugin, cause);
    }
}
