package core.com.rylinaux.plugman.config;

import core.com.rylinaux.plugman.config.model.PlugManConfig;
import core.com.rylinaux.plugman.config.model.ResourceMappingsConfig;
import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.List;

/**
 * Platform-agnostic configuration manager for PlugMan including config validation, migration, and resource mapping.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class PlugManConfigurationManager {
    public static final int CURRENT_CONFIG_VERSION = 3;

    private final YamlConfigurationProvider configProvider;
    private final PluginLogger logger;
    private final JacksonConfigurationService jacksonConfigService;


    /**
     * List of plugins to ignore, partially.
     */
    @Getter
    private List<String> ignoredPlugins = null;

    /**
     * Jackson-based configuration object
     */
    @Getter
    private PlugManConfig plugManConfig;

    @Getter
    private ResourceMappingsConfig resourceMappingsConfig;

    /**
     * Initialize and validate configuration
     */
    public void initializeConfiguration() {
        configProvider.saveDefaultConfig();
        loadJacksonConfigurations();
        validateAndMigrateConfig();
        loadIgnoredPlugins();
    }

    /**
     * Load Jackson-based configurations
     */
    private void loadJacksonConfigurations() {
        try {
            var configFile = new File(configProvider.getDataFolder(), "config.yml");
            plugManConfig = jacksonConfigService.loadPlugManConfig(configFile);

            var resourceMappingsFile = new File(configProvider.getDataFolder(), "resourcemaps.yml");
            resourceMappingsConfig = jacksonConfigService.loadResourceMappings(resourceMappingsFile);
        } catch (Exception e) {
            logger.severe("Failed to load Jackson configurations: " + e.getMessage());
            // Fallback to default configurations
            plugManConfig = jacksonConfigService.createDefaultConfig();
            resourceMappingsConfig = jacksonConfigService.createDefaultResourceMappings();
        }
    }

    /**
     * Validate configuration and create new one if invalid
     */
    private void validateAndMigrateConfig() {
        if (!isConfigValid()) {
            logger.severe("Invalid PlugMan config detected! Creating new one...");
            backupOldConfig();
            configProvider.saveDefaultConfig();
            logger.info("New config created!");
        }

        migrateConfigIfNeeded();
    }

    /**
     * Check if current configuration is valid
     */
    private boolean isConfigValid() {
        return plugManConfig != null &&
                plugManConfig.getAutoLoad() != null &&
                plugManConfig.getAutoUnload() != null &&
                plugManConfig.getAutoReload() != null &&
                plugManConfig.getIgnoredPlugins() != null;
    }

    /**
     * Backup old configuration file
     */
    private void backupOldConfig() {
        var oldConfig = new File(configProvider.getDataFolder(), "config.yml");
        var backupConfig = new File(configProvider.getDataFolder(), "config.yml.old-" + System.currentTimeMillis());
        oldConfig.renameTo(backupConfig);
    }

    /**
     * Migrate configuration to newer version if needed
     */
    private void migrateConfigIfNeeded() {
        var configVersion = plugManConfig.getVersion();

        var startTime = System.currentTimeMillis();

        while (configVersion < CURRENT_CONFIG_VERSION) {
            if (System.currentTimeMillis() - startTime > 10000) {
                logger.severe("PlugMan failed to migrate config to version " + CURRENT_CONFIG_VERSION + "! (Timed out)");
                break;
            }

            configVersion = plugManConfig.getVersion();

            if (configVersion <= 1) {
                migrateToVersion2();
                continue;
            }

            if (configVersion == 2) migrateToVersion3();
        }
    }

    private void migrateToVersion3() {
        plugManConfig.setVersion(3);
        plugManConfig.setShowPaperWarning(true);
        saveJacksonConfiguration();

        logger.info("Migrated config to version 3, you can now disable the Paper warning in the config.yml.");
    }

    /**
     * Migrate configuration to version 2
     */
    private void migrateToVersion2() {
        plugManConfig.setVersion(2);
        saveJacksonConfiguration();

        logger.warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.warning("As of 2.4.0, the download command has been removed!");
        logger.warning("If you weren't using it, you can just ignore this message.");
        logger.warning("This message will only display once!");
        logger.warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }

    /**
     * Save Jackson configuration to file
     */
    private void saveJacksonConfiguration() {
        try {
            var configFile = new File(configProvider.getDataFolder(), "config.yml");
            jacksonConfigService.savePlugManConfig(plugManConfig, configFile);
        } catch (Exception e) {
            logger.severe("Failed to save Jackson configuration: " + e.getMessage());
        }
    }

    /**
     * Load ignored plugins from configuration
     */
    private void loadIgnoredPlugins() {
        ignoredPlugins = new java.util.ArrayList<>(plugManConfig.getIgnoredPlugins());
        ignoredPlugins.add("PlugMan");
        ignoredPlugins.add("PlugManX");
    }


    /**
     * Get notification setting for broken command removal
     */
    public boolean shouldNotifyOnBrokenCommandRemoval() {
        return plugManConfig.isNotifyOnBrokenCommandRemoval();
    }
}