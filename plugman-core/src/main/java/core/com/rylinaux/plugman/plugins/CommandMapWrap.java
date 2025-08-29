package core.com.rylinaux.plugman.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class CommandMapWrap<T> {
    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, T> knownCommands;

    public CommandMapWrap(Map<String, T> knownCommands, Function<T, ? extends Command> pluginCommandFactory) {
        this.knownCommands = knownCommands;

        // Note: Never use `forEach` here. The implementation of `forEach` seems to be a no-op
        for (var entry : knownCommands.entrySet()) commands.put(entry.getKey(), pluginCommandFactory.apply(entry.getValue()));
    }

    public Command get(String key) {
        return commands.get(key);
    }

    public Command put(String key, Command value) {
        if (!knownCommands.containsKey(key)) knownCommands.put(key, value.getHandle());
        return commands.put(key, value);
    }

    public void putAll(Map<? extends String, ? extends Command> otherMap) {
        otherMap.forEach((name, command) -> knownCommands.put(name, command.getHandle()));
        commands.putAll(otherMap);
    }

    public Command remove(String key) {
        knownCommands.remove(key);
        return commands.remove(key);
    }

    public boolean remove(String key, Command value) {
        knownCommands.remove(key, value.<T>getHandle());
        return commands.remove(key, value);
    }

    public boolean containsKey(String key) {
        return commands.containsKey(key);
    }

    public int size() {
        return commands.size();
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public Map<String, Command> asMap() {
        return Map.copyOf(commands);
    }

    public Set<Map.Entry<String, Command>> entrySet() {
        return commands.entrySet();
    }

    public Set<String> keySet() {
        return commands.keySet();
    }
}