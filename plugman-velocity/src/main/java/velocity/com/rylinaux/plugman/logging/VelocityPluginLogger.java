package velocity.com.rylinaux.plugman.logging;

import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

/**
 * Velocity implementation of PluginLogger.
 * Bridges the core logging interface with Velocity's SLF4J logging system.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class VelocityPluginLogger implements PluginLogger {
    private final Logger logger;

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }
}