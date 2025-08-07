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

import bungee.com.rylinaux.plugman.commands.PlugManCommandHandler;
import bungee.com.rylinaux.plugman.logging.BungeePluginLogger;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import lombok.Getter;
import lombok.experimental.Delegate;
import manifold.rt.api.NoBootstrap;
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
@NoBootstrap
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
        var logger = new BungeePluginLogger(getLogger());
        var initializer = new BungeePlugManInitializer(this, serviceRegistry, logger);

        initializer.initializeCoreServices();
        initializer.setupMessaging();

        ProxyServer.getInstance().getPluginManager().registerCommand(this, new PlugManCommandHandler());

        initializer.setupAutoFeatures();
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
        var logger = new BungeePluginLogger(getLogger());
        var initializer = new BungeePlugManInitializer(this, serviceRegistry, logger);
        initializer.cleanup();
        ProxyServer.getInstance().getPluginManager().unregisterCommands(this);
    }
}