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
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.services.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Command that displays dependency relationships for a plugin.
 */
public class DepsCommand extends AbstractCommand {

    /**
     * The name of the command.
     */
    public static final String NAME = "Deps";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "View dependency relationships for a plugin.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.deps";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman deps <plugin>";

    /**
     * The sub permissions of the command.
     */
    protected static final String[] SUB_PERMISSIONS = {""};

    /**
     * Construct out object.
     *
     * @param sender   the command sender
     * @param registry the service registry
     */
    public DepsCommand(CommandSender sender, ServiceRegistry registry) {
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

        sender.sendMessage("deps.header", target.getName());
        sender.sendMessage(false, "deps.depends", formatList(target.getDepend()));
        sender.sendMessage(false, "deps.softdepends", formatList(target.getSoftDepend()));
        sender.sendMessage(false, "deps.used-by", formatList(findDependents(target)));
    }

    private List<String> findDependents(Plugin target) {
        var dependents = new ArrayList<String>();
        var targetName = target.getName();

        for (var plugin : getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(targetName)) continue;
            if (!referencesPlugin(plugin, targetName)) continue;

            dependents.add(plugin.getName());
        }

        return dependents;
    }

    private boolean referencesPlugin(Plugin plugin, String targetName) {
        return containsIgnoreCase(plugin.getDepend(), targetName) ||
                containsIgnoreCase(plugin.getSoftDepend(), targetName);
    }

    private boolean containsIgnoreCase(List<String> values, String targetName) {
        for (var value : values) {
            if (value.equalsIgnoreCase(targetName)) return true;
        }

        return false;
    }

    private String formatList(List<String> values) {
        if (values.isEmpty()) return "-";

        return Joiner.on(", ").join(values);
    }
}
