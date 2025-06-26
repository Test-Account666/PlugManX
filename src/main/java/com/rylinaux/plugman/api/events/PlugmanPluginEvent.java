package com.rylinaux.plugman.api.events;

import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

public abstract class PlugmanPluginEvent extends Event {

    protected final Plugin plugin;
    protected final boolean isPaperPlugin;

    public PlugmanPluginEvent(Plugin plugin, boolean isPaperPlugin) {
        this.plugin = plugin;
        this.isPaperPlugin = isPaperPlugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public boolean isPaperPlugin() {
        return isPaperPlugin;
    }
}
