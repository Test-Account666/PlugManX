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
import core.com.rylinaux.plugman.services.ServiceRegistry;

/**
 * Command that restarts plugin(s).
 *
 * @author rylinaux
 */
public class RestartCommand extends AbstractCommand {

    /**
     * The name of the command.
     */
    public static final String NAME = "Restart";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "Restart a plugin.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.restart";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman restart <plugin|all>";

    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {"all"};

    /**
     * Construct out object.
     *
     * @param sender the command sender
     */
    public RestartCommand(CommandSender sender, ServiceRegistry registry) {
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

        if (handleAllArgument(args, "all", () -> {
            getPluginManager().disableAll();
            getPluginManager().enableAll();
        }, "restart.all")) return;

        var target = getPluginManager().getPluginByName(args, 1);

        if (!validatePlugin(label, target)) return;

        var result = getPluginManager().disable(target);
        if (!result.success()) {
            sender.sendMessage(result.messageId(), target.getName());
            return;
        }

        result = getPluginManager().enable(target);
        if (!result.success()) {
            sender.sendMessage(result.messageId(), target.getName());
            return;
        }

        sender.sendMessage("restart.restarted", target.getName());
    }
}
