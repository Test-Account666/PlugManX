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

import com.google.common.base.Joiner;
import core.com.rylinaux.plugman.commands.AbstractCommand;
import core.com.rylinaux.plugman.commands.CommandSender;
import core.com.rylinaux.plugman.services.ServiceRegistry;

/**
 * Command that displays information on a plugin.
 *
 * @author rylinaux
 */
public class InfoCommand extends AbstractCommand {

    /**
     * The name of the command.
     */
    public static final String NAME = "Info";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "View information on a plugin.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.info";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman info <plugin>";

    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {""};

    /**
     * Construct out object.
     *
     * @param sender   the command sender
     * @param registry the service registry
     */
    public InfoCommand(CommandSender sender, ServiceRegistry registry) {
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

        var target = getPluginManager().getPluginByName(args, 1);

        if (target == null) {
            sendInvalidPluginMessage();
            sendUsage(label);
            return;
        }

        var name = target.getName();
        var version = target.getVersion();
        var authors = Joiner.on(", ").join(target.getAuthors());
        var status = target.isEnabled()? "Enabled" : "Disabled";
        var dependList = target.getDepend();
        var softdependList = target.getSoftDepend();

        sender.sendMessage("info.header", name);
        sender.sendMessage(false, "info.version", version);
        sender.sendMessage(false, "info.authors", authors);
        sender.sendMessage(false, "info.status", status);

        if (!dependList.isEmpty())
            sender.sendMessage(false, "info.depends",
                    Joiner.on(", ").join(dependList));

        if (!softdependList.isEmpty())
            sender.sendMessage(false, "info.softdepends",
                    Joiner.on(", ").join(softdependList));

    }
}
