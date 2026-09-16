package velocity.com.rylinaux.plugman.commands;

import core.com.rylinaux.plugman.platform.PlatformCommandAdapter;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;
import velocity.com.rylinaux.plugman.pluginmanager.VelocityPluginManager;

import java.util.List;
import java.util.Set;

public final class VelocityCommandAdapter implements PlatformCommandAdapter {
    private static final String VELOCITY_PREFIX = "velocity-";
    private static final Set<String> REPLACED_HELP_SECTIONS = Set.of("disable", "reload", "restart", "unload");

    @Override
    public Plugin resolveEnableTarget(PluginManager pluginManager, String[] args, int start) {
        var loaded = pluginManager.getPluginByName(args, start);
        if (loaded != null || !(pluginManager instanceof VelocityPluginManager velocityManager)) return loaded;
        return velocityManager.getUnloadedPluginByName(args, start);
    }

    @Override
    public List<PluginGroup> groupPlugins(PluginManager pluginManager) {
        return List.of(new PluginGroup("list.velocity", pluginManager.getPlugins().stream().toList()));
    }

    @Override
    public boolean includeHelpSection(String section) {
        return !REPLACED_HELP_SECTIONS.contains(section);
    }

    @Override
    public String helpPermissionSection(String section) {
        if (!section.startsWith(VELOCITY_PREFIX)) return section;
        var command = section.substring(VELOCITY_PREFIX.length());
        return REPLACED_HELP_SECTIONS.contains(command) ? command : "help";
    }
}
