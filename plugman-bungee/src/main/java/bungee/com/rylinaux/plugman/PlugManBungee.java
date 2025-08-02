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
import bungee.com.rylinaux.plugman.commands.PlugManCommandHandler;
import bungee.com.rylinaux.plugman.config.BungeeConfigurationProvider;
import bungee.com.rylinaux.plugman.config.BungeePlugManConfigurationManager;
import bungee.com.rylinaux.plugman.logging.BungeePluginLogger;
import bungee.com.rylinaux.plugman.messaging.BungeeColorFormatter;
import bungee.com.rylinaux.plugman.pluginmanager.BungeePluginManager;
import bungee.com.rylinaux.plugman.util.BungeeThreadUtil;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.file.messaging.MessageFormatter;
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.ThreadUtil;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import core.com.rylinaux.plugman.util.reflection.MethodAccessor;
import lombok.Getter;
import lombok.experimental.Delegate;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Main plugin class for PlugMan BungeeCord implementation.
 *
 * @author rylinaux
 */
public final class PlugManBungee extends Plugin implements Listener {

    @Getter
    private static PlugManBungee instance;

    @Getter
    @Delegate
    private ServiceRegistry serviceRegistry;
    @Getter
    private Configuration config;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        serviceRegistry = new ServiceRegistry();

        serviceRegistry.register(PluginLogger.class, new BungeePluginLogger(this));

        var configurationManager = BungeePlugManConfigurationManager.of(this);
        serviceRegistry.register(PlugManConfigurationManager.class, configurationManager);

        configurationManager.getIgnoredPlugins().add("PlugManBungee");

        var pluginManager = new BungeePluginManager();
        serviceRegistry.register(PluginManager.class, pluginManager);

        setupMessageFiles();

        try {
            var messagesFile = new File(getDataFolder(), "messages.yml");
            var config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(messagesFile);
            var configProvider = new BungeeConfigurationProvider(config);
            var colorFormatter = new BungeeColorFormatter();
            var messageFormatter = new MessageFormatter(configProvider, colorFormatter);
            serviceRegistry.register(MessageFormatter.class, messageFormatter);
        } catch (IOException e) {
            getLogger().severe("Failed to load message formatter: " + e.getMessage());
        }

        serviceRegistry.register(ThreadUtil.class, new BungeeThreadUtil());
        serviceRegistry.register(PluginManager.class, new BungeePluginManager());

        ProxyServer.getInstance().getPluginManager().registerCommand(this, new PlugManCommandHandler());

        var autoFeatureManager = new BungeeAutoFeatureManager(serviceRegistry);
        autoFeatureManager.setupAutoFeatures();
    }

    public void saveDefaultConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        var file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) try (var in = getResourceAsStream("config.yml")) {
            if (in != null) Files.copy(in, file.toPath());
        } catch (IOException e) {
            getLogger().severe("Failed to create config.yml: " + e.getMessage());
        }

        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Failed to load config.yml", exception);
        }
    }

    @Override
    public void onDisable() {
        instance = null;
        serviceRegistry.clear();
        ClassAccessor.clearCache();
        FieldAccessor.clearCache();
        MethodAccessor.clearCache();
        ProxyServer.getInstance().getPluginManager().unregisterCommands(this);
    }

    /**
     * Setup message files
     */
    private void setupMessageFiles() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        var messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) try (var in = getResourceAsStream("messages.yml")) {
            if (in != null) Files.copy(in, messagesFile.toPath());
        } catch (IOException e) {
            getLogger().severe("Failed to create messages.yml: " + e.getMessage());
        }
    }
}