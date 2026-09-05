package bungee.com.rylinaux.plugman.commands;

import core.com.rylinaux.plugman.platform.PlatformCommandAdapter;
import core.com.rylinaux.plugman.plugins.PluginManager;

import java.util.List;

/**
 * Bungee-specific command presentation.
 */
public final class BungeeCommandAdapter implements PlatformCommandAdapter {

    @Override
    public List<PluginGroup> groupPlugins(PluginManager pluginManager) {
        return List.of(new PluginGroup("list.bungee", pluginManager.getPlugins().stream().toList()));
    }
}
