package velocity.com.rylinaux.plugman.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import core.com.rylinaux.plugman.commands.CommandSender;
import net.kyori.adventure.text.Component;
import velocity.com.rylinaux.plugman.PlugManVelocity;

public record VelocityCommandSender(CommandSource commandSource) implements CommandSender {
    @Override
    public void sendMessage(String message) {
        sendMessage(true, message, new Object[0]);
    }

    @Override
    public void sendMessage(boolean prefix, String message) {
        sendMessage(prefix, message, new Object[0]);
    }

    @Override
    public void sendMessage(String message, Object... args) {
        sendMessage(true, message, args);
    }

    @Override
    public void sendMessage(boolean prefix, String message, Object... args) {
        message = PlugManVelocity.getInstance().getMessageFormatter().formatMessage(prefix, message, args);
        commandSource().sendMessage(Component.text(String.format(message, args)));
    }

    @Override
    public boolean hasPermission(String permission) {
        return commandSource().hasPermission(permission);
    }

    @Override
    public boolean isConsole() {
        return !(commandSource() instanceof Player);
    }

    @Override
    public CommandSource getHandle() {
        return commandSource();
    }
}
