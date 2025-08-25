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
import core.com.rylinaux.plugman.services.ServiceRegistry;
import lombok.Getter;
import lombok.experimental.Delegate;
import manifold.rt.api.NoBootstrap;
import org.slf4j.Logger;
import velocity.com.rylinaux.plugman.commands.PlugManCommandHandler;
import velocity.com.rylinaux.plugman.logging.VelocityPluginLogger;

import java.nio.file.Path;

/**
 * Main plugin class for PlugMan Velocity implementation.
 *
 * @author rylinaux
 */
@Plugin(
        id = "plugmanvelocity",
        name = "PlugManVelocity",
        version = "3.0.2-RC.1",
        description = "Plugin manager for Velocity servers.",
        authors = {"rylinaux", "TestAccount666"}
)
public final class PlugManVelocity {

    @Getter
    private static PlugManVelocity instance;

    @Getter
    @Delegate
    private ServiceRegistry serviceRegistry;

    private final PluginContainer container;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityPlugManInitializer initializer;

    @Inject
    public PlugManVelocity(PluginContainer container, ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.container = container
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;

        serviceRegistry = new ServiceRegistry();
        var pluginLogger = new VelocityPluginLogger(logger);
        initializer = new VelocityPlugManInitializer(this, container, serviceRegistry, pluginLogger);

        initializer.initializeCoreServices();
        initializer.setupMessaging();

        server.getCommandManager().register("plugman", new PlugManCommandHandler());

        initializer.setupAutoFeatures();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        instance = null;
        var pluginLogger = new VelocityPluginLogger(logger);
        initializer.cleanup();
        server.getCommandManager().unregister("plugman");
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
}