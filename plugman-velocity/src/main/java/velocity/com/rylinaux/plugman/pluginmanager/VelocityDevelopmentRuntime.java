package velocity.com.rylinaux.plugman.pluginmanager;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.common.collect.Multimap;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import velocity.com.rylinaux.plugman.PlugManVelocity;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Capability-checked access to Velocity's unsupported runtime plugin lifecycle.
 */
final class VelocityDevelopmentRuntime {
    private final String adapterName;
    private final String compatibilityWarning;
    private final Constructor<?> loaderConstructor;
    private final Constructor<?> containerConstructor;
    private final Method loadCandidate;
    private final Method createPluginFromCandidate;
    private final Method createModule;
    private final Method createPlugin;
    private final Method registerPlugin;
    private final Field pluginsById;
    private final Field pluginInstances;
    private final Method registerInternally;
    private final Field handlersByType;
    private final Field handlerComparator;
    private final Field handlerPlugin;
    private final Method targetedFire;
    private final Field channelIdentifiers;
    private final Field pluginClassLoaders;
    private final Map<ClassLoader, Set<ChannelIdentifier>> pluginChannelsByClassLoader = new ConcurrentHashMap<>();

    private VelocityDevelopmentRuntime(VelocityRuntimeAdapters.Selection selection) throws ReflectiveOperationException {
        var adapter = selection.adapter();
        adapterName = adapter.name();
        compatibilityWarning = selection.warning();
        var layout = adapter.reflectionLayout();
        var loaderClass = Class.forName(layout.javaPluginLoaderClass());
        var containerClass = Class.forName(layout.pluginContainerClass());
        var pluginManagerClass = Class.forName(layout.pluginManagerClass());
        var eventManagerClass = Class.forName(layout.eventManagerClass());

        loaderConstructor = accessible(loaderClass.getDeclaredConstructor(ProxyServer.class, Path.class));
        containerConstructor = accessible(containerClass.getDeclaredConstructor(PluginDescription.class));
        loadCandidate = findMethod(loaderClass, layout.loadCandidateMethods(), Path.class);
        createPluginFromCandidate = findMethod(loaderClass, layout.createPluginFromCandidateMethods(), PluginDescription.class);
        createModule = findMethod(loaderClass, layout.createModuleMethods(), PluginContainer.class);
        createPlugin = findMethod(loaderClass, layout.createPluginMethods(),
                PluginContainer.class, Module[].class);
        registerPlugin = findMethod(pluginManagerClass, layout.registerPluginMethods(), PluginContainer.class);
        pluginsById = findField(pluginManagerClass, layout.pluginMapFields());
        pluginInstances = findField(pluginManagerClass, layout.instanceMapFields());
        registerInternally = findMethod(eventManagerClass, layout.registerInternallyMethods(),
                PluginContainer.class, Object.class);
        handlersByType = findField(eventManagerClass, layout.handlersByTypeFields());
        handlerComparator = findField(eventManagerClass, layout.handlerComparatorFields());

        var handlerClass = Class.forName(layout.handlerRegistrationClass());
        handlerPlugin = findField(handlerClass, layout.handlerPluginFields());
        targetedFire = findTargetedFire(eventManagerClass, handlerClass, layout.targetedFireMethods());
        var channelRegistrarClass = PlugManVelocity.getInstance().getServer().getChannelRegistrar().getClass();
        channelIdentifiers = findOptionalField(channelRegistrarClass, List.of("identifierMap"));
        var pluginClassLoaderClass = Class.forName("com.velocitypowered.proxy.plugin.PluginClassLoader");
        pluginClassLoaders = findOptionalField(pluginClassLoaderClass, List.of("loaders"));
    }

    static VelocityDevelopmentRuntime detect() {
        try {
            var version = PlugManVelocity.getInstance().getServer().getVersion().getVersion();
            var selection = VelocityRuntimeAdapters.find(version);
            if (selection.warning() != null) {
                PlugManVelocity.getInstance().getLogger().warn("{}", selection.warning());
            }
            return new VelocityDevelopmentRuntime(selection);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            PlugManVelocity.getInstance().getLogger().warn(
                    "Velocity development runtime is unavailable on this Velocity build: {}",
                    exception.toString());
            return null;
        }
    }

    String adapterName() {
        return adapterName;
    }

    String compatibilityWarning() {
        return compatibilityWarning;
    }

