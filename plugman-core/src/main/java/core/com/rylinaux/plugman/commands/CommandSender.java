package core.com.rylinaux.plugman.commands;

public interface CommandSender {

    void sendMessage(String message);

    void sendMessage(boolean prefix, String message);

    void sendMessage(String message, Object... args);

    void sendMessage(boolean prefix, String message, Object... args);

    boolean hasPermission(String permission);

    boolean isConsole();

    <T> T getHandle();
}
