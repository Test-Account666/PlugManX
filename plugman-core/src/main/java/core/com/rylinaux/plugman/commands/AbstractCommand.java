package core.com.rylinaux.plugman.commands;

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


import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;


/**
 * Abstract command class that our commands extend.
 *
 * @author rylinaux
 */
@Getter
@AllArgsConstructor
public abstract class AbstractCommand {

    /**
     * The command's sender.
     */
    private final CommandSender sender;

    /**
     * The command's name.
     */
    private final String name;

    /**
     * The command's description.
     */
    private final String description;

    /**
     * The command's permission.
     */
    private final String permission;

    /**
     * The sub permissions.
     */
    private final String[] subPermissions;

    /**
     * The command's usage.
     */
    private final String usage;

    /**
     * The service registry for accessing core services.
     */
    @Delegate
    private final ServiceRegistry serviceRegistry;

    /**
     * Checks whether the sender has permission to do the command.
     *
     * @return does the sender have permission
     */
    public boolean hasPermission() {
        return sender.hasPermission(permission) || isConsoleOrRemoteConsole();
    }

    /**
     * Checks whether the sender has permission to do the command.
     *
     * @param sub the sub permission to check
     * @return does the sender have permission
     */
    public boolean hasPermission(String sub) {
        var fullPermission = permission + "." + sub;
        return sender.hasPermission(fullPermission) || isConsoleOrRemoteConsole();
    }

    /**
     * Checks if the sender is console or remote console.
     *
     * @return true if sender is console or remote console
     */
    private boolean isConsoleOrRemoteConsole() {
        return sender.isConsole();
    }

    /**
     * Sends the usage message to the sender.
     */
    public void sendUsage(String label) {
        sender.sendMessage(false, "error.usage.command", name);
        sender.sendMessage(false, "error.usage.description", description);
        sender.sendMessage(false, "error.usage.usage", label, usage);
    }

    /**
     * Sends a no permission message to the sender.
     */
    public void sendNoPermissionMessage() {
        sender.sendMessage("error.no-permission");
    }

    /**
     * Sends a specify plugin message to the sender.
     */
    protected void sendSpecifyPluginMessage() {
        sender.sendMessage("error.specify-plugin");
    }

    /**
     * Sends an invalid plugin message to the sender.
     */
    protected void sendInvalidPluginMessage() {
        sender.sendMessage("error.invalid-plugin");
    }

    /**
     * Validates if the command has sufficient arguments.
     *
     * @param args    the command arguments
     * @param minArgs the minimum number of arguments required
     * @return true if valid, false otherwise
     */
    protected boolean validateArguments(String label, String[] args, int minArgs) {
        if (args.length < minArgs) {
            sendSpecifyPluginMessage();
            sendUsage(label);
            return false;
        }
        return true;
    }

    /**
     * Validates a plugin for common checks (exists, not ignored, not paper plugin).
     *
     * @param plugin the plugin to validate
     * @return true if valid, false otherwise
     */
    protected boolean validatePlugin(String label, Plugin plugin) {
        if (plugin == null) {
            sendInvalidPluginMessage();
            sendUsage(label);
            return false;
        }

        if (getPluginManager().isIgnored(plugin)) {
            sender.sendMessage("error.ignored");
            return false;
        }

        if (getPluginManager().isPaperPlugin(plugin)) {
            sender.sendMessage("error.paper-plugin");
            return false;
        }

        return true;
    }

    /**
     * Handles the "all" argument for commands that support it.
     *
     * @param args           the command arguments
     * @param subPermission  the sub-permission required for "all"
     * @param allAction      the action to execute for "all"
     * @param successMessage the success message key
     * @return true if "all" was handled, false otherwise
     */
    protected boolean handleAllArgument(String[] args, String subPermission, Runnable allAction, String successMessage) {
        if (args.length > 1 && (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("*"))) {
            if (!hasPermission(subPermission)) {
                sendNoPermissionMessage();
                return true;
            }

            allAction.run();
            sender.sendMessage(successMessage);
            return true;
        }
        return false;
    }

    /**
     * Executes the command.
     *
     * @param sender the sender of the command
     * @param label  the name of the command
     * @param args   the arguments supplied
     */
    public abstract void execute(CommandSender sender, String label, String[] args);

}
