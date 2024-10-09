package me.entity303.plugmanbungee.api.event;

import net.md_5.bungee.api.plugin.Cancellable;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class PreUnloadPluginEvent extends Event implements Cancellable {
    private @NotNull final Plugin plugin;
    private @NotNull final String pluginName;

    private String cancelledReason;
    private boolean isCancelled;

    public PreUnloadPluginEvent(@NotNull Plugin plugin, @NotNull String pluginName) {
        this.plugin = plugin;
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

    public void setCancelledReason(@NotNull String reason) {
        this.cancelledReason = reason;
    }

    public @NotNull String getCancelledReason() {
        if (this.cancelledReason == null) {
            // default message
            return "§cPlugin §4"+pluginName+"§c unload has been canceled.";
        }
        return cancelledReason;
    }

    public @NotNull Plugin getPlugin() {
        return plugin;
    }

    public @NotNull String getPluginName() {
        return pluginName;
    }
}
