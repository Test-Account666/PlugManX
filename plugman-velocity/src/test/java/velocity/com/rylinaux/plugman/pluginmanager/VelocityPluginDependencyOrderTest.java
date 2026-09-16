package velocity.com.rylinaux.plugman.pluginmanager;

import core.com.rylinaux.plugman.plugins.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityPluginDependencyOrderTest {

    @Test
    void ordersDependenciesBeforeDependents() {
        var library = plugin("library");
        var feature = plugin("feature", "library");
        var extension = plugin("extension", "feature");

        var order = VelocityPluginManager.dependencyOrder(List.of(extension, feature, library));

        assertEquals(List.of("library", "feature", "extension"), names(order));
    }

    @Test
    void keepsIndependentPluginsDeterministic() {
        var order = VelocityPluginManager.dependencyOrder(List.of(plugin("Zulu"), plugin("alpha")));

        assertEquals(List.of("alpha", "Zulu"), names(order));
    }

    @Test
    void emitsEveryPluginOnceWhenDependenciesCycle() {
        var first = plugin("first", "second");
        var second = plugin("second", "first");

        var order = VelocityPluginManager.dependencyOrder(List.of(first, second));

        assertEquals(2, order.size());
        assertEquals(2, order.stream().map(Plugin::getName).distinct().count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PlugManX", "plugmanvelocity", "LuckPerms", "geyser", "geyser-velocity", "VelocityScoreboardAPI"
    })
    void marksCriticalPluginsAsForceProtected(String pluginId) {
        assertTrue(VelocityPluginManager.isForceProtectedPluginId(pluginId));
    }

    @Test
    void doesNotForceProtectRegularPlugins() {
        assertFalse(VelocityPluginManager.isForceProtectedPluginId("example"));
    }

    private static Plugin plugin(String name, String... dependencies) {
        return new TestPlugin(name, List.of(dependencies));
    }

    private static List<String> names(List<Plugin> plugins) {
        return plugins.stream().map(Plugin::getName).toList();
    }

    private record TestPlugin(String name, List<String> dependencies) implements Plugin {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public List<String> getDepend() {
            return dependencies;
        }

        @Override
        public List<String> getSoftDepend() {
            return List.of();
        }

        @Override
        public List<String> getAuthors() {
            return List.of();
        }

        @Override
        public File getFile() {
            return null;
        }

        @Override
        public <T> T getHandle() {
            return null;
        }
    }
}
