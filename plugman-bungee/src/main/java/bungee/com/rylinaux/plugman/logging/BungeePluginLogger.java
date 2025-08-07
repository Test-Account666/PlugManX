package bungee.com.rylinaux.plugman.logging;

import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

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
}
