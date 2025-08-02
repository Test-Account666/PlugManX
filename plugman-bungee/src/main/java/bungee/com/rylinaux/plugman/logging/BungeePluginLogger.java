package bungee.com.rylinaux.plugman.logging;

import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Bungee implementation of PluginLogger.
 * Bridges the core logging interface with Bungee's logging system.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class BungeePluginLogger implements PluginLogger {

    private final Plugin plugin;

    @Override
    public void info(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void warning(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void severe(String message) {
        plugin.getLogger().severe(message);
    }
}
