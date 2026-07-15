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
    private static final String VELOCITY_RELOAD_DEBUG_KEY = "velocityReloadDebug";
    private static final String SHOW_VELOCITY_WARNING_KEY = "showVelocityWarning";
    private static final String[] REMOVED_DEV_KEYS = {
            "velocityDevMode", "velocityCrashDumps", "velocityDevTestFunctions"
    };

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
    public void initializeConfiguration() {
        super.initializeConfiguration();
        ensureVelocityOptions();
    }

    @Override
    public void reloadConfiguration() {
        super.reloadConfiguration();
        ensureVelocityOptions();
    }

    private void ensureVelocityOptions() {
        if (!configProvider.contains(SHOW_VELOCITY_WARNING_KEY)) {
            configProvider.set(SHOW_VELOCITY_WARNING_KEY, true);
        }
        if (!configProvider.contains(VELOCITY_RELOAD_DEBUG_KEY)) {
            configProvider.set(VELOCITY_RELOAD_DEBUG_KEY, false);
        }
        removeLegacyDevOptions(configProvider);
        configProvider.save();
    }

    public boolean isVelocityReloadDebugEnabled() {
        return configProvider.getBoolean(VELOCITY_RELOAD_DEBUG_KEY, false);
    }

    public boolean isShowVelocityWarningEnabled() {
        return configProvider.getBoolean(SHOW_VELOCITY_WARNING_KEY, true);
    }

    static void removeLegacyDevOptions(YamlConfigurationProvider configProvider) {
        for (var removedKey : REMOVED_DEV_KEYS) configProvider.set(removedKey, null);
    }

}
