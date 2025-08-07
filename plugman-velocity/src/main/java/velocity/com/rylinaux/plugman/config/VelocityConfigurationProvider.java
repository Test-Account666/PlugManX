package velocity.com.rylinaux.plugman.config;

/*
 * #%L
 * PlugManVelocity
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

import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import velocity.com.rylinaux.plugman.PlugManVelocity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Velocity implementation of YamlConfigurationProvider.
 * Bridges the core configuration interface with Velocity's Path-based configuration system.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class VelocityConfigurationProvider implements YamlConfigurationProvider {
    private final Path configPath;

    //TODO: Actually implement this .-.

    @SneakyThrows
    @Override
    public YamlConfigurationProvider loadConfiguration(File file) {
        return this;
    }

    @Override
    public Object get(String path, Object def) {
        return def;
    }

    @Override
    public String getString(String path, String def) {
        var value = get(path, def);
        if (value instanceof String stringValue) return stringValue;
        return def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        var value = get(path, def);
        if (value instanceof Boolean boolValue) return boolValue;
        return def;
    }

    @Override
    public int getInt(String path, int def) {
        var value = get(path, def);
        if (value instanceof Number numberValue) return numberValue.intValue();
        return def;
    }

    @Override
    public long getLong(String path, long def) {
        var value = get(path, def);
        if (value instanceof Long longValue) return longValue;
        return def;
    }

    @Override
    public List<String> getStringList(String path) {
        var value = get(path);
        if (value instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return Collections.emptyList();
    }

    @Override
    public boolean contains(String path) {
        return false;
    }

    @Override
    public YamlConfigurationSection getConfigurationSection(String path) {
        return null;
    }

    @Override
    public boolean isSet(String key) {
        return get(key, null) != null;
    }

    @Override
    public void set(String key, Object value) {
    }

    @Override
    public void saveDefaultConfig() {
        if (Files.exists(configPath)) return;

        try {
            Files.createDirectories(configPath.getParent());
            try (var inputStream = getClass().getResourceAsStream("/config.yml")) {
                if (inputStream != null) Files.copy(inputStream, configPath);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void save() {
    }

    @Override
    public File getDataFolder() {
        return PlugManVelocity.getInstance().getDataDirectory().toFile();
    }
}