package bukkit.com.rylinaux.plugman;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
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

import bukkit.com.rylinaux.plugman.commands.CommandCreator;
import bukkit.com.rylinaux.plugman.commands.PlugManCommandHandler;
import bukkit.com.rylinaux.plugman.commands.PlugManTabCompleter;
import bukkit.com.rylinaux.plugman.logging.BukkitPluginLogger;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.file.PlugManFileManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

/**
 * Plugin manager for Bukkit servers.
 *
 * @author rylinaux
 */
@SuppressWarnings("JavadocDeclaration")
public class PlugManBukkit extends JavaPlugin {

    @Getter
    private static PlugManBukkit instance = null;
    @ApiStatus.Internal
    public CommandCreator commandCreator;
    @Getter
    @Delegate
    @ApiStatus.Internal
    public ServiceRegistry serviceRegistry;
    @ApiStatus.Internal
    public Runnable hook = () -> {
    };
    @Delegate
    private PlugManFileManager fileManager;


    @Override
    public void onEnable() {
        PlugManBukkit.instance = this;

        saveDefaultConfig();

        if (commandCreator == null) commandCreator = new CommandCreator();

        serviceRegistry = new ServiceRegistry();
        var logger = new BukkitPluginLogger(getLogger());
        var initializer = new BukkitPlugManInitializer(this, serviceRegistry, logger);
        fileManager = new PlugManFileManager(logger);

        initializer.initializeCoreServices();
        initializer.setupMessaging();

        serviceRegistry.register(PlugManFileManager.class, fileManager);
        commandCreator.registerCommand("plugman", new PlugManCommandHandler(), new PlugManTabCompleter(), "plm");

        // Initialize configuration and scan plugins
        var configurationManager = serviceRegistry.get(PlugManConfigurationManager.class);
        configurationManager.initializeConfiguration();
        fileManager.scanExistingPlugins();

        initializer.setupAutoFeatures();

        hook.run();
    }


    @Override
    public void onDisable() {
        PlugManBukkit.instance = null;
        var logger = new BukkitPluginLogger(getLogger());
        var initializer = new BukkitPlugManInitializer(this, serviceRegistry, logger);
        initializer.cleanup();
    }
}
