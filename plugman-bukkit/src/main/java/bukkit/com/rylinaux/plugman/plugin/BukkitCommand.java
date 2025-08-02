package bukkit.com.rylinaux.plugman.plugin;

import core.com.rylinaux.plugman.plugins.Command;
import lombok.experimental.Delegate;


public record BukkitCommand(@Delegate org.bukkit.command.Command bukkitCommand) implements Command {
    @SuppressWarnings("unchecked")
    @Override
    public org.bukkit.command.Command getHandle() {
        return bukkitCommand();
    }
}
