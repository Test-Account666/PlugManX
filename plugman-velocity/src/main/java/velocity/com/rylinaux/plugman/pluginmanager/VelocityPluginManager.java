package velocity.com.rylinaux.plugman.pluginmanager;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.command.VelocityCommandManager;
import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Command;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import lombok.SneakyThrows;
import velocity.com.rylinaux.plugman.PlugManVelocity;
import velocity.com.rylinaux.plugman.plugin.VelocityCommand;
import velocity.com.rylinaux.plugman.plugin.VelocityPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Velocity implementation of PluginManager.
 * Manages plugins on Velocity proxy servers.
 *
 * @author rylinaux
 */
public class VelocityPluginManager implements PluginManager {
    //TODO: Actually implement this
    //TODO: Use `id` instead of name. Name isn't guaranteed to be available

    private ProxyServer getServer() {
        return PlugManVelocity.getInstance().getServer();
    }

    private com.velocitypowered.proxy.plugin.VelocityPluginManager getPluginManager() {
        return (com.velocitypowered.proxy.plugin.VelocityPluginManager) getServer().getPluginManager();
    }

    private VelocityCommandManager getCommandManager() {
        return (VelocityCommandManager) getServer().getCommandManager();
    }

    @Override
    public PluginResult enable(Plugin plugin) {
        return new PluginResult(false, "Not implemented");
    }

    @Override
    public PluginResult enableAll() {
        return new PluginResult(false, "Not implemented");
    }

    @Override
    public PluginResult disable(Plugin plugin) {
        return new PluginResult(false, "Not implemented");
    }

    @Override
    public PluginResult disableAll() {
        return new PluginResult(false, "Not implemented");
    }

    @Override
    public String getFormattedName(Plugin plugin) {
        return getFormattedName(plugin, false);
    }

    @Override
    public String getFormattedName(Plugin plugin, boolean includeVersions) {
        if (includeVersions) return plugin.getName() + " (" + plugin.getVersion() + ")";
        return plugin.getName();
    }

    @Override
    public Plugin getPluginByName(String[] args, int start) {
        if (args.length <= start) return null;
        return getPluginByName(String.join(" ", Arrays.copyOfRange(args, start, args.length)));
    }

    @Override
    public Plugin getPluginByName(String name) {
        return getServer().getPluginManager().getPlugin(name)
                .map(pluginContainer -> new VelocityPlugin(pluginContainer, pluginContainer.getInstance()))
                .orElse(null);
    }

    @Override
    public List<String> getPluginNames(boolean fullName) {
        return getServer().getPluginManager().getPlugins().stream()
                .map(container -> fullName? getFormattedName(new VelocityPlugin(container, container.getInstance()), true)
                        : container.getDescription().getId())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getDisabledPluginNames(boolean fullName) {
        // Velocity doesn't have disabled plugins concept
        return Collections.emptyList();
    }

    @Override
    public List<String> getEnabledPluginNames(boolean fullName) {
        return getPluginNames(fullName);
    }

    @Override
    public String getPluginVersion(String name) {
        return getServer().getPluginManager().getPlugin(name)
                .map(container -> container.getDescription().getVersion().orElse("Unknown"))
                .orElse("Unknown");
    }

    @Override
    public String getUsages(Plugin plugin) {
        var list = new ArrayList<String>();

        for (var alias : getServer().getCommandManager().getAliases()) {
            var name = plugin.getName();

            if (!findByCommand(alias).contains(name)) continue;
            list.add(alias);
        }

        return String.join(", ", list);
    }

    @Override
    public List<String> findByCommand(String command) {
        var list = new ArrayList<String>();

        try {
            var commandMeta = getServer().getCommandManager().getCommandMeta(command);
            if (commandMeta != null) {
                var pluginContainer = commandMeta.getPlugin();
                if (pluginContainer instanceof PluginContainer container) {
                    var pluginName = container.getDescription().getName();
                    pluginName.ifPresent(list::add);
                }
            }

            // Also check for namespaced commands (plugin:command)
            for (var alias : getServer().getCommandManager().getAliases())
                if (alias.contains(":")) {
                    var parts = alias.split(":");
                    if (parts.length == 2 && parts[1].equalsIgnoreCase(command)) if (!list.contains(parts[0])) list.add(parts[0]);
                }
        } catch (Exception exception) {
            // Log error but don't fail completely
            PlugManVelocity.getInstance().getLogger().warn("Error finding command: {}", command, exception);
        }

        return list;
    }

    @Override
    public boolean isIgnored(Plugin plugin) {
        var configManager = PlugManVelocity.getInstance().get(PlugManConfigurationManager.class);
        return configManager != null && configManager.getIgnoredPlugins().contains(plugin.getName());
    }

    @Override
    public boolean isIgnored(String plugin) {
        var configManager = PlugManVelocity.getInstance().get(PlugManConfigurationManager.class);
        return configManager != null && configManager.getIgnoredPlugins().contains(plugin);
    }

    @Override
    public PluginResult load(String name) {
        return new PluginResult(false, "Not implemented");
    }

    @Override
    public PluginResult load(Plugin plugin) {
        return new PluginResult(false, "Not implemented");
    }

    @SneakyThrows
    @Override
    public Map<String, Command> getKnownCommands() {
        var commands = new HashMap<String, Command>();

        var commandMetas = FieldAccessor.<Map<String, com.velocitypowered.api.command.CommandMeta>>getValue("commandMetas", getCommandManager());

        for (var entry : commandMetas.entrySet()) {
            var alias = entry.getKey();

            commands.put(alias, new VelocityCommand(entry.getValue()));
        }
        return commands;
    }

    @Override
    public void setKnownCommands(Map<String, Command> knownCommands) {
        // Not implemented
    }

    @Override
    public PluginResult unload(Plugin plugin) {
        return new PluginResult(false, "Not implemented");
    }

    @Override
    public boolean isPaperPlugin(Plugin plugin) {
        return false; // Velocity plugins are not Paper plugins
    }

    @Override
    public Set<Plugin> getPlugins() {
        return getServer().getPluginManager().getPlugins().stream()
                .map(pluginContainer -> new VelocityPlugin(pluginContainer, pluginContainer.getInstance()))
                .collect(Collectors.toSet());
    }

    @SneakyThrows
    public File findPluginFile(String name) {
        var plugin = getServer().getPluginManager().getPlugins().stream()
                .filter(pluginContainer -> pluginContainer.getDescription().getName().get().equalsIgnoreCase(name))
                .findFirst();
        if (plugin.isEmpty()) return null;

        var pluginClass = plugin.get().getInstance().getClass();
        var pluginFile = pluginClass.getProtectionDomain().getCodeSource().getLocation();

        return Path.of(pluginFile.toURI()).toFile();
    }

    public PluginResult loadPluginFromFile(File file) {
        return new PluginResult(false, "Not implemented");
    }
}