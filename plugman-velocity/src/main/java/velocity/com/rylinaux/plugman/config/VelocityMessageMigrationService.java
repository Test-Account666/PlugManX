package velocity.com.rylinaux.plugman.config;

import core.com.rylinaux.plugman.initialization.BasePlugManInitializer;
import core.com.rylinaux.plugman.logging.PluginLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds missing Velocity message keys without replacing customized values. */
public final class VelocityMessageMigrationService {
    private final Path dataFolder;
    private final PluginLogger logger;

    public VelocityMessageMigrationService(Path dataFolder, PluginLogger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void migrate() {
        migrateFile("messages.yml", dataFolder.resolve("messages.yml"));
        for (var language : BasePlugManInitializer.LANGUAGES) {
            var name = "messages_" + language + ".yml";
            migrateFile(name, dataFolder.resolve("messages").resolve(name));
        }
    }

    private void migrateFile(String resourceName, Path target) {
        if (!Files.isRegularFile(target)) return;

        try (var defaultsStream = getClass().getClassLoader().getResourceAsStream(resourceName);
             var reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            if (defaultsStream == null) return;
            var yaml = new Yaml();
            var defaults = asStringMap(yaml.load(defaultsStream));
            var current = asStringMap(yaml.load(reader));
            if (!mergeMissing(current, defaults)) return;

            try (var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                yaml.dump(current, writer);
            }
            logger.info("Added missing Velocity messages to " + target.getFileName() + ".");
        } catch (IOException | RuntimeException exception) {
            logger.warning("Failed to migrate " + target.getFileName() + ": " + exception.getMessage());
        }
    }

    private static boolean mergeMissing(Map<String, Object> current, Map<String, Object> defaults) {
        var changed = false;
        for (var entry : defaults.entrySet()) {
            var existing = current.get(entry.getKey());
            if (existing instanceof Map<?, ?> existingMap && entry.getValue() instanceof Map<?, ?> defaultMap) {
                var mergedChild = asStringMap(existingMap);
                if (mergeMissing(mergedChild, asStringMap(defaultMap))) {
                    current.put(entry.getKey(), mergedChild);
                    changed = true;
                }
                continue;
            }
            if (!current.containsKey(entry.getKey())) {
                current.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private static Map<String, Object> asStringMap(Object value) {
        var result = new LinkedHashMap<String, Object>();
        if (!(value instanceof Map<?, ?> map)) return result;
        for (var entry : map.entrySet()) {
            var child = entry.getValue();
            result.put(String.valueOf(entry.getKey()), child instanceof Map<?, ?> ? asStringMap(child) : child);
        }
        return result;
    }
}
