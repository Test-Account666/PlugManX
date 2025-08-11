package bukkit.com.rylinaux.plugman.commands;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

public class CommandCreator {
    public void registerCommand(String commandName, CommandExecutor executor, TabCompleter tabCompleter, String... aliases) {
        var command = PlugManBukkit.getInstance().getCommand(commandName);

        command.setExecutor(executor);
        command.setTabCompleter(tabCompleter);
    }
}
