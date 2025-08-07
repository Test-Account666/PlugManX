package bungee.com.rylinaux.plugman.config;

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

import bungee.com.rylinaux.plugman.PlugManBungee;
import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * BungeeCord implementation of YamlConfigurationProvider.
 * Bridges the core configuration interface with BungeeCord's configuration system.
 *
 * @author rylinaux
 */
public class BungeeConfigurationProvider implements YamlConfigurationProvider {
    @Delegate
    private Configuration config;
    private File file;

    public BungeeConfigurationProvider(Configuration config, File file) {
        this.config = config;
        this.file = file;
    }

    @SneakyThrows
    @Override
    public YamlConfigurationProvider loadConfiguration(File file) {
        this.file = file;
        config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

        return this;
    }

    @Override
    public YamlConfigurationSection getConfigurationSection(String path) {
        var section = config.getSection(path);
        if (section == null) return null;

        return new YamlConfigurationSection() {
            @Override
            public Set<String> getKeys(boolean deep) {
                return new HashSet<>(section.getKeys());
            }

            @Override
            public String getName() {
                return path.substring(path.lastIndexOf('.') + 1);
            }
        };
    }

    @Override
    public Object get(String path, Object def) {
        return config.get(path, def);
    }

    @Override
    public boolean isSet(String key) {
        return config.get(key, null) != null;
    }

    @Override
    public void saveDefaultConfig() {
        PlugManBungee.getInstance().saveDefaultConfig();
    }

    @Override
    public void save() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
        } catch (IOException exception) {
            PlugManBungee.getInstance().getLogger().log(Level.SEVERE, "Failed to save configuration file: " + file.getName(), exception);
        }
    }

    @Override
    public File getDataFolder() {
        return PlugManBungee.getInstance().getDataFolder();
    }
}