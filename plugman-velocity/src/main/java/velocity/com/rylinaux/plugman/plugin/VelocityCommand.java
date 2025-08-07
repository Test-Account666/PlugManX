package velocity.com.rylinaux.plugman.plugin;

import com.velocitypowered.api.command.CommandMeta;
import core.com.rylinaux.plugman.plugins.Command;
import lombok.Getter;

public record VelocityCommand(@Getter CommandMeta commandMeta) implements Command {

    @Override
    public CommandMeta getHandle() {
        return getCommandMeta();
    }
}
