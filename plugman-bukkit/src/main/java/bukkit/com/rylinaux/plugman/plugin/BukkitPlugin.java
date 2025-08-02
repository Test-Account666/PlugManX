package bukkit.com.rylinaux.plugman.plugin;

import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;


public record BukkitPlugin(@Delegate org.bukkit.plugin.Plugin bukkitPlugin) implements Plugin {
    @Override
    public String getVersion() {
        return getDescription().getVersion();
    }

    @Override
    public List<String> getDepend() {
        return getDescription().getDepend();
    }

    @Override
    public List<String> getSoftDepend() {
        return getDescription().getSoftDepend();
    }

    @Override
    public List<String> getAuthors() {
        return getDescription().getAuthors();
    }

    @SneakyThrows
    @Override
    public File getFile() {
        return FieldAccessor.getValue(JavaPlugin.class, "getFile", bukkitPlugin);
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.bukkit.plugin.Plugin getHandle() {
        return bukkitPlugin();
    }
}