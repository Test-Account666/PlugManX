package bungee.com.rylinaux.plugman.plugin;

import core.com.rylinaux.plugman.plugins.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Bungee implementation of the core Plugin interface.
 * Wraps a Bungee Plugin to provide the core abstraction.
 */
public record BungeePlugin(net.md_5.bungee.api.plugin.Plugin bungeePlugin) implements Plugin {
    @Override
    public String getName() {
        return bungeePlugin.getDescription().getName();
    }

    @Override
    public boolean isEnabled() {
        // Bungee plugins don't have an enabled/disabled state like Bukkit
        // They are either loaded or not loaded
        return true;
    }

    @Override
    public String getVersion() {
        return bungeePlugin.getDescription().getVersion();
    }

    @Override
    public List<String> getDepend() {
        return new ArrayList<>(bungeePlugin.getDescription().getDepends());
    }

    @Override
    public List<String> getSoftDepend() {
        return new ArrayList<>(bungeePlugin.getDescription().getSoftDepends());
    }

    @Override
    public List<String> getAuthors() {
        return List.of(bungeePlugin.getDescription().getAuthor());
    }

    @Override
    public File getFile() {
        return bungeePlugin.getFile();
    }

    @SuppressWarnings("unchecked")
    @Override
    public net.md_5.bungee.api.plugin.Plugin getHandle() {
        return bungeePlugin();
    }
}