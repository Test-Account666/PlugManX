package bukkit.com.rylinaux.plugman.commands;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.StringUtil;

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
public class PlugManTabCompleter implements TabCompleter {

    /**
     * Valid command names.
     */
    private static final String[] COMMANDS = {"check", "disable", "dump", "enable", "help", "info", "list", "load", "lookup", "reload", "restart", "unload", "usage"};

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender, args)) return null;

        var completions = new ArrayList<String>();

        if (args.length == 0) completions.addAll(Arrays.asList(COMMANDS));
        if (args.length == 1) completeCommands(args[0], completions);
        else if (args.length == 2) completeSecondArgument(args, completions);

        Collections.sort(completions);
        return completions;
    }

    private boolean hasPermission(CommandSender sender, String[] args) {
        return sender.isOp() || sender.hasPermission("plugman.admin") ||
                (args.length > 0 && sender.hasPermission("plugman." + args[0]));
    }

    private void completeCommands(String partialCommand, List<String> completions) {
        var commands = new ArrayList<>(Arrays.asList(COMMANDS));
        StringUtil.copyPartialMatches(partialCommand, commands, completions);
    }

    private void completeSecondArgument(String[] args, List<String> completions) {
        var command = args[0].toLowerCase();
        var partialArg = args[1];

        switch (command) {
            case "load" -> completeLoadablePlugins(partialArg, completions);
            case "lookup" -> completeKnownCommands(partialArg, completions);
            case "enable" -> completeDisabledPlugins(partialArg, completions);
            case "disable" -> completeEnabledPlugins(partialArg, completions);
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
            if (jarFile.getEntry("plugin.yml") == null) return null;

            try (var stream = jarFile.getInputStream(jarFile.getEntry("plugin.yml"))) {
                if (stream == null) return null;

                new PluginDescriptionFile(stream); // Validate the plugin.yml
                return pluginFile.getName().substring(0, pluginFile.getName().length() - ".jar".length());
            }
        } catch (IOException | InvalidDescriptionException exception) {
            return null;
        }
    }

    private boolean isPluginAlreadyLoaded(File pluginFile) {
        try (var jarFile = new JarFile(pluginFile)) {
            if (jarFile.getEntry("plugin.yml") == null) return false;

            try (var stream = jarFile.getInputStream(jarFile.getEntry("plugin.yml"))) {
                if (stream == null) return false;

                var descriptionFile = new PluginDescriptionFile(stream);
                for (var plugin : Bukkit.getPluginManager().getPlugins()) {
                    if (!plugin.getName().equalsIgnoreCase(descriptionFile.getName())) continue;
                    return true;
                }
            }
        } catch (IOException | InvalidDescriptionException exception) {
            return false;
        }
        return false;
    }

    private void completeKnownCommands(String partialCommand, List<String> completions) {
        var commands = PlugManBukkit.getInstance().getPluginManager().getKnownCommands().keySet()
                .stream()
                .filter(s -> !s.toLowerCase().contains(":"))
                .collect(Collectors.toList());
        commands.remove("/");
        StringUtil.copyPartialMatches(partialCommand, commands, completions);
    }

    private void completeDisabledPlugins(String partialPlugin, List<String> completions) {
        var plugins = PlugManBukkit.getInstance().getPluginManager().getDisabledPluginNames(false);
        StringUtil.copyPartialMatches(partialPlugin, plugins, completions);
    }

    private void completeEnabledPlugins(String partialPlugin, List<String> completions) {
        var plugins = PlugManBukkit.getInstance().getPluginManager().getEnabledPluginNames(false);
        StringUtil.copyPartialMatches(partialPlugin, plugins, completions);
    }

    private void completeAllPlugins(String partialPlugin, List<String> completions) {
        var plugins = PlugManBukkit.getInstance().getPluginManager().getPluginNames(false);
        StringUtil.copyPartialMatches(partialPlugin, plugins, completions);
    }
}
