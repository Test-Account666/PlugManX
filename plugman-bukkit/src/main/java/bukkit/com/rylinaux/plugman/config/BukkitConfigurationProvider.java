package bukkit.com.rylinaux.plugman.config;

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

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Bukkit implementation of ConfigurationProvider.
 * Bridges the core configuration interface with Bukkit's configuration system.
 *
 * @author rylinaux
 */
@AllArgsConstructor
public class BukkitConfigurationProvider implements YamlConfigurationProvider {
    @Delegate
    private final FileConfiguration config;
    private File file;

    @SneakyThrows
    @Override
    public YamlConfigurationProvider loadConfiguration(File file) {
        this.file = file;
        config.load(file);
        return this;
    }

    @Override
    public YamlConfigurationSection getConfigurationSection(String path) {
        var section = config.getConfigurationSection(path);

        return new YamlConfigurationSection() {
            @Override
            public Set<String> getKeys(boolean deep) {
                return section.getKeys(deep);
            }

            @Override
            public String getName() {
                return section.getName();
            }
        };
    }

    @Override
    public void saveDefaultConfig() {
        PlugManBukkit.getInstance().saveDefaultConfig();
    }

    @Override
    public void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}