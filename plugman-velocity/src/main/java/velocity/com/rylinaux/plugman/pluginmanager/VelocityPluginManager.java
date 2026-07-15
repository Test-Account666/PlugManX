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
import velocity.com.rylinaux.plugman.logging.VelocityCrashDumpWriter;
import velocity.com.rylinaux.plugman.plugin.VelocityCommand;
import velocity.com.rylinaux.plugman.plugin.VelocityPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Velocity implementation of PluginManager.
 * Runtime load and unload use development adapters because Velocity has no supported lifecycle API.
 */
public class VelocityPluginManager implements PluginManager {
    private static final String UNKNOWN_PLUGIN = "Unknown";
    private static final String LOAD_INVALID_PLUGIN = "load.invalid-plugin";
    private static final Set<String> ALWAYS_PROTECTED_PLUGIN_IDS = Set.of("velocity");
    private static final Set<String> FORCE_PROTECTED_PLUGIN_IDS = Set.of(
            "plugman", "plugmanx", "plugmanvelocity", "luckperms", "geyser", "geyser-velocity");

    private final VelocityDevelopmentRuntime runtime = VelocityDevelopmentRuntime.detect();
    private final Map<String, File> unloadedPluginFiles = new ConcurrentHashMap<>();
    private final Map<String, Plugin> unloadedPlugins = new ConcurrentHashMap<>();
    private final Map<String, ReloadBackup> reloadBackups = new ConcurrentHashMap<>();
    private final Map<String, Path> knownGoodPluginJars = new ConcurrentHashMap<>();
    private final Map<String, String> pendingCleanupWarnings = new ConcurrentHashMap<>();
    private final ReentrantLock operationLock = new ReentrantLock(true);
    private final ThreadLocal<Integer> operationBatchDepth = ThreadLocal.withInitial(() -> 0);

    public VelocityPluginManager() {
        initializeKnownGoodPluginCache();
    }

    private ProxyServer getServer() {
        return PlugManVelocity.getInstance().getServer();
    }

    @Override
    public PluginResult enable(Plugin plugin) {
        return serialized(() -> enableLocked(plugin));
    }

    private PluginResult enableLocked(Plugin plugin) {
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
        return serialized(this::enableAllLocked);
    }

    private PluginResult enableAllLocked() {
        var successful = true;
        for (var plugin : dependencyOrder(unloadedPlugins.values())) {
            if (!loadLocked(plugin).success()) successful = false;
        }
        return new PluginResult(successful, "plugins.enabled-all");
    }

    @Override
    public PluginResult disable(Plugin plugin) {
        return disable(plugin, false);
    }

    @Override
    public PluginResult disable(Plugin plugin, boolean force) {
        return serialized(() -> disableLocked(plugin, force));
    }

    private PluginResult disableLocked(Plugin plugin, boolean force) {
        var result = unloadLocked(plugin, force);
        return result.success()
                ? new PluginResult(true, "velocity.disabled", plugin.getName())
                : result;
    }

    @Override
    public PluginResult disableAll() {
        return disableAll(false);
    }

    @Override
    public PluginResult disableAll(boolean force) {
        return serialized(() -> disableAllLocked(force));
    }

