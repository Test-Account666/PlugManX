package com.rylinaux.plugman.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreDisablePluginEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS_LIST = new HandlerList();
    private @Nullable final Plugin plugin;
    private final boolean disableAll;
    private boolean isCancelled;

    public PreDisablePluginEvent(@Nullable Plugin plugin, boolean disableAll) {
        this.plugin = plugin;
        this.disableAll = disableAll;
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


    public @Nullable Plugin getPlugin() {
        return plugin;
    }

    public boolean getDisableAll() {
        return disableAll;
    }
}