    PluginDescription readDescription(ProxyServer server, Path source) throws ReflectiveOperationException {
        var loader = loaderConstructor.newInstance(server, source.getParent());
        return (PluginDescription) loadCandidate.invoke(loader, source);
    }

    PluginContainer load(ProxyServer server, Path source, Consumer<String> debug) throws ReflectiveOperationException {
        var startedAt = System.nanoTime();
        var channelsBeforeLoad = registeredChannels(server);
        var classLoadersBeforeLoad = registeredPluginClassLoaders();
        var loader = loaderConstructor.newInstance(server, source.getParent());
        debug(debug, startedAt, "Created JavaPluginLoader");
        var candidate = (PluginDescription) loadCandidate.invoke(loader, source);
        debug(debug, startedAt, "Loaded plugin candidate");
        PluginContainer container = null;
        var registered = false;
        try {
            var description = (PluginDescription) createPluginFromCandidate.invoke(loader, candidate);
            container = (PluginContainer) containerConstructor.newInstance(description);
            debug(debug, startedAt, "Created plugin description and container");
            var pluginModule = (Module) createModule.invoke(loader, container);
            var commonModule = createCommonModule(server, container);
            debug(debug, startedAt, "Created dependency injection modules");

            createPlugin.invoke(loader, container, (Object) new Module[]{pluginModule, commonModule});
            debug(debug, startedAt, "Created plugin instance");
            registerPlugin.invoke(server.getPluginManager(), container);
            registered = true;
            debug(debug, startedAt, "Registered plugin container");

            var instance = container.getInstance().orElseThrow(
                    () -> new IllegalStateException("Velocity did not create a plugin instance"));
            registerInternally.invoke(server.getEventManager(), container, instance);
            debug(debug, startedAt, "Registered annotated event handlers");
            var initializeHandlers = fireForPlugin(server.getEventManager(), container, new ProxyInitializeEvent());
            debug(debug, startedAt, "Ran " + initializeHandlers + " ProxyInitializeEvent handlers");
            trackPluginChannels(server, container, channelsBeforeLoad, debug, startedAt);
            return container;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            VelocityCleanupException rollbackFailure;
            if (container == null) {
                rollbackFailure = rollbackFailedCandidateLoad(classLoadersBeforeLoad, debug, startedAt);
            } else {
                trackPluginChannels(server, container, channelsBeforeLoad, debug, startedAt);
                rollbackFailure = rollbackFailedLoad(
                        server, container, registered, classLoadersBeforeLoad, debug, startedAt);
            }
            if (rollbackFailure != null) exception.addSuppressed(rollbackFailure);
            throw exception;
        }
    }

    void unload(ProxyServer server, PluginContainer container, Consumer<String> debug) throws ReflectiveOperationException {
        var startedAt = System.nanoTime();
        var instance = container.getInstance().orElseThrow(
                () -> new IllegalStateException("Velocity plugin has no instance"));
        var classLoader = instance.getClass().getClassLoader();
        var failures = new ArrayList<CleanupFailure>();

        cleanupStep("ProxyShutdownEvent", failures, debug, startedAt, () -> {
            var shutdownHandlers = fireForPlugin(server.getEventManager(), container, new ProxyShutdownEvent());
            debug(debug, startedAt, "Ran " + shutdownHandlers + " ProxyShutdownEvent handlers");
        });
        cleanupStep("event listeners", failures, debug, startedAt,
                () -> server.getEventManager().unregisterListeners(instance));
        cleanupStep("scheduled tasks", failures, debug, startedAt, () -> {
            var tasks = List.copyOf(server.getScheduler().tasksByPlugin(instance));
            for (ScheduledTask task : tasks) task.cancel();
            debug(debug, startedAt, "Cancelled " + tasks.size() + " scheduled tasks");
        });
        cleanupStep("commands", failures, debug, startedAt, () -> {
            var commandCount = unregisterCommands(server, container, instance);
            debug(debug, startedAt, "Unregistered " + commandCount + " command aliases");
        });
        cleanupStep("messaging channels", failures, debug, startedAt, () -> {
            var channelCount = unregisterPluginChannels(server, container, classLoader);
            debug(debug, startedAt, "Unregistered " + channelCount + " messaging channels");
        });
        var leakSnapshot = captureLeakSnapshot(server, container, instance, classLoader, debug, startedAt);
        cleanupStep("plugin registry", failures, debug, startedAt, () -> {
            var removed = pluginMap(server.getPluginManager()).remove(container.getDescription().getId()) != null;
            debug(debug, startedAt, "Removed plugin registry entry: " + removed);
        });
        cleanupStep("instance registry", failures, debug, startedAt, () -> {
            var removed = instanceMap(server.getPluginManager()).remove(instance) != null;
            debug(debug, startedAt, "Removed instance registry entry: " + removed);
        });
        cleanupStep("plugin classloader", failures, debug, startedAt, () -> {
            if (classLoader instanceof Closeable closeable) {
                closeable.close();
                debug(debug, startedAt, "Closed plugin classloader");
            } else {
                debug(debug, startedAt, "Plugin classloader is not closeable");
            }
        });

        if (classLoader != null) inspectLeaks(leakSnapshot, classLoader, debug, startedAt);
        pluginChannelsByClassLoader.remove(classLoader);
        if (!failures.isEmpty()) {
            throw new VelocityCleanupException(container.getDescription().getId(), failures);
        }
    }

