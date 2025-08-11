package core.com.rylinaux.plugman.plugins;

public interface Command {

    <T> T getHandle();
}
