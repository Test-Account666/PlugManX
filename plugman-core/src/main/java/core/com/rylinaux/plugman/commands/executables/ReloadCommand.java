package core.com.rylinaux.plugman.commands.executables;

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

import core.com.rylinaux.plugman.commands.AbstractCommand;
import core.com.rylinaux.plugman.commands.CommandSender;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.services.ServiceRegistry;

import java.util.ArrayList;

/**
 * Command that reloads plugin(s).
 *
 * @author rylinaux
 */
public class ReloadCommand extends AbstractCommand {

    /**
     * The name of the command.
     */
    public static final String NAME = "Reload";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "Reload a plugin.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.reload";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman reload <plugin|all>";

    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {"all"};

    /**
     * Construct out object.
     *
     * @param sender the command sender
     */
    public ReloadCommand(CommandSender sender, ServiceRegistry registry) {
        super(sender, NAME, DESCRIPTION, PERMISSION, SUB_PERMISSIONS, USAGE, registry);
    }

    /**
     * Execute the command.
     *
     * @param sender the sender of the command
     * @param label  the name of the command
     * @param args   the arguments supplied
     */
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!validateArguments(label, args, 2)) return;

        if (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("*")) {
            if (!hasPermission("all")) {
                sendNoPermissionMessage();
                return;
            }
            reloadAllPlugins(sender, label);
            return;
        }

        var target = getPluginManager().getPluginByName(args, 1);

        if (!validatePlugin(label, target)) return;
        reloadPlugin(sender, label, target);
    }

    private void reloadAllPlugins(CommandSender sender, String label) {
        var plugins = getPluginManager().getPlugins().stream().filter(plugin ->
                plugin != null && !getPluginManager().isIgnored(plugin) && !getPluginManager().isPaperPlugin(plugin)).toList();

        var failedPlugins = new ArrayList<String>();

        for (var plugin : plugins) {
            var success = reloadPlugin(sender, label, plugin);

            if (success) continue;
            failedPlugins.add(plugin.getName());
        }

        if (failedPlugins.isEmpty()) {
            sender.sendMessage("reload.all");
            return;
        }

        sender.sendMessage("reload.all-failed", String.join(", ", failedPlugins));
    }

    private boolean reloadPlugin(CommandSender sender, String label, Plugin target) {
        if (target == null) {
            sendInvalidPluginMessage();
            sendUsage(label);
            return false;
        }

        var result = getPluginManager().unload(target);
        if (!result.success()) {
            sender.sendMessage(result.messageId(), target.getName());
            return false;
        }

        result = getPluginManager().load(target);
        if (!result.success()) {
            sender.sendMessage(result.messageId(), target.getName());
            return false;
        }

        sender.sendMessage("reload.reloaded", target.getName());
        return true;
    }

}
