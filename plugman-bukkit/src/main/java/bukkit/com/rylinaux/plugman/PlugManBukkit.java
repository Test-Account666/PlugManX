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
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import lombok.Getter;
import lombok.experimental.Delegate;
import manifold.rt.api.NoBootstrap;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Plugin manager for Bukkit servers.
 *
 * @author rylinaux
 */
@SuppressWarnings("JavadocDeclaration")
@NoBootstrap
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

    private static InputStream getResourceStatic(String filename) {
        try {
            var url = PlugManBukkit.class.getClassLoader().getResource(filename);
            if (url == null) return null;
            else {
                var connection = url.openConnection();
                connection.setUseCaches(false);
                return connection.getInputStream();
            }
        } catch (IOException exception) {
            return null;
        }
    }

    public static void saveResourceStatic(String resourcePath, boolean replace) {
        var dataFolder = new File("plugins");
        if (resourcePath != null && !resourcePath.isEmpty()) {
            resourcePath = resourcePath.replace('\\', '/');
            var in = PlugManBukkit.getResourceStatic(resourcePath);
            if (in == null) throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in PlugManX");
            else {
                var outFile = new File(dataFolder, resourcePath);
                var lastIndex = resourcePath.lastIndexOf(47);
                var outDir = new File(dataFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));
                if (!outDir.exists()) outDir.mkdirs();

                try {
                    var out = new FileOutputStream(outFile);
                    var buf = new byte[1024];

                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

                    out.close();
                    in.close();
                } catch (IOException exception) {
                    if (instance != null) {
                        instance.getLogger().severe("Could not save " + outFile.getName() + " to " + outFile + ": " + exception.getMessage());
                    } else {
                        // Fallback when instance is not available
                        java.util.logging.Logger.getLogger("PlugManX").severe("Could not save " + outFile.getName() + " to " + outFile + ": " + exception.getMessage());
                    }
                }

            }
        } else throw new IllegalArgumentException("ResourcePath cannot be null or empty");
    }

    @Deprecated(
            forRemoval = true,
            since = "2.5.0"
    )
    public PluginManager getPluginUtil() {
        return serviceRegistry.getPluginManager();
    }


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
