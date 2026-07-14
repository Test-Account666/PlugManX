package velocity.com.rylinaux.plugman.pluginmanager;

import java.util.List;

interface VelocityRuntimeAdapter {

    boolean supports(String version);

    String name();

    ReflectionLayout reflectionLayout();

    record ReflectionLayout(
            String javaPluginLoaderClass,
            String pluginContainerClass,
            String pluginManagerClass,
            String eventManagerClass,
            String handlerRegistrationClass,
            List<String> loadCandidateMethods,
            List<String> createPluginFromCandidateMethods,
            List<String> createModuleMethods,
            List<String> createPluginMethods,
            List<String> registerPluginMethods,
            List<String> registerInternallyMethods,
            List<String> pluginMapFields,
            List<String> instanceMapFields,
            List<String> handlersByTypeFields,
            List<String> handlerComparatorFields,
            List<String> handlerPluginFields,
            List<String> targetedFireMethods
    ) {
    }
}
