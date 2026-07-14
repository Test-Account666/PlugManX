package velocity.com.rylinaux.plugman.commands;

/*
 * #%L
 * PlugManVelocity
 * %%
 * Copyright (C) 2010 - 2024 PlugMan
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

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import core.com.rylinaux.plugman.commands.executables.*;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import velocity.com.rylinaux.plugman.PlugManVelocity;
import velocity.com.rylinaux.plugman.config.VelocityPlugManConfigurationManager;
import velocity.com.rylinaux.plugman.logging.VelocityCrashDumpWriter;
import velocity.com.rylinaux.plugman.pluginmanager.VelocityPluginManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Velocity command handler for PlugMan commands.
 * Listens for commands and executes them using the core command system.
 *
 * @author rylinaux
 */
public class PlugManCommandHandler implements SimpleCommand {
    /**
     * Valid command names.
     */
    private static final String[] COMMANDS = {"check", "disable", "dump", "enable", "help", "info", "list", "load", "lookup", "reload", "restart", "unload", "usage"};

    @Override
    public void execute(Invocation invocation) {
        var sender = invocation.source();
        var args = invocation.arguments();

        if (!isVelocityConsole(sender)) {
            // Normally unreachable because hasPermission() hides the proxy command from players.
            // Stay silent so PlugManX never conflicts with a backend command of the same name.
            return;
        }

        logDevelopmentCommand(args);
        
        var commandName = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";

        if ("dev".equals(commandName) && executeDevelopmentCommand(sender, args)) return;

        var plugManSender = new VelocityCommandSender(sender);
        var registry = PlugManVelocity.getInstance().getServiceRegistry();

        var cmd = switch (commandName) {
            case "list" -> new ListCommand(plugManSender, registry);
            case "dump" -> new DumpCommand(plugManSender, registry);
            case "info" -> new InfoCommand(plugManSender, registry);
            case "lookup" -> new LookupCommand(plugManSender, registry);
            case "usage" -> new UsageCommand(plugManSender, registry);
            case "enable" -> new EnableCommand(plugManSender, registry);
            case "load" -> new LoadCommand(plugManSender, registry);
            case "disable" -> new DisableCommand(plugManSender, registry);
            case "unload" -> new UnloadCommand(plugManSender, registry);
            case "restart", "reload" -> new ReloadCommand(plugManSender, registry);
            case "check" -> new CheckCommand(plugManSender, registry);
            default -> new HelpCommand(plugManSender, registry);
        };

        if (!cmd.hasPermission()) {
            cmd.sendNoPermissionMessage();
            return;
        }

        cmd.execute(cmd.getSender(), "plugman", args);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!isVelocityConsole(invocation.source())) return List.of();
        var args = invocation.arguments();
        
        if (args.length <= 1) {
            if (areDevelopmentTestFunctionsEnabled() && invocation.source().hasPermission("plugman.dev")) {
                var commands = new java.util.ArrayList<>(Arrays.asList(COMMANDS));
                commands.add("dev");
                return commands;
            }
            return Arrays.asList(COMMANDS);
        }
        if (args.length == 2 && "dev".equalsIgnoreCase(args[0])
                && areDevelopmentTestFunctionsEnabled()
                && invocation.source().hasPermission("plugman.dev")) {
            return List.of("status", "crashdump");
        }
        
        // For now, return empty list for sub-command suggestions
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return isVelocityConsole(invocation.source());
    }

    private boolean executeDevelopmentCommand(CommandSource sender, String[] args) {
        if (!areDevelopmentTestFunctionsEnabled()) return false;
        if (!sender.hasPermission("plugman.dev")) {
            sender.sendMessage(Component.text("[PlugManX] You do not have permission to use development functions.",
                    NamedTextColor.RED));
            return true;
        }

        var action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if ("crashdump".equals(action)) {
            var result = VelocityCrashDumpWriter.write("Development crash dump test",
                    new IllegalStateException("User-requested Velocity development dump test"));
            if (result == null) {
                sender.sendMessage(Component.text("[PlugManX] Failed to write the test crash dump.",
                        NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text("[PlugManX] Test crash dump written with ID " + result.id(),
                        NamedTextColor.GREEN));
            }
            return true;
        }

        if (!"status".equals(action)) {
            sender.sendMessage(Component.text("[PlugManX] Usage: /plugman dev [status|crashdump]",
                    NamedTextColor.YELLOW));
            return true;
        }

        var plugin = PlugManVelocity.getInstance();
        var manager = plugin.get(core.com.rylinaux.plugman.plugins.PluginManager.class);
        var runtimeStatus = manager instanceof VelocityPluginManager velocityManager
                && velocityManager.isDevelopmentRuntimeAvailable() ? "available" : "unavailable";
        sender.sendMessage(Component.text("[PlugManX] Velocity development runtime: " + runtimeStatus,
                NamedTextColor.GREEN));
        sender.sendMessage(Component.text("[PlugManX] Proxy: " + plugin.getServer().getVersion().getVersion()
                + ", Java: " + System.getProperty("java.version"), NamedTextColor.GRAY));
        return true;
    }

    private static boolean areDevelopmentTestFunctionsEnabled() {
        var plugin = PlugManVelocity.getInstance();
        if (plugin == null) return false;
        var configurationManager = plugin.get(PlugManConfigurationManager.class);
        return configurationManager instanceof VelocityPlugManConfigurationManager velocityConfig
                && velocityConfig.areVelocityDevTestFunctionsEnabled();
    }

    private static boolean isVelocityConsole(CommandSource source) {
        var plugin = PlugManVelocity.getInstance();
        return plugin != null && source.equals(plugin.getServer().getConsoleCommandSource());
    }

    private static void logDevelopmentCommand(String[] args) {
        var plugin = PlugManVelocity.getInstance();
        var configurationManager = plugin.get(PlugManConfigurationManager.class);
        if (!(configurationManager instanceof VelocityPlugManConfigurationManager velocityConfig)
                || !velocityConfig.isVelocityDevModeEnabled()) return;

        var arguments = args.length == 0 ? "help" : String.join(" ", args);
        plugin.getLogger().info("[VelocityDev] Console command: /plugman {}", arguments);
    }

}
