package velocity.com.rylinaux.plugman.pluginmanager;

import java.util.List;

final class Velocity4RuntimeAdapter implements VelocityRuntimeAdapter {
    private static final ReflectionLayout REFLECTION_LAYOUT = new ReflectionLayout(
            "com.velocitypowered.proxy.plugin.loader.java.JavaPluginLoader",
            "com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer",
            "com.velocitypowered.proxy.plugin.VelocityPluginManager",
            "com.velocitypowered.proxy.event.VelocityEventManager",
            "com.velocitypowered.proxy.event.VelocityEventManager$HandlerRegistration",
            List.of("loadCandidate", "loadPluginDescription"),
            List.of("createPluginFromCandidate", "loadPlugin"),
            List.of("createModule"),
            List.of("createPlugin"),
            List.of("registerPlugin"),
            List.of("registerInternally"),
            List.of("pluginsById", "plugins"),
            List.of("pluginInstances"),
            List.of("handlersByType"),
            List.of("handlerComparator"),
            List.of("plugin"),
            List.of("fire")
    );

    @Override
    public boolean supports(String version) {
        return VelocityRuntimeAdapters.compare(version, 4, 0, 0) >= 0;
    }

    @Override
    public String name() {
        return "Velocity 4.0 runtime adapter";
    }

    @Override
    public ReflectionLayout reflectionLayout() {
        return REFLECTION_LAYOUT;
    }

    @Override
    public boolean supportsPacketRegistryCleanup() {
        return true;
    }
}
