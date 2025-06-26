package com.rylinaux.plugman.api.events;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class PlugmanDisablePluginEvent extends PlugmanPluginEvent {

    private static final HandlerList handlers = new HandlerList();


    public PlugmanDisablePluginEvent(Plugin plugin, boolean isPaperPlugin) {
        super(plugin, isPaperPlugin);
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
