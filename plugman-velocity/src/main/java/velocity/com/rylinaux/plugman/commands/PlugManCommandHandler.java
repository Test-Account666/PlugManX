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
import velocity.com.rylinaux.plugman.PlugManVelocity;

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
    private static final String[] COMMANDS = {
            "check", "deps", "disable", "dump", "enable", "help", "info", "list", "load", "lookup",
            "reload", "reloadconfig", "reloadmode", "restart", "unload", "usage"
    };

    @Override
    public void execute(Invocation invocation) {
        var sender = invocation.source();
        var args = invocation.arguments();

        if (!isVelocityConsole(sender)) {
            // Normally unreachable because hasPermission() hides the proxy command from players.
            // Stay silent so PlugManX never conflicts with a backend command of the same name.
            return;
        }

        var commandName = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";

        var plugManSender = new VelocityCommandSender(sender);
        var registry = PlugManVelocity.getInstance().getServiceRegistry();

        var cmd = switch (commandName) {
            case "list" -> new ListCommand(plugManSender, registry);
            case "dump" -> new DumpCommand(plugManSender, registry);
            case "info" -> new InfoCommand(plugManSender, registry);
            case "deps" -> new DepsCommand(plugManSender, registry);
            case "lookup" -> new LookupCommand(plugManSender, registry);
            case "usage" -> new UsageCommand(plugManSender, registry);
            case "enable" -> new EnableCommand(plugManSender, registry);
            case "load" -> new LoadCommand(plugManSender, registry);
            case "disable" -> new DisableCommand(plugManSender, registry);
            case "unload" -> new UnloadCommand(plugManSender, registry);
            case "restart" -> new RestartCommand(plugManSender, registry);
            case "reload" -> new ReloadCommand(plugManSender, registry);
            case "reloadconfig" -> new ReloadConfigCommand(plugManSender, registry);
            case "reloadmode" -> new ReloadModeCommand(plugManSender, registry);
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
            return Arrays.asList(COMMANDS);
        }
        
        // For now, return empty list for sub-command suggestions
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return isVelocityConsole(invocation.source());
    }

    private static boolean isVelocityConsole(CommandSource source) {
        var plugin = PlugManVelocity.getInstance();
        return plugin != null && source.equals(plugin.getServer().getConsoleCommandSource());
    }

}
