package com.rylinaux.plugman.api.event;

import com.rylinaux.plugman.PlugMan;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreDisablePluginEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    /**If all plugin, then this item is null*/
    private @Nullable final Plugin plugin;
    /**Determine whether you are ready to disable all plugin*/
    private final boolean isDisableAll;

    private String cancelledReason;
    private boolean isCancelled;

    public PreDisablePluginEvent(@Nullable Plugin plugin, boolean isDisableAll) {
        this.plugin = plugin;
        this.isDisableAll = isDisableAll;
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
            if (plugin != null) {
                return PlugMan.getInstance().getMessageFormatter().format("cancel.disable.one", plugin.getName());
            }
            return PlugMan.getInstance().getMessageFormatter().format("cancel.disable.all");
        }
        return cancelledReason;
    }

    /**If all plugin, then this item is null*/
    public @Nullable Plugin getPlugin() {
        return plugin;
    }

    /**Determine whether you are ready to disable all plugin*/
    public boolean isDisableAll() {
        return isDisableAll;
    }
}
