package paper.com.rylinaux.plugman.commands;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.commands.CommandCreator;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class PaperCommandCreator extends CommandCreator {

    @Override
    public void registerCommand(String commandName, CommandExecutor executor, TabCompleter tabCompleter, String... aliases) {
        PlugManBukkit.getInstance().registerCommand(commandName, createCommand(executor, tabCompleter, commandName));
        Arrays.stream(aliases).forEach(alias -> PlugManBukkit.getInstance().registerCommand(alias, createCommand(executor, tabCompleter, alias)));
    }

    private BasicCommand createCommand(CommandExecutor executor, TabCompleter tabCompleter, String label) {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                var sender = commandSourceStack.getSender();
                executor.onCommand(sender, null, label, args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                var sender = commandSourceStack.getSender();
                var suggestions = tabCompleter.onTabComplete(sender, null, label, args);
                if (suggestions == null) suggestions = Collections.emptyList();

                return suggestions;
            }
        };
    }
}
