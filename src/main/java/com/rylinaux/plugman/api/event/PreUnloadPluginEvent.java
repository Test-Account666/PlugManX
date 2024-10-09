package com.rylinaux.plugman.api.event;

import com.rylinaux.plugman.PlugMan;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class PreUnloadPluginEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    private @NotNull final Plugin plugin;

    private String cancelledReason;
    private boolean isCancelled;

    public PreUnloadPluginEvent(@NotNull Plugin plugin) {
        this.plugin = plugin;
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

    public void setCancelledReason(@NotNull String reason) {
        this.cancelledReason = reason;
    }

    public @NotNull String getCancelledReason() {
        if (this.cancelledReason == null) {
            // default message
            return PlugMan.getInstance().getMessageFormatter().format("cancel.unload", plugin.getName());
        }
        return cancelledReason;
    }

    public @NotNull Plugin getPlugin() {
        return plugin;
    }
}