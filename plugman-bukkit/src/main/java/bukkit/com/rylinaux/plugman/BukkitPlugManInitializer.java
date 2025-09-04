package bukkit.com.rylinaux.plugman;

/*
 * #%L
 * PlugMan
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

import bukkit.com.rylinaux.plugman.auto.BukkitAutoFeatureManager;
import bukkit.com.rylinaux.plugman.config.BukkitConfigurationProvider;
import bukkit.com.rylinaux.plugman.config.BukkitPlugManConfigurationManager;
import bukkit.com.rylinaux.plugman.messaging.BukkitColorFormatter;
import bukkit.com.rylinaux.plugman.plugin.BukkitPlugin;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import bukkit.com.rylinaux.plugman.util.BukkitThreadUtil;
import core.com.rylinaux.plugman.auto.AutoFeatureManager;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.file.messaging.MessageFormatter;
import core.com.rylinaux.plugman.initialization.BasePlugManInitializer;
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.ThreadUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Bukkit-specific initializer that extends the base initializer with platform-specific implementations.
 *
 * @author rylinaux
 */
public class BukkitPlugManInitializer extends BasePlugManInitializer {

    private final PlugManBukkit plugin;

    public BukkitPlugManInitializer(PlugManBukkit plugin, ServiceRegistry serviceRegistry, PluginLogger logger) {
        super(new BukkitPlugin(plugin), serviceRegistry, logger);
        this.plugin = plugin;
    }

    @Override
    protected PlugManConfigurationManager createConfigurationManager() {
        return BukkitPlugManConfigurationManager.of(plugin);
    }

    @Override
    protected PluginManager createPluginManager() {
        return new BukkitPluginManager();
    }

    @Override
    protected ThreadUtil createThreadUtil() {
        return new BukkitThreadUtil();
    }

    @Override
    protected MessageFormatter createMessageFormatter() {
        var messagesFile = new File(getDataFolder(), "messages.yml");
        var config = YamlConfiguration.loadConfiguration(messagesFile);
        return new MessageFormatter(new BukkitConfigurationProvider(config, messagesFile), new BukkitColorFormatter());
    }

    @Override
    protected AutoFeatureManager createAutoFeatureManager() {
        return new BukkitAutoFeatureManager(serviceRegistry);
    }
}