    private LeakSnapshot captureLeakSnapshot(ProxyServer server,
                                             PluginContainer container,
                                             Object instance,
                                             ClassLoader classLoader,
                                             Consumer<String> debug,
                                             long startedAt) {
        if (debug == null) return null;

        try {
            return new LeakSnapshot(
                    countListeners(server.getEventManager(), container),
                    server.getScheduler().tasksByPlugin(instance).size(),
                    findOwnedCommandAliases(server, container, instance),
                    findRemainingPluginChannels(server, classLoader));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            debug(debug, startedAt, "WARNING: unload registration leak check failed: " + exception);
            return null;
        }
    }

    private void inspectLeaks(LeakSnapshot snapshot,
                              ClassLoader classLoader,
                              Consumer<String> debug,
                              long startedAt) {
        if (debug == null) return;

        try {
            var remainingListeners = snapshot == null ? -1 : snapshot.listeners();
            var remainingTasks = snapshot == null ? -1 : snapshot.tasks();
            var remainingCommands = snapshot == null ? List.<String>of() : snapshot.commands();
            var remainingChannels = snapshot == null ? List.<String>of() : snapshot.channels();
            var remainingThreads = findOwnedThreads(classLoader);

            if (snapshot != null && remainingListeners == 0 && remainingTasks == 0
                    && remainingCommands.isEmpty() && remainingChannels.isEmpty() && remainingThreads.isEmpty()) {
                debug(debug, startedAt,
                        "Leak check passed: no listeners, tasks, commands, classloader-owned messaging channels, "
                                + "or plugin threads remain");
                return;
            }

            if (snapshot != null) {
                debug(debug, startedAt, "WARNING: unload leak check found " + remainingListeners
                        + " listeners, " + remainingTasks + " tasks, " + remainingCommands.size()
                        + " command aliases, " + remainingChannels.size()
                        + " classloader-owned messaging channels, and "
                        + remainingThreads.size() + " plugin threads still registered");
            } else if (!remainingThreads.isEmpty()) {
                debug(debug, startedAt, "WARNING: unload leak check found " + remainingThreads.size()
                        + " plugin threads still running; registration leak counts were unavailable");
            }
            if (!remainingCommands.isEmpty()) {
                debug(debug, startedAt, "WARNING: remaining command aliases: " + String.join(", ", remainingCommands));
            }
            if (!remainingChannels.isEmpty()) {
                debug(debug, startedAt,
                        "WARNING: messaging channels still owned by the unloaded classloader: "
                                + String.join(", ", remainingChannels));
            }
            if (!remainingThreads.isEmpty()) {
                debug(debug, startedAt, "WARNING: remaining plugin threads: " + String.join(", ", remainingThreads));
            }
        } catch (RuntimeException exception) {
            debug(debug, startedAt, "WARNING: unload leak check failed: " + exception);
        }
    }

    @SuppressWarnings("unchecked")
    private int countListeners(EventManager manager, PluginContainer container) throws IllegalAccessException {
        var handlers = (Multimap<Class<?>, Object>) handlersByType.get(manager);
        var count = 0;
        for (var registration : handlers.values()) {
            if (handlerPlugin.get(registration) == container) count++;
        }
        return count;
    }

