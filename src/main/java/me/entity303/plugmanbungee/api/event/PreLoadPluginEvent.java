package me.entity303.plugmanbungee.api.event;

import net.md_5.bungee.api.plugin.Cancellable;
import net.md_5.bungee.api.plugin.Event;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class PreLoadPluginEvent extends Event implements Cancellable {
    private @NotNull final Path pluginPath;
    private @NotNull final String pluginName;
    private boolean isCancelled;

    public PreLoadPluginEvent(@NotNull Path pluginPath, @NotNull String pluginName) {
        this.pluginPath = pluginPath;
        this.pluginName = pluginName;
        this.isCancelled = false;
    }

    public @NotNull Path getPluginPath() {
        return pluginPath;
    }

    public @NotNull String getPluginName() {
        return pluginName;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        isCancelled = b;
    }
}