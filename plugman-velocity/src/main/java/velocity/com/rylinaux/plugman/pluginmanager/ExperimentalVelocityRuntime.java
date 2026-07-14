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
    private static final String JAVA_PLUGIN_LOADER =
            "com.velocitypowered.proxy.plugin.loader.java.JavaPluginLoader";
    private static final String VELOCITY_PLUGIN_CONTAINER =
            "com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer";

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

    private ExperimentalVelocityRuntime() throws ReflectiveOperationException {
        var loaderClass = Class.forName(JAVA_PLUGIN_LOADER);
        var containerClass = Class.forName(VELOCITY_PLUGIN_CONTAINER);
        var pluginManagerClass = Class.forName("com.velocitypowered.proxy.plugin.VelocityPluginManager");
        var eventManagerClass = Class.forName("com.velocitypowered.proxy.event.VelocityEventManager");

        loaderConstructor = accessible(loaderClass.getDeclaredConstructor(ProxyServer.class, Path.class));
        containerConstructor = accessible(containerClass.getDeclaredConstructor(PluginDescription.class));
        loadCandidate = findMethod(loaderClass, List.of("loadCandidate", "loadPluginDescription"), Path.class);
        createPluginFromCandidate = findMethod(loaderClass,
                List.of("createPluginFromCandidate", "loadPlugin"), PluginDescription.class);
        createModule = findMethod(loaderClass, List.of("createModule"), PluginContainer.class);
        createPlugin = findMethod(loaderClass, List.of("createPlugin"),
                PluginContainer.class, Module[].class);
        registerPlugin = findMethod(pluginManagerClass, List.of("registerPlugin"), PluginContainer.class);
        pluginsById = findField(pluginManagerClass, "pluginsById", "plugins");
        pluginInstances = findField(pluginManagerClass, "pluginInstances");
        registerInternally = findMethod(eventManagerClass, List.of("registerInternally"),
                PluginContainer.class, Object.class);
        handlersByType = findField(eventManagerClass, "handlersByType");
        handlerComparator = findField(eventManagerClass, "handlerComparator");

        var handlerClass = Class.forName(
                "com.velocitypowered.proxy.event.VelocityEventManager$HandlerRegistration");
        handlerPlugin = findField(handlerClass, "plugin");
        targetedFire = findTargetedFire(eventManagerClass, handlerClass);
    }

    static ExperimentalVelocityRuntime detect() {
        try {
            return new ExperimentalVelocityRuntime();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            PlugManVelocity.getInstance().getLogger().warn(
                    "Experimental Velocity plugin reload is unavailable on this Velocity build: {}",
                    exception.toString());
            return null;
        }
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

        var shutdownHandlers = fireForPlugin(server.getEventManager(), container, new ProxyShutdownEvent());
        debug(debug, startedAt, "Ran " + shutdownHandlers + " ProxyShutdownEvent handlers");
        server.getEventManager().unregisterListeners(instance);
        debug(debug, startedAt, "Unregistered event listeners");

        var tasks = List.copyOf(server.getScheduler().tasksByPlugin(instance));
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        debug(debug, startedAt, "Cancelled " + tasks.size() + " scheduled tasks");

        var commandCount = unregisterCommands(server, container, instance);
        debug(debug, startedAt, "Unregistered " + commandCount + " command aliases");
        var pluginRemoved = pluginMap(server.getPluginManager()).remove(container.getDescription().getId()) != null;
        var instanceRemoved = instanceMap(server.getPluginManager()).remove(instance) != null;
        debug(debug, startedAt, "Removed plugin registry entry: " + pluginRemoved
                + ", instance registry entry: " + instanceRemoved);

        var classLoader = instance.getClass().getClassLoader();
        if (classLoader instanceof Closeable closeable) {
            try {
                closeable.close();
                debug(debug, startedAt, "Closed plugin classloader");
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to close the Velocity plugin classloader", exception);
            }
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

    private static Method findTargetedFire(Class<?> type, Class<?> handlerClass)
            throws NoSuchMethodException {
        var handlerArray = Array.newInstance(handlerClass, 0).getClass();
        return accessible(type.getDeclaredMethod("fire", CompletableFuture.class, Object.class,
                int.class, boolean.class, handlerArray));
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

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
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
}