    private List<String> findOwnedCommandAliases(ProxyServer server,
                                                  PluginContainer container,
                                                  Object instance) {
        var aliases = new ArrayList<String>();
        var commandManager = server.getCommandManager();
        for (var alias : commandManager.getAliases()) {
            var meta = commandManager.getCommandMeta(alias);
            if (meta == null) continue;
            var owner = meta.getPlugin();
            if (owner == container || owner == instance) aliases.add(alias);
        }
        aliases.sort(String.CASE_INSENSITIVE_ORDER);
        return aliases;
    }

    private List<String> findOwnedThreads(ClassLoader classLoader) {
        var threads = new ArrayList<String>();
        for (var thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getContextClassLoader() == classLoader
                    || thread.getClass().getClassLoader() == classLoader) {
                threads.add(thread.getName() + " [" + thread.getState() + "]");
            }
        }
        threads.sort(String.CASE_INSENSITIVE_ORDER);
        return threads;
    }

    private void trackPluginChannels(ProxyServer server,
                                     PluginContainer container,
                                     Set<ChannelIdentifier> channelsBeforeLoad,
                                     Consumer<String> debug,
                                     long startedAt) {
        if (channelIdentifiers == null) return;
        try {
            var addedChannels = new HashSet<>(registeredChannels(server));
            addedChannels.removeAll(channelsBeforeLoad);
            if (addedChannels.isEmpty()) return;
            var instance = container.getInstance().orElse(null);
            if (instance == null) return;
            var classLoader = instance.getClass().getClassLoader();
            pluginChannelsByClassLoader.merge(classLoader, addedChannels, (tracked, added) -> {
                var merged = new HashSet<>(tracked);
                merged.addAll(added);
                return merged;
            });
            debug(debug, startedAt, "Associated " + addedChannels.size()
                    + " newly registered messaging channels with classloader " + describeClassLoader(classLoader));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            debug(debug, startedAt, "WARNING: messaging channel tracking failed: " + exception);
        }
    }

    private int unregisterPluginChannels(ProxyServer server,
                                         PluginContainer container,
                                         ClassLoader classLoader) throws IllegalAccessException {
        var channels = findPluginChannels(classLoader);
        if (channels == null || channels.isEmpty()) return 0;
        server.getChannelRegistrar().unregister(channels.toArray(ChannelIdentifier[]::new));
        return channels.size();
    }

    private Set<ChannelIdentifier> findPluginChannels(ClassLoader classLoader) {
        return classLoader == null
                ? new HashSet<ChannelIdentifier>()
                : new HashSet<>(pluginChannelsByClassLoader.getOrDefault(classLoader, Set.of()));
    }

    private List<String> findRemainingPluginChannels(ProxyServer server, ClassLoader classLoader)
            throws IllegalAccessException {
        if (classLoader == null) return List.of();
        var tracked = pluginChannelsByClassLoader.get(classLoader);
        if (tracked == null || tracked.isEmpty()) return List.of();
        var registered = registeredChannels(server);
        return tracked.stream().filter(registered::contains).map(ChannelIdentifier::getId)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static String describeClassLoader(ClassLoader classLoader) {
        return classLoader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(classLoader));
    }

    @SuppressWarnings("unchecked")
    private Set<ChannelIdentifier> registeredChannels(ProxyServer server) throws IllegalAccessException {
        if (channelIdentifiers == null) return Set.of();
        var identifiers = (Map<String, ChannelIdentifier>) channelIdentifiers.get(server.getChannelRegistrar());
        return new HashSet<>(identifiers.values());
    }

    private static void cleanupStep(String name,
                                    List<CleanupFailure> failures,
                                    Consumer<String> debug,
                                    long startedAt,
                                    CleanupAction action) {
        try {
            action.run();
            debug(debug, startedAt, "Completed cleanup step: " + name);
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError exception) {
            failures.add(new CleanupFailure(name, exception));
            debug(debug, startedAt, "WARNING: cleanup step failed (" + name + "): " + exception);
        }
    }

    private int unregisterCommands(ProxyServer server, PluginContainer container, Object instance) {
        var commandManager = server.getCommandManager();
        var removed = 0;
        for (var alias : List.copyOf(commandManager.getAliases())) {
            var meta = commandManager.getCommandMeta(alias);
            if (meta == null) continue;

            var owner = meta.getPlugin();
            if (owner == container || owner == instance) {
                commandManager.unregister(alias);
                removed++;
            }
        }
        return removed;
    }

    private VelocityCleanupException rollbackFailedLoad(ProxyServer server,
                                                        PluginContainer container,
                                                        boolean registered,
                                                        Set<Closeable> classLoadersBeforeLoad,
                                                        Consumer<String> debug,
                                                        long startedAt) {
        var instance = container.getInstance().orElse(null);
        var classLoader = instance == null ? null : instance.getClass().getClassLoader();
        var failures = new ArrayList<CleanupFailure>();

        cleanupStep("rollback event listeners", failures, debug, startedAt,
                () -> unregisterRollbackListeners(server, instance));
        cleanupStep("rollback scheduled tasks", failures, debug, startedAt,
                () -> cancelRollbackTasks(server, instance));
        cleanupStep("rollback commands", failures, debug, startedAt,
                () -> unregisterRollbackCommands(server, container, instance));
        cleanupStep("rollback messaging channels", failures, debug, startedAt,
                () -> unregisterPluginChannels(server, container, classLoader));
        var leakSnapshot = captureRollbackLeakSnapshot(
                server, container, instance, classLoader, registered, debug, startedAt);
        cleanupStep("rollback plugin registry", failures, debug, startedAt,
                () -> removeRollbackPlugin(server, container, registered));
        cleanupStep("rollback instance registry", failures, debug, startedAt,
                () -> removeRollbackInstance(server, instance, registered));
        cleanupStep("rollback plugin classloader", failures, debug, startedAt,
                () -> closeRollbackClassLoader(classLoader));
        cleanupStep("rollback orphaned plugin classloaders", failures, debug, startedAt,
                () -> closeNewPluginClassLoaders(classLoadersBeforeLoad));

        if (classLoader != null) inspectLeaks(leakSnapshot, classLoader, debug, startedAt);
        if (classLoader != null) pluginChannelsByClassLoader.remove(classLoader);
        if (failures.isEmpty()) return null;

        return new VelocityCleanupException(container.getDescription().getId(), failures);
    }

    private VelocityCleanupException rollbackFailedCandidateLoad(Set<Closeable> classLoadersBeforeLoad,
                                                                 Consumer<String> debug,
                                                                 long startedAt) {
        var failures = new ArrayList<CleanupFailure>();
        cleanupStep("rollback candidate classloader", failures, debug, startedAt,
                () -> closeNewPluginClassLoaders(classLoadersBeforeLoad));
        if (failures.isEmpty()) return null;
        return new VelocityCleanupException("candidate", failures);
    }

    private void unregisterRollbackListeners(ProxyServer server, Object instance) {
        if (instance != null) server.getEventManager().unregisterListeners(instance);
    }

    private void cancelRollbackTasks(ProxyServer server, Object instance) {
        if (instance == null) return;
        for (ScheduledTask task : List.copyOf(server.getScheduler().tasksByPlugin(instance))) {
            task.cancel();
        }
    }

    private void unregisterRollbackCommands(ProxyServer server, PluginContainer container, Object instance) {
        if (instance != null) unregisterCommands(server, container, instance);
    }

    private LeakSnapshot captureRollbackLeakSnapshot(ProxyServer server,
                                                      PluginContainer container,
                                                      Object instance,
                                                      ClassLoader classLoader,
                                                      boolean registered,
                                                      Consumer<String> debug,
                                                      long startedAt) {
        if (!registered || instance == null) return null;
        return captureLeakSnapshot(server, container, instance, classLoader, debug, startedAt);
    }

    private void removeRollbackPlugin(ProxyServer server, PluginContainer container, boolean registered)
            throws IllegalAccessException {
        if (registered) pluginMap(server.getPluginManager()).remove(container.getDescription().getId());
    }

    private void removeRollbackInstance(ProxyServer server, Object instance, boolean registered)
            throws IllegalAccessException {
        if (registered && instance != null) instanceMap(server.getPluginManager()).remove(instance);
    }

    private void closeRollbackClassLoader(ClassLoader classLoader) throws IOException {
        if (classLoader instanceof Closeable closeable) closeable.close();
    }

    private void closeNewPluginClassLoaders(Set<Closeable> classLoadersBeforeLoad)
            throws IllegalAccessException, IOException {
        var currentClassLoaders = registeredPluginClassLoaders();
        currentClassLoaders.removeAll(classLoadersBeforeLoad);
        IOException failure = null;
        for (var classLoader : currentClassLoaders) {
            try {
                classLoader.close();
            } catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    @SuppressWarnings("unchecked")
    private Set<Closeable> registeredPluginClassLoaders() throws IllegalAccessException {
        if (pluginClassLoaders == null) return Set.of();
        return new HashSet<>((Set<Closeable>) pluginClassLoaders.get(null));
    }

    private AbstractModule createCommonModule(ProxyServer server, PluginContainer loadingContainer) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProxyServer.class).toInstance(server);
                bind(PluginManager.class).toInstance(server.getPluginManager());
                bind(EventManager.class).toInstance(server.getEventManager());
                bind(CommandManager.class).toInstance(server.getCommandManager());

                var containers = new ArrayList<>(server.getPluginManager().getPlugins());
                containers.add(loadingContainer);
                for (var container : containers) {
                    bind(PluginContainer.class)
                            .annotatedWith(Names.named(container.getDescription().getId()))
                            .toInstance(container);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, PluginContainer> pluginMap(PluginManager manager) throws IllegalAccessException {
        return (Map<String, PluginContainer>) pluginsById.get(manager);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, PluginContainer> instanceMap(PluginManager manager) throws IllegalAccessException {
        return (Map<Object, PluginContainer>) pluginInstances.get(manager);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int fireForPlugin(EventManager manager, PluginContainer container, Object event)
            throws ReflectiveOperationException {
        var handlers = (Multimap<Class<?>, Object>) handlersByType.get(manager);
        var registrations = new ArrayList<>(handlers.get(event.getClass()));
        registrations.removeIf(registration -> {
            try {
                return handlerPlugin.get(registration) != container;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        });

        var comparator = (Comparator) handlerComparator.get(
                Modifier.isStatic(handlerComparator.getModifiers()) ? null : manager);
        registrations.sort(comparator);
        if (registrations.isEmpty()) return 0;

        var registrationType = targetedFire.getParameterTypes()[4].getComponentType();
        var registrationArray = Array.newInstance(registrationType, registrations.size());
        for (var index = 0; index < registrations.size(); index++) {
            Array.set(registrationArray, index, registrations.get(index));
        }

        var future = new CompletableFuture<>();
        targetedFire.invoke(manager, future, event, 0, true, registrationArray);
        future.join();
        return registrations.size();
    }

    private static void debug(Consumer<String> consumer, long startedAt, String message) {
        if (consumer != null) {
            consumer.accept(message + " after " + ((System.nanoTime() - startedAt) / 1_000_000L) + " ms");
        }
    }

    private static Method findTargetedFire(Class<?> type, Class<?> handlerClass, List<String> names)
            throws NoSuchMethodException {
        var handlerArray = Array.newInstance(handlerClass, 0).getClass();
        return findMethod(type, names, CompletableFuture.class, Object.class,
                int.class, boolean.class, handlerArray);
    }

    private static Method findMethod(Class<?> type, List<String> names, Class<?>... parameters)
            throws NoSuchMethodException {
        for (var name : names) {
            try {
                return accessible(type.getDeclaredMethod(name, parameters));
            } catch (NoSuchMethodException ignored) {
                // Try the compatibility name used by another Velocity generation.
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }

    private static Field findField(Class<?> type, List<String> names) throws NoSuchFieldException {
        for (var name : names) {
            try {
                return accessible(type.getDeclaredField(name));
            } catch (NoSuchFieldException ignored) {
                // Try the compatibility name used by another Velocity generation.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + String.join("/", names));
    }

    private static Field findOptionalField(Class<?> type, List<String> names) {
        try {
            return findField(type, names);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T object) {
        object.setAccessible(true);
        return object;
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run() throws ReflectiveOperationException, IOException;
    }

    private record CleanupFailure(String step, Throwable cause) {
    }

    private record LeakSnapshot(int listeners, int tasks, List<String> commands, List<String> channels) {
    }

    private static final class VelocityCleanupException extends ReflectiveOperationException {
        private static final long serialVersionUID = 1L;

        private VelocityCleanupException(String pluginId, List<CleanupFailure> failures) {
            super("Velocity cleanup for " + pluginId + " failed in " + failures.size() + " step(s): "
                    + failures.stream().map(CleanupFailure::step).toList());
            failures.forEach(failure -> addSuppressed(
                    new IllegalStateException("Cleanup step failed: " + failure.step(), failure.cause())));
        }
    }
}
