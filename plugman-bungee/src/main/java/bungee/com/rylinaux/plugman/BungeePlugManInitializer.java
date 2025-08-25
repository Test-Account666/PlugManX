package bungee.com.rylinaux.plugman;

/*
 * #%L
 * PlugManBungee
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

import bungee.com.rylinaux.plugman.auto.BungeeAutoFeatureManager;
import bungee.com.rylinaux.plugman.config.BungeeConfigurationProvider;
import bungee.com.rylinaux.plugman.config.BungeePlugManConfigurationManager;
import bungee.com.rylinaux.plugman.messaging.BungeeColorFormatter;
import bungee.com.rylinaux.plugman.plugin.BungeePlugin;
import bungee.com.rylinaux.plugman.pluginmanager.BungeePluginManager;
import bungee.com.rylinaux.plugman.util.BungeeThreadUtil;
import core.com.rylinaux.plugman.auto.AutoFeatureManager;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.file.messaging.MessageFormatter;
import core.com.rylinaux.plugman.initialization.BasePlugManInitializer;
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.ThreadUtil;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Bungee-specific initializer that extends the base initializer with platform-specific implementations.
 *
 * @author rylinaux
 */
public class BungeePlugManInitializer extends BasePlugManInitializer {

    private final PlugManBungee plugin;

    public BungeePlugManInitializer(PlugManBungee plugin, ServiceRegistry serviceRegistry, PluginLogger logger) {
        super(new BungeePlugin(plugin), serviceRegistry, logger);
        this.plugin = plugin;
    }

    @Override
    protected PlugManConfigurationManager createConfigurationManager() {
        var configurationManager = BungeePlugManConfigurationManager.of(plugin);
        configurationManager.initializeConfiguration();
        configurationManager.getIgnoredPlugins().add("PlugManBungee");
        return configurationManager;
    }

    @Override
    protected PluginManager createPluginManager() {
        return new BungeePluginManager();
    }

    @Override
    protected ThreadUtil createThreadUtil() {
        return new BungeeThreadUtil();
    }

    @Override
    protected MessageFormatter createMessageFormatter() throws IOException {
        var messagesFile = new File(getDataFolder(), "messages.yml");
        var config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(messagesFile);
        var configProvider = new BungeeConfigurationProvider(config, messagesFile);
        var colorFormatter = new BungeeColorFormatter();
        return new MessageFormatter(configProvider, colorFormatter);
    }

    @Override
    protected AutoFeatureManager createAutoFeatureManager() {
        return new BungeeAutoFeatureManager(serviceRegistry);
    }
}