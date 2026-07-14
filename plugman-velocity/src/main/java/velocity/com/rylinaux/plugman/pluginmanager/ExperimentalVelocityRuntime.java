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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Capability-checked access to Velocity's unsupported runtime plugin lifecycle.
 */
final class ExperimentalVelocityRuntime {
    private final String adapterName;
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

    private ExperimentalVelocityRuntime(VelocityRuntimeAdapter adapter) throws ReflectiveOperationException {
        adapterName = adapter.name();
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
    }

    static ExperimentalVelocityRuntime detect() {
        try {
            var version = PlugManVelocity.getInstance().getServer().getVersion().getVersion();
            return new ExperimentalVelocityRuntime(VelocityRuntimeAdapters.find(version));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            PlugManVelocity.getInstance().getLogger().warn(
                    "Experimental Velocity plugin reload is unavailable on this Velocity build: {}",
                    exception.toString());
            return null;
        }
    }

    String adapterName() {
        return adapterName;
    }

    PluginDescription readDescription(ProxyServer server, Path source) throws ReflectiveOperationException {
        var loader = loaderConstructor.newInstance(server, source.getParent());
        return (PluginDescription) loadCandidate.invoke(loader, source);
    }

    PluginContainer load(ProxyServer server, Path source, Consumer<String> debug) throws ReflectiveOperationException {
        var startedAt = System.nanoTime();
        var loader = loaderConstructor.newInstance(server, source.getParent());
        debug(debug, startedAt, "Created JavaPluginLoader");
        var candidate = (PluginDescription) loadCandidate.invoke(loader, source);
        debug(debug, startedAt, "Loaded plugin candidate");
        var description = (PluginDescription) createPluginFromCandidate.invoke(loader, candidate);
        var container = (PluginContainer) containerConstructor.newInstance(description);
        debug(debug, startedAt, "Created plugin description and container");
        var registered = false;
        try {
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
            return container;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            rollbackFailedLoad(server, container, registered);
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
        var leakSnapshot = captureLeakSnapshot(server, container, instance, debug, startedAt);
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

        inspectLeaks(leakSnapshot, classLoader, debug, startedAt);
        if (!failures.isEmpty()) {
            throw new VelocityCleanupException(container.getDescription().getId(), failures);
        }
    }

    private LeakSnapshot captureLeakSnapshot(ProxyServer server,
                                             PluginContainer container,
                                             Object instance,
                                             Consumer<String> debug,
                                             long startedAt) {
        if (debug == null) return null;

        try {
            return new LeakSnapshot(
                    countListeners(server.getEventManager(), container),
                    server.getScheduler().tasksByPlugin(instance).size(),
                    findOwnedCommandAliases(server, container, instance));
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
            var remainingThreads = findOwnedThreads(classLoader);

            if (snapshot != null && remainingListeners == 0 && remainingTasks == 0
                    && remainingCommands.isEmpty() && remainingThreads.isEmpty()) {
                debug(debug, startedAt, "Leak check passed: no listeners, tasks, commands, or plugin threads remain");
                return;
            }

            if (snapshot != null) {
                debug(debug, startedAt, "WARNING: unload leak check found " + remainingListeners
                        + " listeners, " + remainingTasks + " tasks, " + remainingCommands.size()
                        + " command aliases, and " + remainingThreads.size() + " plugin threads still registered");
            } else if (!remainingThreads.isEmpty()) {
                debug(debug, startedAt, "WARNING: unload leak check found " + remainingThreads.size()
                        + " plugin threads still running; registration leak counts were unavailable");
            }
            if (!remainingCommands.isEmpty()) {
                debug(debug, startedAt, "WARNING: remaining command aliases: " + String.join(", ", remainingCommands));
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

    private void rollbackFailedLoad(ProxyServer server, PluginContainer container, boolean registered) {
        var instance = container.getInstance().orElse(null);
        try {
            if (registered && instance != null) {
                server.getEventManager().unregisterListeners(instance);
                for (ScheduledTask task : List.copyOf(server.getScheduler().tasksByPlugin(instance))) {
                    task.cancel();
                }
                unregisterCommands(server, container, instance);
                pluginMap(server.getPluginManager()).remove(container.getDescription().getId());
                instanceMap(server.getPluginManager()).remove(instance);
            }

            if (instance != null && instance.getClass().getClassLoader() instanceof Closeable closeable) {
                closeable.close();
            }
        } catch (Exception rollbackException) {
            PlugManVelocity.getInstance().getLogger().error(
                    "Failed to roll back an incomplete experimental Velocity plugin load",
                    rollbackException);
        }
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

    private record LeakSnapshot(int listeners, int tasks, List<String> commands) {
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
