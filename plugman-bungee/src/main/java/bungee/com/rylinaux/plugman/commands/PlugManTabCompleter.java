package bungee.com.rylinaux.plugman.commands;

import bungee.com.rylinaux.plugman.PlugManBungee;
import core.com.rylinaux.plugman.util.StringUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Completes partial matches in command and plugin names.
 *
 * @author rylinaux
 */
public class PlugManTabCompleter implements TabExecutor {

    /**
     * Valid command names.
     */
    private static final String[] COMMANDS = {"check", "disable", "dump", "enable", "help", "info", "list", "load", "lookup", "reload", "restart", "unload", "usage"};

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (!hasPermission(sender, args)) return null;

        var completions = new ArrayList<String>();

        if (args.length == 0) completions.addAll(Arrays.asList(COMMANDS));
        if (args.length == 1) completeCommands(args[0], completions);
        else if (args.length == 2) completeSecondArgument(args, completions);

        Collections.sort(completions);
        return completions;
    }

    private boolean hasPermission(CommandSender sender, String[] args) {
        return sender.hasPermission("plugman.admin") || (args.length > 0 && sender.hasPermission("plugman." + args[0]));
    }

    private void completeCommands(String partialCommand, List<String> completions) {
        var commands = new ArrayList<>(Arrays.asList(COMMANDS));
        StringUtil.copyPartialMatches(partialCommand, commands, completions);
    }

    private void completeSecondArgument(String[] args, List<String> completions) {
        var command = args[0].toLowerCase();
        var partialArg = args[1];

        switch (command) {
            case "load", "enable" -> completeLoadablePlugins(partialArg, completions);
            case "lookup" -> completeKnownCommands(partialArg, completions);
            default -> completeAllPlugins(partialArg, completions);
        }
    }

    private void completeLoadablePlugins(String partialPlugin, List<String> completions) {
        var files = new ArrayList<String>();
        var pluginsDir = new File("plugins");

        if (!pluginsDir.exists() || pluginsDir.listFiles() == null) return;

        for (var pluginFile : pluginsDir.listFiles()) {
            var fileName = extractLoadablePluginName(pluginFile);
            if (fileName != null && !isPluginAlreadyLoaded(pluginFile)) files.add(fileName);
        }

        StringUtil.copyPartialMatches(partialPlugin, files, completions);
    }

    private String extractLoadablePluginName(File pluginFile) {
        if (pluginFile.isDirectory()) return null;

        if (!pluginFile.getName().toLowerCase().endsWith(".jar")) if (!new File("plugins", pluginFile.getName() + ".jar").exists()) return null;

        try (var jarFile = new JarFile(pluginFile)) {
            if (jarFile.getEntry("bungee.yml") == null) return null;

            try (var stream = jarFile.getInputStream(jarFile.getEntry("bungee.yml"))) {
                if (stream == null) return null;

                return pluginFile.getName().substring(0, pluginFile.getName().length() - ".jar".length());
            }
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean isPluginAlreadyLoaded(File pluginFile) {
        try (var jarFile = new JarFile(pluginFile)) {
            if (jarFile.getEntry("plugin.yml") == null) return false;

            try (var stream = jarFile.getInputStream(jarFile.getEntry("bungee.yml"))) {
                if (stream == null) return false;

                var yaml = new Yaml();
                var desc = yaml.loadAs(stream, PluginDescription.class);

                for (var plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
                    if (!plugin.getDescription().getName().equalsIgnoreCase(desc.getName())) continue;
                    return true;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    private void completeKnownCommands(String partialCommand, List<String> completions) {
        var commands = PlugManBungee.getInstance().getPluginManager().getKnownCommands().keySet()
                .stream()
                .filter(s -> !s.toLowerCase().contains(":"))
                .collect(Collectors.toList());
        commands.remove("/");
        StringUtil.copyPartialMatches(partialCommand, commands, completions);
    }

    private void completeAllPlugins(String partialPlugin, List<String> completions) {
        var plugins = PlugManBungee.getInstance().getPluginManager().getPluginNames(false);
        StringUtil.copyPartialMatches(partialPlugin, plugins, completions);
    }
}
