package bungee.com.rylinaux.plugman.logging;

import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bungee implementation of PluginLogger.
 * Bridges the core logging interface with Bungee's logging system.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class BungeePluginLogger implements PluginLogger {
    @Delegate
    private final Logger logger;

    @Override
    public void info(String message, Throwable throwable) {
        logger.log(Level.INFO, message, throwable);
    }

    @Override
    public void warning(String message, Throwable throwable) {
        logger.log(Level.WARNING, message, throwable);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}
