package bungee.com.rylinaux.plugman.config;

import bungee.com.rylinaux.plugman.PlugManBungee;
import bungee.com.rylinaux.plugman.logging.BungeePluginLogger;
import core.com.rylinaux.plugman.config.JacksonConfigurationService;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import core.com.rylinaux.plugman.logging.PluginLogger;

/**
 * Bungee wrapper for the core PlugManConfigurationManager.
 * Delegates all functionality to the core implementation while maintaining backward compatibility.
 *
 * @author rylinaux
 */
public class BungeePlugManConfigurationManager extends PlugManConfigurationManager {
    private BungeePlugManConfigurationManager(YamlConfigurationProvider configProvider, PluginLogger logger, JacksonConfigurationService jacksonConfigService) {
        super(configProvider, logger, jacksonConfigService);
    }

    public static PlugManConfigurationManager of(PlugManBungee plugin) {
        var configProvider = new BungeeConfigurationProvider(plugin.getConfig(), plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        var logger = new BungeePluginLogger(plugin.getLogger());
        var jacksonConfigService = new JacksonConfigurationService();

        return new BungeePlugManConfigurationManager(configProvider, logger, jacksonConfigService);
    }
}
