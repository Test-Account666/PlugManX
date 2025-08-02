package bungee.com.rylinaux.plugman.commands;

/*
 * #%L
 * PlugManBungee
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

import bungee.com.rylinaux.plugman.PlugManBungee;
import core.com.rylinaux.plugman.commands.executables.*;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

/**
 * BungeeCord command handler for PlugMan commands.
 * Listens for commands and executes them using the core command system.
 *
 * @author rylinaux
 */
public class PlugManCommandHandler extends Command {

    public PlugManCommandHandler() {
        super("plugmanbungee", "plugman.admin", "plmb");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        var commandName = args.length > 0? args[0].toLowerCase() : "help";

        var plugManSender = new BungeeCommandSender(sender);
        var registry = PlugManBungee.getInstance().getServiceRegistry();

        var cmd = switch (commandName) {
            case "list" -> new ListCommand(plugManSender, registry);
            case "dump" -> new DumpCommand(plugManSender, registry);
            case "info" -> new InfoCommand(plugManSender, registry);
            case "lookup" -> new LookupCommand(plugManSender, registry);
            case "usage" -> new UsageCommand(plugManSender, registry);
            case "enable" -> new EnableCommand(plugManSender, registry);
            case "disable" -> new DisableCommand(plugManSender, registry);
            case "restart" -> new RestartCommand(plugManSender, registry);
            case "load" -> new LoadCommand(plugManSender, registry);
            case "reload" -> new ReloadCommand(plugManSender, registry);
            case "unload" -> new UnloadCommand(plugManSender, registry);
            case "check" -> new CheckCommand(plugManSender, registry);
            default -> new HelpCommand(plugManSender, registry);
        };

        if (!cmd.hasPermission()) {
            cmd.sendNoPermissionMessage();
            return;
        }

        cmd.execute(cmd.getSender(), "plmb", args);
    }
}