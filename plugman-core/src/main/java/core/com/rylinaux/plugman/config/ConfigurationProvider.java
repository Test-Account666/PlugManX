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
import java.util.List;

/**
 * Platform-agnostic interface for configuration operations.
 * This abstracts the underlying configuration system (Bukkit, Bungee, etc.)
 *
 * @author rylinaux
 */
public interface ConfigurationProvider {

    /**
     * Check if a configuration key is set
     *
     * @param key the configuration key
     * @return true if the key is set
     */
    boolean isSet(String key);

    /**
     * Get an integer value from configuration
     *
     * @param key the configuration key
     * @param defaultValue the default value if key is not found
     * @return the integer value
     */
    int getInt(String key, int defaultValue);

    /**
     * Get a boolean value from configuration
     *
     * @param key the configuration key
     * @param defaultValue the default value if key is not found
     * @return the boolean value
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Get a string list from configuration
     *
     * @param key the configuration key
     * @return the string list
     */
    List<String> getStringList(String key);

    /**
     * Set a configuration value
     *
     * @param key the configuration key
     * @param value the value to set
     */
    void set(String key, Object value);

    /**
     * Save the configuration to disk
     */
    void saveConfig();

    /**
     * Reload the configuration from disk
     */
    void reloadConfig();

    /**
     * Save the default configuration if it doesn't exist
     */
    void saveDefaultConfig();

    /**
     * Get the plugin's data folder
     *
     * @return the data folder
     */
    File getDataFolder();

    /**
     * Save a resource from the plugin jar to the data folder
     *
     * @param resourcePath the path to the resource in the jar
     * @param replace whether to replace existing files
     */
    void saveResource(String resourcePath, boolean replace);
}