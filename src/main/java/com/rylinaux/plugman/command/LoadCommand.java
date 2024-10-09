package com.rylinaux.plugman.command;

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

import com.rylinaux.plugman.PlugMan;
import com.rylinaux.plugman.util.StringUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Command that loads plugin(s).
 *
 * @author rylinaux
 */
public class LoadCommand extends AbstractCommand {

    /**
     * The name of the command.
     */
    public static final String NAME = "Load";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "Load a plugin.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.load";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman load <plugin>";

    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {""};

    /**
     * Construct out object.
     *
     * @param sender the command sender
     */
    public LoadCommand(CommandSender sender) {
        super(sender, LoadCommand.NAME, LoadCommand.DESCRIPTION, LoadCommand.PERMISSION, LoadCommand.SUB_PERMISSIONS, LoadCommand.USAGE);
    }

    /**
     * Execute the command.
     *
     * @param sender  the sender of the command
     * @param command the command being done
     * @param label   the name of the command
     * @param args    the arguments supplied
     */
    @Override
    public void execute(CommandSender sender, Command command, String label, String[] args) {
        if (!this.hasPermission()) {
            sender.sendMessage(PlugMan.getInstance().getMessageFormatter().format("error.no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PlugMan.getInstance().getMessageFormatter().format("error.specify-plugin"));
            this.sendUsage();
            return;
        }

        for (int i = 1; i < args.length; i++)
            args[i] = args[i].replaceAll("[/\\\\]", "");

        Plugin potential = PlugMan.getInstance().getPluginUtil().getPluginByName(args, 1);

        if (potential != null) {
            sender.sendMessage(PlugMan.getInstance().getMessageFormatter().format("load.already-loaded", potential.getName()));
            return;
        }

        String name = StringUtil.consolidateStrings(args, 1);

        if (PlugMan.getInstance().getPluginUtil().isIgnored(name)) {
            sender.sendMessage(PlugMan.getInstance().getMessageFormatter().format("error.ignored"));
            return;
        }

        sender.sendMessage(PlugMan.getInstance().getPluginUtil().load(name));
    }
}
