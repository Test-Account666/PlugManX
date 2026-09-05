package core.com.rylinaux.plugman.platform;

import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;

import java.util.List;

/**
 * Optional platform extension points for command behavior that cannot be
 * represented by the shared Bukkit-oriented defaults.
 */
public interface PlatformCommandAdapter {

    default Plugin resolveEnableTarget(PluginManager pluginManager, String[] args, int start) {
        return pluginManager.getPluginByName(args, start);
    }

    default List<PluginGroup> groupPlugins(PluginManager pluginManager) {
        var paper = pluginManager.getPlugins().stream().filter(pluginManager::isPaperPlugin).toList();
        var bukkit = pluginManager.getPlugins().stream().filter(plugin -> !pluginManager.isPaperPlugin(plugin)).toList();
        return List.of(new PluginGroup("list.paper", paper), new PluginGroup("list.bukkit", bukkit));
    }

    default boolean includeHelpSection(String section) {
        return true;
    }

    default String helpPermissionSection(String section) {
        return section;
    }

    record PluginGroup(String messageKey, List<Plugin> plugins) {
        public PluginGroup {
            plugins = List.copyOf(plugins);
        }
    }
}
