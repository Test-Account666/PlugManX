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

import bukkit.com.rylinaux.plugman.commands.PlugManCommandHandler;
import bukkit.com.rylinaux.plugman.commands.PlugManTabCompleter;
import bukkit.com.rylinaux.plugman.config.BukkitConfigurationProvider;
import bukkit.com.rylinaux.plugman.messaging.BukkitColorFormatter;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import core.com.rylinaux.plugman.file.messaging.MessageFormatter;
import core.com.rylinaux.plugman.plugins.PluginManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Handles initialization logic for PlugMan including plugin manager setup, message files, and commands.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class PlugManInitializer {

    private final PlugMan plugin;

    /**
     * Initialize the plugin manager based on server type
     */
    public PluginManager initializePluginManager() {
        return new BukkitPluginManager();
    }

    /**
     * Setup message files for different languages
     */
    public void setupMessageFiles() {
        var messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", true);

        setupLanguageFiles();
    }

    /**
     * Setup language-specific message files
     */
    private void setupLanguageFiles() {
        var languageFiles = new String[]{"messages_jp.yml", "messages_de.yml", "messages_es.yml"};

        for (var languageFile : languageFiles) {
            if (new File(plugin.getDataFolder(), languageFile).exists()) continue;
            plugin.saveResource(languageFile, true);
        }
    }

    /**
     * Setup message formatter
     */
    public MessageFormatter setupMessageFormatter() {
        var config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        return new MessageFormatter(new BukkitConfigurationProvider(config), new BukkitColorFormatter());
    }

    /**
     * Setup command handlers and tab completers
     */
    public void setupCommands() {
        plugin.commandCreator.registerCommand("plugman", new PlugManCommandHandler(), new PlugManTabCompleter(), "plm");
    }
}