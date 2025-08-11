package core.com.rylinaux.plugman.initialization;

/*
 * #%L
 * PlugManCore
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
import core.com.rylinaux.plugman.logging.PluginLogger;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.services.ServiceRegistry;
import core.com.rylinaux.plugman.util.ThreadUtil;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import core.com.rylinaux.plugman.util.reflection.FieldAccessor;
import core.com.rylinaux.plugman.util.reflection.MethodAccessor;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base initializer class that provides common initialization logic for PlugMan implementations.
 * This class extracts duplicated logic between different platform implementations.
 *
 * @author rylinaux
 */
public abstract class BasePlugManInitializer {
    public static final String[] LANGUAGES = {"cn", "de", "es", "jp", "tw", "ru"};
    protected final ServiceRegistry serviceRegistry;
    protected final PluginLogger logger;
    @Getter
    protected final File dataFolder = Path.of("plugins", "PlugManX").toFile();

    protected BasePlugManInitializer(ServiceRegistry serviceRegistry, PluginLogger logger) {
        this.serviceRegistry = serviceRegistry;
        this.logger = logger;
    }

    /**
     * Initialize core services in the service registry
     */
    public void initializeCoreServices() {
        serviceRegistry.register(PluginLogger.class, logger);

        var configurationManager = createConfigurationManager();
        serviceRegistry.register(PlugManConfigurationManager.class, configurationManager);

        var pluginManager = createPluginManager();
        serviceRegistry.register(PluginManager.class, pluginManager);

        var threadUtil = createThreadUtil();
        serviceRegistry.register(ThreadUtil.class, threadUtil);
    }

    /**
     * Setup message files and formatter
     */
    public void setupMessaging() {
        setupMessageFiles();

        try {
            var messageFormatter = createMessageFormatter();
            serviceRegistry.register(MessageFormatter.class, messageFormatter);
        } catch (IOException e) {
            logger.severe("Failed to load message formatter: " + e.getMessage());
        }
    }

    /**
     * Setup auto features
     */
    public void setupAutoFeatures() {
        var autoFeatureManager = createAutoFeatureManager();
        autoFeatureManager.setupAutoFeatures();
    }

    /**
     * Cleanup resources and clear caches
     */
    public void cleanup() {
        serviceRegistry.clear();
        ClassAccessor.clearCache();
        FieldAccessor.clearCache();
        MethodAccessor.clearCache();
    }

    /**
     * Setup message files in the data folder
     */
    protected void setupMessageFiles() {
        if (!dataFolder.exists()) dataFolder.mkdirs();

        var messagesFile = new File(dataFolder, "messages.yml");
        if (!messagesFile.exists()) try (var in = getResourceAsStream("messages.yml")) {
            if (in != null) Files.copy(in, messagesFile.toPath());
        } catch (IOException exception) {
            logger.severe("Failed to create messages.yml: ", exception);
        }

        var messagesDir = dataFolder.toPath().resolve("messages").toFile();
        if (!messagesDir.exists()) messagesDir.mkdirs();

        for (var language : LANGUAGES) {
            var fileName = "messages_" + language + ".yml";

            messagesFile = new File(messagesDir, fileName);
            if (!messagesFile.exists()) try (var in = getResourceAsStream(fileName)) {
                if (in != null) Files.copy(in, messagesFile.toPath());
            } catch (IOException exception) {
                logger.severe("Failed to create " + fileName, exception);
            }
        }
    }

    protected InputStream getResourceAsStream(String resource) {
        return getClass().getClassLoader().getResourceAsStream(resource);
    }

    protected abstract PlugManConfigurationManager createConfigurationManager();

    protected abstract PluginManager createPluginManager();

    protected abstract ThreadUtil createThreadUtil();

    protected abstract MessageFormatter createMessageFormatter() throws IOException;

    protected abstract AutoFeatureManager createAutoFeatureManager();
}