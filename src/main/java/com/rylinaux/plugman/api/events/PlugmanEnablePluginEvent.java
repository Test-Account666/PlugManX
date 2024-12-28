package com.rylinaux.plugman.api.events;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class PlugmanEnablePluginEvent extends PlugmanPluginEvent {


    private static final HandlerList handlers = new HandlerList();

    public PlugmanEnablePluginEvent(final Plugin plugin, final boolean isPaperPlugin) {
        super(plugin, isPaperPlugin);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return PlugmanEnablePluginEvent.handlers;
    }

    public static HandlerList getHandlerList() {
        return PlugmanEnablePluginEvent.handlers;
    }
}
