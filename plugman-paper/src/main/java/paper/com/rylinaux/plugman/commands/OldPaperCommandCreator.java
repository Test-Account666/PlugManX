package paper.com.rylinaux.plugman.commands;

import bukkit.com.rylinaux.plugman.commands.CommandCreator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class OldPaperCommandCreator extends CommandCreator {
    @Override
    public void registerCommand(String commandName, CommandExecutor executor, TabCompleter tabCompleter, String... aliases) {
        var command = new Command(commandName, "", "", List.of(aliases)) {
            @Override
            public boolean execute(CommandSender commandSender, String label, String[] arguments) {
                return executor.onCommand(commandSender, this, label, arguments);
            }

            @Override
            public @NotNull List<String> tabComplete(CommandSender commandSender, String label, String[] arguments) {
                var suggestions = tabCompleter.onTabComplete(commandSender, this, label, arguments);
                if (suggestions == null) suggestions = Collections.emptyList();

                return suggestions;
            }
        };

        Bukkit.getCommandMap().register(commandName, "plugmanx", command);

        for (var alias : aliases) Bukkit.getCommandMap().register(alias, "plugmanx", command);
    }
}
