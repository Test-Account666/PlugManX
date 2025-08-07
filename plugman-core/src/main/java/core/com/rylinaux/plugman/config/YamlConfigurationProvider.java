package core.com.rylinaux.plugman.config;

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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Platform-agnostic interface for YAML configuration file operations.
 * This abstracts the underlying YAML configuration system.
 *
 * @author rylinaux
 */
public interface YamlConfigurationProvider {

    /**
     * Load a YAML configuration from a file
     *
     * @param file the file to load from
     * @return the loaded configuration
     */
    YamlConfigurationProvider loadConfiguration(File file);

    /**
     * Get a configuration section
     *
     * @param path the path to the section
     * @return the configuration section, or null if not found
     */
    YamlConfigurationSection getConfigurationSection(String path);

    default Object get(String path) {
        return get(path, null);
    }

    Object get(String path, Object def);

    default String getString(String path) {
        return getString(path, null);
    }

    /**
     * Get a string value from the configuration
     *
     * @param path         the path to the value
     * @param defaultValue the default value if not found
     * @return the string value
     */
    String getString(String path, String defaultValue);

    default int getInt(String path) {
        return getInt(path, 0);
    }

    /**
     * Get an integer value from the configuration
     *
     * @param path         the path to the value
     * @param defaultValue the default value if not found
     * @return the integer value
     */
    int getInt(String path, int defaultValue);

    default long getLong(String path) {
        return getLong(path, -1L);
    }

    /**
     * Get a long value from the configuration
     *
     * @param path         the path to the value
     * @param defaultValue the default value if not found
     * @return the long value
     */
    long getLong(String path, long defaultValue);

    default boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    /**
     * Get a boolean value from the configuration
     *
     * @param path         the path to the value
     * @param defaultValue the default value if not found
     * @return the boolean value
     */
    boolean getBoolean(String path, boolean defaultValue);

    /**
     * Get a string list from the configuration
     *
     * @param path the path to the value
     * @return the string list
     */
    List<String> getStringList(String path);

    /**
     * Check if a path exists in the configuration
     *
     * @param path the path to check
     * @return true if the path exists
     */
    boolean contains(String path);

    boolean isSet(String key);

    void set(String key, Object value);

    void saveDefaultConfig();

    void save();

    default File getDataFolder() {
        return Path.of("plugins", "PlugManX").toFile();
    }

    /**
     * Interface for configuration sections
     */
    interface YamlConfigurationSection {
        /**
         * Get all keys in this section
         *
         * @param deep whether to get keys recursively
         * @return the set of keys
         */
        Set<String> getKeys(boolean deep);

        String getName();
    }
}