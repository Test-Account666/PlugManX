package velocity.com.rylinaux.plugman.config;

import core.com.rylinaux.plugman.config.JacksonConfigurationService;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import core.com.rylinaux.plugman.logging.PluginLogger;
import velocity.com.rylinaux.plugman.PlugManVelocity;
import velocity.com.rylinaux.plugman.logging.VelocityPluginLogger;

/**
 * Velocity wrapper for the core PlugManConfigurationManager.
 * Delegates all functionality to the core implementation while maintaining backward compatibility.
 *
 * @author rylinaux
 */
public class VelocityPlugManConfigurationManager extends PlugManConfigurationManager {
    private final YamlConfigurationProvider configProvider;

    private VelocityPlugManConfigurationManager(YamlConfigurationProvider configProvider, PluginLogger logger, JacksonConfigurationService jacksonConfigService) {
        super(configProvider, logger, jacksonConfigService);
        this.configProvider = configProvider;
    }

    public static PlugManConfigurationManager of(PlugManVelocity plugin) {
        var configFile = plugin.getDataDirectory().resolve("config.yml");
        var configProvider = new VelocityConfigurationProvider(configFile);
        var logger = new VelocityPluginLogger(plugin.getLogger());
        var jacksonConfigService = new JacksonConfigurationService();

        return new VelocityPlugManConfigurationManager(configProvider, logger, jacksonConfigService);
    }

    @Override
    protected int getCurrentConfigVersion() {
        return 6;
    }

    @Override
    public void initializeConfiguration() {
        super.initializeConfiguration();
        if (!configProvider.contains("velocityReloadDebug")) {
            configProvider.set("velocityReloadDebug", false);
            configProvider.save();
        }
    }

    public boolean isVelocityReloadDebugEnabled() {
        return configProvider.getBoolean("velocityReloadDebug", false);
    }
}
