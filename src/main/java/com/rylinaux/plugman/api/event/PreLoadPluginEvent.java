package com.rylinaux.plugman.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class PreLoadPluginEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS_LIST = new HandlerList();
    private final @NotNull Path pluginPath;
    private final @NotNull String pluginName;
    private boolean isCancelled;

    public PreLoadPluginEvent(@NotNull Path pluginPath, @NotNull String pluginName) {
        this.pluginPath = pluginPath;
        this.pluginName = pluginName;
        this.isCancelled = false;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.isCancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    public @NotNull Path getPluginPath() {
        return pluginPath;
    }

    public @NotNull String getPluginName() {
        return pluginName;
    }
}