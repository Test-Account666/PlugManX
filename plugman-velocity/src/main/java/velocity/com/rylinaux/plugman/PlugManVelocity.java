package velocity.com.rylinaux.plugman;

/*
 * #%L
 * PlugManVelocity
 * %%
 * Copyright (C) 2010 - 2024 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.PluginContainer;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import lombok.Getter;
import lombok.experimental.Delegate;
import manifold.rt.api.NoBootstrap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import velocity.com.rylinaux.plugman.commands.PlugManCommandHandler;
import velocity.com.rylinaux.plugman.config.VelocityPlugManConfigurationManager;
import velocity.com.rylinaux.plugman.logging.VelocityPluginLogger;
import velocity.com.rylinaux.plugman.pluginmanager.VelocityPluginManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main plugin class for PlugMan Velocity implementation.
 *
 * @author rylinaux
 */
@Plugin(
        id = "plugmanvelocity",
        name = "PlugManVelocity",
        version = "3.0.5",
        description = "Plugin manager for Velocity servers.",
        authors = {"rylinaux", "TestAccount666"}
)
public final class PlugManVelocity {

    private static final String WARNING_BORDER =
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~";
    private static final Component CONSOLE_PREFIX = Component.text("[PlugManX] ", NamedTextColor.GREEN);

    @Getter
    private static PlugManVelocity instance;

    @Getter
    @Delegate
    private ServiceRegistry serviceRegistry;

    private final PluginContainer container;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Path legacyDataDirectory;

    private VelocityPlugManInitializer initializer;

    @Inject
    public PlugManVelocity(PluginContainer container, ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.container = container;
        this.server = server;
        this.logger = logger;
        this.legacyDataDirectory = dataDirectory;
        this.dataDirectory = dataDirectory.resolveSibling("PlugManX");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        setInstance(this);
        prepareDataDirectory();

        serviceRegistry = new ServiceRegistry();
        var pluginLogger = new VelocityPluginLogger(logger);
        initializer = new VelocityPlugManInitializer(this, container, serviceRegistry, pluginLogger);

        initializer.initializeCoreServices();
        showVelocityWarningIfNeeded();
        initializer.setupMessaging();

        server.getCommandManager().register("plugman", new PlugManCommandHandler());

        initializer.setupAutoFeatures();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        setInstance(null);
        initializer.cleanup();
        server.getCommandManager().unregister("plugman");
    }

    private static void setInstance(PlugManVelocity plugin) {
        instance = plugin;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    private void prepareDataDirectory() {
        try {
            Files.createDirectories(dataDirectory);
            migrateLegacyFile("config.yml");
            migrateLegacyFile("resourcemaps.yml");
        } catch (IOException exception) {
            logger.error("Failed to prepare PlugManX data directory {}", dataDirectory, exception);
        }
    }

    private void migrateLegacyFile(String fileName) throws IOException {
        if (legacyDataDirectory.equals(dataDirectory)) return;

        var source = legacyDataDirectory.resolve(fileName);
        var target = dataDirectory.resolve(fileName);
        if (!Files.isRegularFile(source) || Files.exists(target)) return;

        Files.copy(source, target);
        logger.info("Migrated {} to {}", source, target);
    }

    private void showVelocityWarningIfNeeded() {
        var configurationManager = get(PlugManConfigurationManager.class);
        var showDiagnostics = configurationManager != null
                && configurationManager.getPlugManConfig().isShowVelocityWarning();
        var startupState = createVelocityStartupState();
        var proxyVersion = server.getVersion();

        sendWarningLine(Component.text(WARNING_BORDER, NamedTextColor.DARK_GRAY));
        sendWarningLine(Component.text("It seems like you're running on ", NamedTextColor.YELLOW)
                .append(Component.text(proxyVersion.getName() + " (" + proxyVersion.getVersion() + ")",
                        NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.YELLOW)));
        if (showDiagnostics) sendVelocityDiagnostics(configurationManager, startupState);
        sendWarningLine(Component.text(
                "This PlugManX Velocity artifact is a development build.", NamedTextColor.YELLOW));
        sendWarningLine(Component.text(
                "Velocity runtime plugin management uses unsupported internal APIs.", NamedTextColor.YELLOW));
        if (startupState.compatibilityWarning() != null) {
            sendWarningLine(Component.text(startupState.compatibilityWarning(), NamedTextColor.RED));
        }
        sendWarningLine(Component.text(
                "If an error occurs, enable velocityReloadDebug, reproduce it,", NamedTextColor.YELLOW));
        sendWarningLine(Component.text(
                "then create a GitHub issue and include the logs and crash dump ID.", NamedTextColor.YELLOW));
        sendWarningLine(Component.text(
                        "Also, if you encounter any issues, please join my discord: ", NamedTextColor.YELLOW)
                .append(Component.text("https://discord.gg/GxEFhVY6ff", NamedTextColor.BLUE)));
        sendWarningLine(Component.text("Or create an issue on GitHub: ", NamedTextColor.YELLOW)
                .append(Component.text("https://github.com/Test-Account666/PlugManX", NamedTextColor.BLUE)));
        sendWarningLine(Component.text(WARNING_BORDER, NamedTextColor.DARK_GRAY));
        sendWarningLine(Component.text(
                "You can disable this warning by setting 'showVelocityWarning' to false in config.yml",
                NamedTextColor.YELLOW));
    }

    private VelocityStartupState createVelocityStartupState() {
        var pluginManager = get(core.com.rylinaux.plugman.plugins.PluginManager.class);
        if (!(pluginManager instanceof VelocityPluginManager velocityManager)) {
            return new VelocityStartupState(false, "unavailable", null);
        }
        return new VelocityStartupState(
                velocityManager.isDevelopmentRuntimeAvailable(),
                velocityManager.getDevelopmentRuntimeAdapterName(),
                velocityManager.getDevelopmentRuntimeCompatibilityWarning());
    }

    private void sendVelocityDiagnostics(PlugManConfigurationManager configurationManager,
                                         VelocityStartupState startupState) {
        var proxyVersion = server.getVersion();
        sendDiagnosticLine("Detected proxy software: ", proxyVersion.getName());
        sendDiagnosticLine("Velocity version: ", proxyVersion.getVersion());
        sendDiagnosticLine("Java version: ", System.getProperty("java.version", "Unknown"));
        sendDiagnosticLine("Velocity reload strategy: ", startupState.adapter());
        sendDiagnosticLine("Runtime reload capabilities available: ", startupState.available() ? "yes" : "no");

        if (!(configurationManager instanceof VelocityPlugManConfigurationManager velocityConfig)) return;
        sendDiagnosticLine("Velocity reload debug enabled: ",
                velocityConfig.isVelocityReloadDebugEnabled() ? "yes" : "no");
    }

    private void sendDiagnosticLine(String label, String value) {
        sendWarningLine(Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.AQUA)));
    }

    private void sendWarningLine(Component message) {
        server.getConsoleCommandSource().sendMessage(CONSOLE_PREFIX.append(message));
    }

    private record VelocityStartupState(boolean available, String adapter, String compatibilityWarning) {
    }
}
