package velocity.com.rylinaux.plugman.config;

import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import org.yaml.snakeyaml.Yaml;
import velocity.com.rylinaux.plugman.PlugManVelocity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SnakeYAML-backed configuration provider for Velocity.
 */
public class VelocityConfigurationProvider implements YamlConfigurationProvider {
    private static final Object MISSING_VALUE = new Object();

    private final Path configPath;
    private volatile Map<String, Object> values = new LinkedHashMap<>();

    public VelocityConfigurationProvider(Path configPath) {
        this.configPath = configPath;
        reloadFromDisk();
    }

    @Override
    public YamlConfigurationProvider loadConfiguration(File file) {
        reloadFromDisk();
        return this;
    }

    @Override
    public Object get(String path, Object def) {
        var value = findValue(path);
        return value == MISSING_VALUE ? def : value;
    }

    @Override
    public String getString(String path, String def) {
        var value = get(path, def);
        return value == null ? def : String.valueOf(value);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        var value = get(path, def);
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof String stringValue) return Boolean.parseBoolean(stringValue);
        return def;
    }

    @Override
    public int getInt(String path, int def) {
        var value = get(path, def);
        return value instanceof Number numberValue ? numberValue.intValue() : def;
    }

    @Override
    public long getLong(String path, long def) {
        var value = get(path, def);
        return value instanceof Number numberValue ? numberValue.longValue() : def;
    }

    @Override
    public List<String> getStringList(String path) {
        var value = get(path);
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return Collections.emptyList();
    }

    @Override
    public boolean contains(String path) {
        return findValue(path) != MISSING_VALUE;
    }

    @Override
    public YamlConfigurationSection getConfigurationSection(String path) {
        var value = findValue(path);
        if (!(value instanceof Map<?, ?> section)) return null;

        return new YamlConfigurationSection() {
            @Override
            public Set<String> getKeys(boolean deep) {
                var keys = new LinkedHashSet<String>();
                collectKeys(section, "", deep, keys);
                return keys;
            }

            @Override
            public String getName() {
                var separator = path.lastIndexOf('.');
                return separator < 0 ? path : path.substring(separator + 1);
            }
        };
    }

    @Override
    public boolean isSet(String key) {
        return contains(key);
    }

    @Override
    public synchronized void set(String key, Object value) {
        var segments = splitPath(key);
        if (segments.length == 0) return;

        var updatedValues = deepCopy(values);
        Map<String, Object> current = updatedValues;
        for (var index = 0; index < segments.length - 1; index++) {
            var child = current.get(segments[index]);
            if (child instanceof Map<?, ?> childMap) {
                var mutableChild = copyMap(childMap);
                current.put(segments[index], mutableChild);
                current = mutableChild;
            } else {
                var mutableChild = new LinkedHashMap<String, Object>();
                current.put(segments[index], mutableChild);
                current = mutableChild;
            }
        }

        if (value == null) current.remove(segments[segments.length - 1]);
        else current.put(segments[segments.length - 1], value);
        values = updatedValues;
    }

    @Override
    public void saveDefaultConfig() {
        if (Files.exists(configPath)) {
            reloadFromDisk();
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());
            try (var inputStream = getClass().getResourceAsStream("/config.yml")) {
                if (inputStream != null) Files.copy(inputStream, configPath);
            }
            reloadFromDisk();
        } catch (IOException exception) {
            logFailure("save default configuration", exception);
        }
    }

    @Override
    public synchronized void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (var writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                new Yaml().dump(values, writer);
            }
        } catch (IOException exception) {
            logFailure("save configuration", exception);
        }
    }

    @Override
    public File getDataFolder() {
        return PlugManVelocity.getInstance().getDataDirectory().toFile();
    }

    private synchronized void reloadFromDisk() {
        if (!Files.isRegularFile(configPath)) {
            values = new LinkedHashMap<>();
            return;
        }

        try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            var loaded = new Yaml().load(reader);
            values = loaded instanceof Map<?, ?> map ? copyMap(map) : new LinkedHashMap<>();
        } catch (IOException | RuntimeException exception) {
            values = new LinkedHashMap<>();
            logFailure("load configuration", exception);
        }
    }

    private Object findValue(String path) {
        if (path == null || path.isBlank()) return values;

        Object current = values;
        for (var segment : splitPath(path)) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) return MISSING_VALUE;
            current = map.get(segment);
        }
        return current;
    }

    private static String[] splitPath(String path) {
        return path == null || path.isBlank() ? new String[0] : path.split("\\.");
    }

    private static void collectKeys(Map<?, ?> source, String prefix, boolean deep, Set<String> target) {
        for (var entry : source.entrySet()) {
            var key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            target.add(key);
            if (deep && entry.getValue() instanceof Map<?, ?> child) collectKeys(child, key, true, target);
        }
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        return copyMap(source);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            var value = entry.getValue();
            copy.put(String.valueOf(entry.getKey()),
                    value instanceof Map<?, ?> child ? copyMap(child) : value);
        }
        return copy;
    }

    private void logFailure(String action, Throwable throwable) {
        var plugin = PlugManVelocity.getInstance();
        if (plugin != null) plugin.getLogger().error("Failed to {}: {}", action, configPath, throwable);
    }
}
