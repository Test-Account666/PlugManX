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
    private static final String VELOCITY_DEV_MODE_KEY = "velocityDevMode";
    private static final String VELOCITY_CRASH_DUMPS_KEY = "velocityCrashDumps";
    private static final String VELOCITY_DEV_TEST_FUNCTIONS_KEY = "velocityDevTestFunctions";

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
        if (!configProvider.contains(VELOCITY_RELOAD_DEBUG_KEY)) {
            configProvider.set(VELOCITY_RELOAD_DEBUG_KEY, false);
        }
        if (!configProvider.contains(VELOCITY_DEV_MODE_KEY)) {
            configProvider.set(VELOCITY_DEV_MODE_KEY, false);
        }
        if (!configProvider.contains(VELOCITY_CRASH_DUMPS_KEY)) {
            configProvider.set(VELOCITY_CRASH_DUMPS_KEY, true);
        }
        if (!configProvider.contains(VELOCITY_DEV_TEST_FUNCTIONS_KEY)) {
            configProvider.set(VELOCITY_DEV_TEST_FUNCTIONS_KEY, false);
        }
        configProvider.save();
    }

    public boolean isVelocityReloadDebugEnabled() {
        return configProvider.getBoolean(VELOCITY_RELOAD_DEBUG_KEY, false);
    }

    public boolean isVelocityDevModeEnabled() {
        return configProvider.getBoolean(VELOCITY_DEV_MODE_KEY, false);
    }

    public boolean areVelocityCrashDumpsEnabled() {
        return configProvider.getBoolean(VELOCITY_CRASH_DUMPS_KEY, true);
    }

    public boolean areVelocityDevTestFunctionsEnabled() {
        return configProvider.getBoolean(VELOCITY_DEV_TEST_FUNCTIONS_KEY, false);
    }
}
