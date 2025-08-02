package core.com.rylinaux.plugman.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.com.rylinaux.plugman.config.model.PlugManConfig;
import core.com.rylinaux.plugman.config.model.ResourceMappingsConfig;
import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Jackson-based configuration service for reading and writing YAML configurations.
 * Replaces the annotation-based configuration system.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class JacksonConfigurationService {

    private final ObjectMapper objectMapper;
    private final Yaml yaml;

    /**
     * Create a new configuration service with SnakeYAML and Jackson support
     */
    public JacksonConfigurationService() {
        objectMapper = new ObjectMapper();
        yaml = new Yaml();
    }

    /**
     * Load PlugMan configuration from file
     *
     * @param configFile the configuration file
     * @return the loaded configuration
     * @throws IOException if reading fails
     */
    public PlugManConfig loadPlugManConfig(File configFile) throws IOException {
        if (!configFile.exists()) return new PlugManConfig();
        try (var fis = new FileInputStream(configFile)) {
            var yamlData = yaml.load(fis);
            return objectMapper.convertValue(yamlData, PlugManConfig.class);
        }
    }

    /**
     * Save PlugMan configuration to file
     *
     * @param config     the configuration to save
     * @param configFile the file to save to
     * @throws IOException if writing fails
     */
    public void savePlugManConfig(PlugManConfig config, File configFile) throws IOException {
        var configData = objectMapper.convertValue(config, Object.class);
        try (var writer = new FileWriter(configFile)) {
            yaml.dump(configData, writer);
        }
    }

    /**
     * Load resource mappings configuration from file
     *
     * @param resourceMappingsFile the resource mappings file
     * @return the loaded resource mappings
     * @throws IOException if reading fails
     */
    public ResourceMappingsConfig loadResourceMappings(File resourceMappingsFile) throws IOException {
        if (!resourceMappingsFile.exists()) return new ResourceMappingsConfig();
        try (var fis = new FileInputStream(resourceMappingsFile)) {
            var yamlData = yaml.load(fis);
            return objectMapper.convertValue(yamlData, ResourceMappingsConfig.class);
        }
    }

    /**
     * Save resource mappings configuration to file
     *
     * @param config               the resource mappings to save
     * @param resourceMappingsFile the file to save to
     * @throws IOException if writing fails
     */
    public void saveResourceMappings(ResourceMappingsConfig config, File resourceMappingsFile) throws IOException {
        var configData = objectMapper.convertValue(config, Object.class);
        try (var writer = new FileWriter(resourceMappingsFile)) {
            yaml.dump(configData, writer);
        }
    }

    /**
     * Create a default PlugMan configuration
     *
     * @return the default configuration
     */
    public PlugManConfig createDefaultConfig() {
        return new PlugManConfig();
    }

    /**
     * Create a default resource mappings configuration
     *
     * @return the default resource mappings
     */
    public ResourceMappingsConfig createDefaultResourceMappings() {
        return new ResourceMappingsConfig();
    }
}