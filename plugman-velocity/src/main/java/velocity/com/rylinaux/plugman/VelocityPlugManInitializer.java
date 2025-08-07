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

import core.com.rylinaux.plugman.auto.AutoFeatureManager;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.file.messaging.MessageFormatter;
import core.com.rylinaux.plugman.initialization.BasePlugManInitializer;
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.ThreadUtil;
import velocity.com.rylinaux.plugman.auto.VelocityAutoFeatureManager;
import velocity.com.rylinaux.plugman.config.VelocityConfigurationProvider;
import velocity.com.rylinaux.plugman.config.VelocityPlugManConfigurationManager;
import velocity.com.rylinaux.plugman.messaging.VelocityColorFormatter;
import velocity.com.rylinaux.plugman.pluginmanager.VelocityPluginManager;
import velocity.com.rylinaux.plugman.util.VelocityThreadUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Velocity-specific initializer that extends the base initializer with platform-specific implementations.
 *
 * @author rylinaux
 */
public class VelocityPlugManInitializer extends BasePlugManInitializer {

    private final PlugManVelocity plugin;

    public VelocityPlugManInitializer(PlugManVelocity plugin, ServiceRegistry serviceRegistry, PluginLogger logger) {
        super(serviceRegistry, logger);
        this.plugin = plugin;
    }

    @Override
    protected PlugManConfigurationManager createConfigurationManager() {
        var configurationManager = VelocityPlugManConfigurationManager.of(plugin);
        configurationManager.initializeConfiguration();
        configurationManager.getIgnoredPlugins().add("PlugManVelocity");
        return configurationManager;
    }

    @Override
    protected PluginManager createPluginManager() {
        return new VelocityPluginManager();
    }

    @Override
    protected ThreadUtil createThreadUtil() {
        return new VelocityThreadUtil();
    }

    @Override
    protected MessageFormatter createMessageFormatter() throws IOException {
        var messagesFile = getDataFolder().toPath().resolve("messages.yml");
        if (!Files.exists(messagesFile)) {
            // Create default messages file if it doesn't exist
            Files.createDirectories(messagesFile.getParent());
            try (var inputStream = getClass().getResourceAsStream("/messages.yml")) {
                if (inputStream != null) Files.copy(inputStream, messagesFile);
            }
        }

        var configProvider = new VelocityConfigurationProvider(messagesFile);
        var colorFormatter = new VelocityColorFormatter();
        return new MessageFormatter(configProvider, colorFormatter);
    }

    @Override
    protected AutoFeatureManager createAutoFeatureManager() {
        return new VelocityAutoFeatureManager(serviceRegistry);
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataDirectory().toFile();
    }
}