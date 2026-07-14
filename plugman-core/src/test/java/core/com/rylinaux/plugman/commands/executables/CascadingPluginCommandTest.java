package core.com.rylinaux.plugman.commands.executables;

import core.com.rylinaux.plugman.plugins.Plugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CascadingPluginCommandTest {

    @Test
    void createsDependencyFirstLoadOrderWithoutDuplicates() {
        var dependency = new TestPlugin("Dependency", List.of(), List.of());
        var softDependency = new TestPlugin("SoftDependency", List.of(), List.of());
        var plugin = new TestPlugin("Plugin", List.of("Dependency"), List.of("SoftDependency"));

        var plan = CascadingPluginCommand.createDependencyPlan(
                List.of(plugin, dependency, softDependency, plugin), true);

        assertEquals(List.of("Dependency", "SoftDependency", "Plugin"),
                plan.loadOrder().stream().map(Plugin::getName).toList());
        assertEquals(List.of(), plan.cycles());
    }

    @Test
    void ignoresSoftDependenciesWhenDisabled() {
        var softDependency = new TestPlugin("SoftDependency", List.of(), List.of());
        var plugin = new TestPlugin("Plugin", List.of(), List.of("SoftDependency"));

        var plan = CascadingPluginCommand.createDependencyPlan(List.of(plugin, softDependency), false);

        assertEquals(List.of("Plugin", "SoftDependency"),
                plan.loadOrder().stream().map(Plugin::getName).toList());
    }

    @Test
    void resolvesDependenciesThroughProvidedNames() {
        var provider = new TestPlugin("Provider", List.of(), List.of(), List.of("LegacyApi"));
        var plugin = new TestPlugin("Plugin", List.of("LegacyApi"), List.of());

        var plan = CascadingPluginCommand.createDependencyPlan(List.of(plugin, provider), false);

        assertEquals(List.of("Provider", "Plugin"),
                plan.loadOrder().stream().map(Plugin::getName).toList());
    }

    @Test
    void reportsDependencyCycles() {
        var first = new TestPlugin("First", List.of("Second"), List.of());
        var second = new TestPlugin("Second", List.of("First"), List.of());

        var plan = CascadingPluginCommand.createDependencyPlan(List.of(first, second), false);

        assertEquals(List.of("First -> Second -> First"), plan.cycles());
        assertEquals(2, plan.loadOrder().size());
    }

    private record TestPlugin(String name,
                              List<String> depend,
                              List<String> softDepend,
                              List<String> provides) implements Plugin {
        private TestPlugin(String name, List<String> depend, List<String> softDepend) {
            this(name, depend, softDepend, List.of());
        }

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
            return "1.0";
        }

        @Override
        public List<String> getDepend() {
            return depend;
        }

        @Override
        public List<String> getSoftDepend() {
            return softDepend;
        }

        @Override
        public List<String> getProvides() {
            return provides;
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