    private PluginResult disableAllLocked(boolean force) {
        var successful = true;
        var plugins = new ArrayList<>(dependencyOrder(getPlugins()));
        for (var plugin : plugins.reversed()) {
            if (isAlwaysProtected(plugin) || isIgnored(plugin) || (!force && requiresForce(plugin))) continue;
            if (!unloadLocked(plugin, force).success()) successful = false;
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
        return plugin != null && isIgnored(plugin.getName());
    }

    @Override
    public boolean isIgnored(String plugin) {
        if (isAlwaysProtected(plugin)) return true;
        var configManager = PlugManVelocity.getInstance().get(PlugManConfigurationManager.class);
        return configManager != null && configManager.getIgnoredPlugins().stream()
                .anyMatch(ignored -> ignored.equalsIgnoreCase(plugin));
    }

    @Override
    public PluginResult load(String name) {
        return serialized(() -> loadLocked(name));
    }

    private PluginResult loadLocked(String name) {
        if (!runtimeAvailable()) return new PluginResult(false, LOAD_INVALID_PLUGIN, name);
        var file = findPluginFile(name);
        if (file == null) return new PluginResult(false, "load.cannot-find", name);
        return loadPluginFromFileLocked(file);
    }

    @Override
    public PluginResult load(Plugin plugin) {
        return serialized(() -> loadLocked(plugin));
    }

    private PluginResult loadLocked(Plugin plugin) {
        if (plugin == null) return new PluginResult(false, LOAD_INVALID_PLUGIN);
        var id = plugin.getName().toLowerCase(Locale.ROOT);
        var file = unloadedPluginFiles.get(id);
        if (file == null) file = plugin.getFile();
        var result = loadPluginFromFileLocked(file);
        if (result.success()) return result;
        if (!reloadBackups.containsKey(id)) {
            reportCleanupRecoveryFailure(id);
            return result;
        }
        var rollbackResult = restoreReloadBackup(id, result);
        reportCleanupRecoveryFailure(id);
        return rollbackResult;
    }

    @Override
    public void beginCommandUpdateBatch() {
        operationLock.lock();
        operationBatchDepth.set(operationBatchDepth.get() + 1);
    }

    @Override
    public void endCommandUpdateBatch() {
        var depth = operationBatchDepth.get();
        if (depth <= 0) return;

        try {
            if (depth == 1) {
                discardAllReloadBackups();
                operationBatchDepth.remove();
            } else {
                operationBatchDepth.set(depth - 1);
            }
        } finally {
            operationLock.unlock();
        }
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
        return unload(plugin, false);
    }

    @Override
    public PluginResult unload(Plugin plugin, boolean force) {
        return serialized(() -> unloadLocked(plugin, force));
    }

    private PluginResult unloadLocked(Plugin plugin, boolean force) {
        if (!runtimeAvailable() || !(plugin instanceof VelocityPlugin velocityPlugin)) {
            return new PluginResult(false, "unload.failed", plugin == null ? UNKNOWN_PLUGIN : plugin.getName());
        }
        if (isAlwaysProtected(plugin)) return new PluginResult(false, "error.ignored", plugin.getName());
        if (!force && requiresForce(plugin)) {
            return new PluginResult(false, "velocity.force-required", plugin.getName());
        }

        var container = velocityPlugin.pluginContainer();
        var file = plugin.getFile();
        var startedAt = System.nanoTime();
        debug("Starting unload for {}", plugin.getName());
        try {
            prepareReloadBackup(plugin);
            runtime.unload(getServer(), container, debugConsumer());
            rememberUnloadedPlugin(velocityPlugin, file);
            debug("Completed unload for {} in {} ms", plugin.getName(), elapsedMillis(startedAt));
            return new PluginResult(true, "unload.unloaded", plugin.getName());
        } catch (ReflectiveOperationException | IOException | RuntimeException exception) {
            debug("Unload for {} failed after {} ms: {}", plugin.getName(), elapsedMillis(startedAt), exception);
            logFailure("unload", plugin.getName(), exception);
            if (!getServer().getPluginManager().isLoaded(plugin.getName())) {
                rememberUnloadedPlugin(velocityPlugin, file);
                var id = plugin.getName().toLowerCase(Locale.ROOT);
                var failedSteps = VelocityDevelopmentRuntime.cleanupFailureSummary(exception);
                if (failedSteps == null) failedSteps = exception.getClass().getSimpleName();
                pendingCleanupWarnings.put(id, failedSteps);
                PlugManVelocity.getInstance().getLogger().warn(
                        "Velocity removed {} despite cleanup warnings in: {}. Attempting to load it again.",
                        plugin.getName(), failedSteps);
                return new PluginResult(true, "unload.unloaded", plugin.getName());
            }
            discardReloadBackup(plugin.getName());
            return new PluginResult(false, "unload.failed", plugin.getName());
        }
    }

    private void rememberUnloadedPlugin(VelocityPlugin plugin, File file) {
        if (file == null) return;
        var id = plugin.getName().toLowerCase(Locale.ROOT);
        unloadedPluginFiles.put(id, file);
        unloadedPlugins.put(id, UnloadedPluginSnapshot.from(plugin, file));
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
        return serialized(() -> loadPluginFromFileLocked(file));
    }

    private PluginResult loadPluginFromFileLocked(File file) {
        if (!runtimeAvailable() || file == null || !file.isFile()) {
            return new PluginResult(false, LOAD_INVALID_PLUGIN, file == null ? UNKNOWN_PLUGIN : file.getName());
        }

        var startedAt = System.nanoTime();
        debug("Starting load for {}", file.getName());
        try {
            var description = runtime.readDescription(getServer(), file.toPath());
            debug("Read plugin description for {} after {} ms", description.getId(), elapsedMillis(startedAt));
            if (isAlwaysProtected(description.getId()) || isIgnored(description.getId())) {
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
            discardReloadBackup(id);
            cacheKnownGoodPluginJar(id, file.toPath());
            reportCleanupRecoverySuccess(id, container.getDescription().getId());
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

    static List<Plugin> dependencyOrder(Collection<? extends Plugin> plugins) {
        var byId = new java.util.LinkedHashMap<String, Plugin>();
        for (var plugin : plugins) {
            byId.putIfAbsent(plugin.getName().toLowerCase(Locale.ROOT), plugin);
        }
        var ordered = new ArrayList<Plugin>();
        var visiting = new HashSet<String>();
        var visited = new HashSet<String>();
        byId.values().stream()
                .sorted(java.util.Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(plugin -> visitDependencies(plugin, byId, visiting, visited, ordered));
        return ordered;
    }

    private static void visitDependencies(Plugin plugin,
                                          Map<String, Plugin> byId,
                                          Set<String> visiting,
                                          Set<String> visited,
                                          List<Plugin> ordered) {
        var id = plugin.getName().toLowerCase(Locale.ROOT);
        if (visited.contains(id) || !visiting.add(id)) return;
        for (var dependencyId : plugin.getDepend()) {
            var dependency = byId.get(dependencyId.toLowerCase(Locale.ROOT));
            if (dependency != null) visitDependencies(dependency, byId, visiting, visited, ordered);
        }
        visiting.remove(id);
        if (visited.add(id)) ordered.add(plugin);
    }

    private void prepareReloadBackup(Plugin plugin) throws IOException {
        if (operationBatchDepth.get() <= 0 || plugin == null) return;
        var source = plugin.getFile();
        if (source == null || !source.isFile()) return;

        var backupDirectory = PlugManVelocity.getInstance().getDataDirectory().resolve("reload-backups");
        Files.createDirectories(backupDirectory);
        var id = plugin.getName().toLowerCase(Locale.ROOT);
        if (reloadBackups.containsKey(id)) return;
        var knownGoodSource = knownGoodPluginJars.getOrDefault(id, source.toPath());
        var backup = Files.createTempFile(backupDirectory, sanitizeFileName(id) + "-", ".jar");
        Files.copy(knownGoodSource, backup, StandardCopyOption.REPLACE_EXISTING);
        reloadBackups.put(id, new ReloadBackup(source.toPath(), backup));
        debug("Created reload backup for {}", plugin.getName());
    }

    private PluginResult restoreReloadBackup(String id, PluginResult loadFailure) {
        var backup = reloadBackups.remove(id.toLowerCase(Locale.ROOT));
        if (backup == null) return loadFailure;

        try {
            Files.copy(backup.backup(), backup.original(), StandardCopyOption.REPLACE_EXISTING);
            var restored = loadPluginFromFileLocked(backup.original().toFile());
            if (!restored.success()) return loadFailure;
            debug("Restored previous plugin jar for {} after failed reload", id);
            return new PluginResult(false, "load.rollback-restored", id);
        } catch (IOException exception) {
            PlugManVelocity.getInstance().getLogger().error("Failed to restore reload backup for {}", id, exception);
            return loadFailure;
        } finally {
            deleteBackup(backup);
        }
    }

    private void discardReloadBackup(String id) {
        if (id == null) return;
        var backup = reloadBackups.remove(id.toLowerCase(Locale.ROOT));
        if (backup != null) deleteBackup(backup);
    }

    private void discardAllReloadBackups() {
        for (var id : List.copyOf(reloadBackups.keySet())) discardReloadBackup(id);
    }

    private void deleteBackup(ReloadBackup backup) {
        try {
            Files.deleteIfExists(backup.backup());
        } catch (IOException exception) {
            PlugManVelocity.getInstance().getLogger().warn(
                    "Failed to delete temporary reload backup {}", backup.backup(), exception);
        }
    }

    private static String sanitizeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void initializeKnownGoodPluginCache() {
        try {
            var cacheDirectory = knownGoodCacheDirectory();
            Files.createDirectories(cacheDirectory);
            try (Stream<Path> files = Files.list(cacheDirectory)) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        PlugManVelocity.getInstance().getLogger().warn(
                                "Failed to delete stale Velocity reload cache file {}", path, exception);
                    }
                });
            }
            for (var container : getServer().getPluginManager().getPlugins()) {
                var source = container.getDescription().getSource();
                if (source.isPresent() && Files.isRegularFile(source.get())) {
                    cacheKnownGoodPluginJar(container.getDescription().getId(), source.get());
                }
            }
        } catch (IOException | RuntimeException exception) {
            PlugManVelocity.getInstance().getLogger().warn(
                    "Failed to initialize the Velocity known-good plugin cache", exception);
        }
    }

    private void cacheKnownGoodPluginJar(String pluginId, Path source) {
        if (pluginId == null || source == null || !Files.isRegularFile(source)) return;
        var id = pluginId.toLowerCase(Locale.ROOT);
        try {
            var cacheDirectory = knownGoodCacheDirectory();
            Files.createDirectories(cacheDirectory);
            var cachedJar = cacheDirectory.resolve(sanitizeFileName(id) + ".jar");
            Files.copy(source, cachedJar, StandardCopyOption.REPLACE_EXISTING);
            knownGoodPluginJars.put(id, cachedJar);
        } catch (IOException exception) {
            PlugManVelocity.getInstance().getLogger().warn(
                    "Failed to update the known-good reload cache for {}", pluginId, exception);
        }
    }

    private void reportCleanupRecoverySuccess(String id, String pluginName) {
        var failedSteps = pendingCleanupWarnings.remove(id);
        if (failedSteps == null) return;
        PlugManVelocity.getInstance().getLogger().warn(
                "{} was successfully loaded again after cleanup warnings in: {}.", pluginName, failedSteps);
    }

    private void reportCleanupRecoveryFailure(String id) {
        var failedSteps = pendingCleanupWarnings.get(id);
        if (failedSteps == null) return;
        PlugManVelocity.getInstance().getLogger().error(
                "{} could not be loaded again after cleanup warnings in: {}. The plugin remains unloaded.",
                id, failedSteps);
    }

    private Path knownGoodCacheDirectory() {
        return PlugManVelocity.getInstance().getDataDirectory().resolve("reload-cache");
    }

    private <T> T serialized(Supplier<T> operation) {
        operationLock.lock();
        try {
            return operation.get();
        } finally {
            operationLock.unlock();
        }
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

    public boolean isDevelopmentRuntimeAvailable() {
        return runtimeAvailable();
    }

    public String getDevelopmentRuntimeAdapterName() {
        return runtimeAvailable() ? runtime.adapterName() : "unavailable";
    }

    public String getDevelopmentRuntimeCompatibilityWarning() {
        return runtimeAvailable() ? runtime.compatibilityWarning() : null;
    }

    private void debug(String message, Object... arguments) {
        if (!isVelocityRuntimeDebugEnabled()) return;
        PlugManVelocity.getInstance().getLogger().info("[PlugManDebug] " + message, arguments);
    }

    private boolean isVelocityRuntimeDebugEnabled() {
        var configurationManager = PlugManVelocity.getInstance().get(PlugManConfigurationManager.class);
        return configurationManager instanceof VelocityPlugManConfigurationManager velocityConfig
                && velocityConfig.isVelocityReloadDebugEnabled();
    }

    private Consumer<String> debugConsumer() {
        return isVelocityRuntimeDebugEnabled() ? this::debug : null;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    @Override
    public boolean requiresForce(Plugin plugin) {
        return plugin != null && isForceProtectedPluginId(plugin.getName());
    }

    @Override
    public boolean supportsForceFlag() {
        return true;
    }

    private boolean isAlwaysProtected(Plugin plugin) {
        return plugin != null && isAlwaysProtected(plugin.getName());
    }

    private boolean isAlwaysProtected(String id) {
        return id != null && ALWAYS_PROTECTED_PLUGIN_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    static boolean isForceProtectedPluginId(String id) {
        return id != null && FORCE_PROTECTED_PLUGIN_IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    private void logFailure(String operation, String plugin, Throwable throwable) {
        var cause = throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : throwable;
        PlugManVelocity.getInstance().getLogger().error(
                "Velocity development runtime {} failed for {}", operation, plugin, cause);
        VelocityCrashDumpWriter.write(operation + " failed for " + plugin, cause);
    }

    private record ReloadBackup(Path original, Path backup) {
    }

    private record UnloadedPluginSnapshot(String name,
                                          String version,
                                          List<String> dependencies,
                                          List<String> softDependencies,
                                          List<String> authors,
                                          File file) implements Plugin {
        private static UnloadedPluginSnapshot from(VelocityPlugin plugin, File file) {
            return new UnloadedPluginSnapshot(
                    plugin.getName(),
                    plugin.getVersion(),
                    List.copyOf(plugin.getDepend()),
                    List.copyOf(plugin.getSoftDepend()),
                    List.copyOf(plugin.getAuthors()),
                    file);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public List<String> getDepend() {
            return dependencies;
        }

        @Override
        public List<String> getSoftDepend() {
            return softDependencies;
        }

        @Override
        public List<String> getAuthors() {
            return authors;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public <T> T getHandle() {
            return null;
        }
    }
}
