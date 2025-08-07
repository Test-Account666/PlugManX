package core.com.rylinaux.plugman.commands.executables;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2015 PlugMan
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
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.pojo.UpdateResult;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.FlagUtil;
import core.com.rylinaux.plugman.util.StringUtil;
import core.com.rylinaux.plugman.util.ThreadUtil;
import core.com.rylinaux.plugman.util.updatechecker.UpdateUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

/**
 * Command that checks if a plugin is up-to-date.
 *
 * @author rylinaux
 */
public class CheckCommand extends AbstractCommand {
    /**
     * The name of the command.
     */
    public static final String NAME = "Check";
    /**
     * The description of the command.
     */
    public static final String DESCRIPTION = "Check if a plugin is up-to-date.";
    /**
     * The main permission of the command.
     */
    public static final String PERMISSION = "plugman.check";
    /**
     * The proper usage of the command.
     */
    public static final String USAGE = "/plugman check <plugin>";
    /**
     * The sub permissions of the command.
     */
    public static final String[] SUB_PERMISSIONS = {"all"};

    /**
     * Construct out object.
     *
     * @param sender the command sender
     */
    public CheckCommand(CommandSender sender, ServiceRegistry registry) {
        super(sender, NAME, DESCRIPTION, PERMISSION, SUB_PERMISSIONS, USAGE, registry);
    }

    /**
     * Execute the command (platform-specific)
     *
     * @param sender the sender of the command
     * @param label  the name of the command
     * @param args   the arguments supplied
     */
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!validateArguments(label, args, 2)) return;

        var toFile = FlagUtil.hasFlag(args, 'f');

        if (args[1] == null) {
            sendSpecifyPluginMessage();
            sendUsage(label);
            return;
        }

        if (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("*")) {
            if (!hasPermission("all")) {
                sendNoPermissionMessage();
                return;
            }

            sender.sendMessage("check.header");

            var threadUtil = get(ThreadUtil.class);

            threadUtil.async(() -> {
                var results = UpdateUtil.checkUpToDate(getPluginManager(), get(PlugManConfigurationManager.class).getResourceMappingsConfig());

                var upToDate = new StringBuilder();
                var outOfDate = new StringBuilder();
                var unknown = new StringBuilder();

                for (var entry : results.entrySet()) {

                    var result = entry.getValue().type();

                    var currentVersion = getPluginManager().getPluginByName(entry.getKey()).getVersion();

                    if (result == UpdateResult.ResultType.UP_TO_DATE)
                        upToDate.append(entry.getKey()).append("(").append(currentVersion).append(") ");
                    else if (result == UpdateResult.ResultType.INVALID_PLUGIN || result == UpdateResult.ResultType.NOT_INSTALLED)
                        unknown.append(entry.getKey()).append("(").append(currentVersion).append(") ");
                    else outOfDate.append(entry.getKey())
                                .append("(")
                                .append(currentVersion)
                                .append(" -> ")
                                .append(entry.getValue().latestVersion())
                                .append(") ");

                }

                if (!toFile) {
                    threadUtil.sync(() -> {
                        sender.sendMessage("check.up-to-date-player", upToDate.toString());
                        sender.sendMessage("check.out-of-date-player", outOfDate.toString());
                        sender.sendMessage("check.unknown-player", unknown.toString());
                    });
                    return;
                }

                var outFile = new File(Path.of("plugins", "PlugManX").toFile(), "updates.txt");

                try (var writer = new PrintWriter(outFile)) {
                    writer.println("Up-to-date (Installed):");
                    writer.println(upToDate);

                    writer.println("Out-of-date (Installed -> Latest):");
                    writer.println(outOfDate);

                    writer.println("Unknown (Installed):");
                    writer.println(unknown);
                } catch (IOException exception) {
                    var logger = get(PluginLogger.class);
                    logger.warning("Error writing to file: " + exception.getMessage());
                    return;
                }

                sender.sendMessage("check.file-done", outFile.getPath());
            });

            return;
        }

        var pluginName = StringUtil.consolidateStrings(args, 1).replaceAll(" ", "+").replace("-[a-zA-Z]", "").replace("+null", "");

        sender.sendMessage(("check.header"));

        var threadUtil = get(ThreadUtil.class);

        threadUtil.async(() -> {
            var result = UpdateUtil.checkUpToDate(pluginName, getPluginManager(), get(PlugManConfigurationManager.class).getResourceMappingsConfig());

            threadUtil.sync(() -> {
                switch (result.type()) {
                    case NOT_INSTALLED -> sender.sendMessage("check.not-found", result.latestVersion());
                    case OUT_OF_DATE -> sender.sendMessage("check.out-of-date", result.currentVersion(), result.latestVersion());
                    case UP_TO_DATE -> sender.sendMessage("check.up-to-date", result.currentVersion());
                    default -> sender.sendMessage("check.not-found-spigot");
                }
            });
        });
    }
}