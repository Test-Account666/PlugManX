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
import core.com.rylinaux.plugman.util.FlagUtil;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * Command that lists plugins.
 *
 * @author rylinaux
 */
public class ListCommand extends AbstractCommand {

    /**
     * The name of the command.
     */
    public static final String NAME = "List";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "List all plugins.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.list";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman list [-v]";

    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {""};

    /**
     * Construct out object.
     *
     * @param sender the command sender
     */
    public ListCommand(CommandSender sender, ServiceRegistry registry) {
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
        var includeVersions = FlagUtil.parse(args, 'v').hasFlag('v');

        var formatFunction = new PluginStringFunction(includeVersions);
        var paperPlugins = new ArrayList<String>();
        var bukkitPlugins = new ArrayList<String>();

        getPluginManager().getPlugins().forEach(plugin -> {
            var pluginList = getPluginManager().isPaperPlugin(plugin) ? paperPlugins : bukkitPlugins;
            pluginList.add(formatFunction.apply(plugin));
        });

        paperPlugins.sort(String.CASE_INSENSITIVE_ORDER);
        bukkitPlugins.sort(String.CASE_INSENSITIVE_ORDER);

        sender.sendMessage("list.paper", paperPlugins.size(), formatPluginList(paperPlugins));
        sender.sendMessage("list.bukkit", bukkitPlugins.size(), formatPluginList(bukkitPlugins));

    }

    private String formatPluginList(ArrayList<String> plugins) {
        return plugins.isEmpty() ? "&7None" : Joiner.on(", ").join(plugins);
    }

    @RequiredArgsConstructor
    private class PluginStringFunction implements Function<Plugin, String> {
        private final boolean includeVersions;

        @Override
        public String apply(Plugin plugin) {
            return getPluginManager().getFormattedName(plugin, includeVersions);
        }
    }
}
