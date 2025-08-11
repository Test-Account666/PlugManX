package velocity.com.rylinaux.plugman.plugin;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import core.com.rylinaux.plugman.plugins.Plugin;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Velocity implementation of the core Plugin interface.
 * Wraps a Velocity PluginContainer to provide the core abstraction.
 */
public record VelocityPlugin(PluginContainer pluginContainer, Object instance) implements Plugin {
    @Override
    public String getName() {
        return pluginContainer.getDescription().getId();
    }

    @Override
    public boolean isEnabled() {
        // Velocity plugins don't have an enabled/disabled state like Bukkit
        // They are either loaded or not loaded
        return true;
    }

    @Override
    public String getVersion() {
        return pluginContainer().getDescription().getVersion().orElse("Unknown");
    }

    @Override
    public List<String> getDepend() {
        return pluginContainer().getDescription().getDependencies().stream().filter(dep -> !dep.isOptional()).map(PluginDependency::getId).toList();
    }

    @Override
    public List<String> getSoftDepend() {
        return pluginContainer().getDescription().getDependencies().stream().filter(PluginDependency::isOptional).map(PluginDependency::getId).toList();
    }

    @Override
    public List<String> getAuthors() {
        return new ArrayList<>(pluginContainer.getDescription().getAuthors());
    }

    @SneakyThrows
    @Override
    public File getFile() {
        return Path.of(instance().getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getHandle() {
        try {
            return (T) instance();
        } catch (ClassCastException exception) {
            return (T) pluginContainer();
        }
    }
}