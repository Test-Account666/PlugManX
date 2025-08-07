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
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.services.ServiceRegistry;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

/**
 * Command that dumps plugin names and versions to file.
 *
 * @author rylinaux
 */
public class DumpCommand extends AbstractCommand {
    /**
     * The name of the command.
     */
    public static final String NAME = "Dump";

    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "Dump plugins and versions to file.";

    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.dump";

    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman dump";

    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {""};

    /**
     * Construct out object.
     *
     * @param sender the command sender
     */
    public DumpCommand(CommandSender sender, ServiceRegistry registry) {
        super(sender, NAME, DESCRIPTION, PERMISSION, SUB_PERMISSIONS, USAGE, registry);
    }

    /**
     * Executes the command.
     *
     * @param sender the sender of the command
     * @param label  the name of the command
     * @param args   the arguments supplied
     */
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        var dumpFile = new File(Path.of("plugins", "PlugManX").toFile(), "versions.txt");

        var plugins = getPluginManager().getPluginNames(true);
        plugins.sort(String.CASE_INSENSITIVE_ORDER);

        try (var writer = new PrintWriter(dumpFile)) {
            for (var plugin : plugins) {
                plugin = reformat(plugin);

                writer.println(plugin);
            }
            writer.flush();

            sender.sendMessage("dump.dumped", dumpFile.getName());
        } catch (IOException exception) {
            var logger = get(PluginLogger.class);
            sender.sendMessage("dump.error");
            logger.severe("Failed to write dump file: " + dumpFile.getPath(), exception);
        }
    }

    private String reformat(String plugin) {
        if (plugin.startsWith("§a")) plugin = plugin + " - Enabled";

        if (plugin.startsWith("§c")) plugin = plugin + " - Disabled";

        plugin = plugin.substring(2);

        return plugin;
    }
}
