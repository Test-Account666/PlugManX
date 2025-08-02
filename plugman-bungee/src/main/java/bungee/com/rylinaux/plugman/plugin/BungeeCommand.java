package bungee.com.rylinaux.plugman.plugin;

import core.com.rylinaux.plugman.plugins.Command;
import lombok.Getter;
import lombok.experimental.Delegate;

public record BungeeCommand(@Delegate net.md_5.bungee.api.plugin.Command bungeeCommand) implements Command {
    @SuppressWarnings("unchecked")
    @Override
    public net.md_5.bungee.api.plugin.Command getHandle() {
        return bungeeCommand();
    }
}