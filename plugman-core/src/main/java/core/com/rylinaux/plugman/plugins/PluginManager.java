package core.com.rylinaux.plugman.plugins;

/*-
 * #%L
 * PlugManX Core
 * %%
 * Copyright (C) 2010 - 2025 plugman-core
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

import core.com.rylinaux.plugman.PluginResult;

import java.util.List;
import java.util.Set;

public interface PluginManager {

    /**
     * Enable a plugin.
     *
     * @param plugin the plugin to enable
     */
    PluginResult enable(Plugin plugin);

    /**
     * Enable all plugins.
     */
    PluginResult enableAll();

    /**
     * Disable a plugin.
     *
     * @param plugin the plugin to disable
     */
    PluginResult disable(Plugin plugin);

    /**
     * Disable all plugins.
     */
    PluginResult disableAll();

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin the plugin to format
     * @return the formatted name
     */
    String getFormattedName(Plugin plugin);

    /**
     * Returns the formatted name of the plugin.
     *
     * @param plugin          the plugin to format
     * @param includeVersions whether to include the version
     * @return the formatted name
     */
    String getFormattedName(Plugin plugin, boolean includeVersions);

    /**
     * Returns a plugin from an array of Strings.
     *
     * @param args  the array
     * @param start the index to start at
     * @return the plugin
     */
    Plugin getPluginByName(String[] args, int start);

    /**
     * Returns a plugin from a String.
     *
     * @param name the name of the plugin
     * @return the plugin
     */
    Plugin getPluginByName(String name);

    /**
     * Returns a List of plugin names.
     *
     * @return list of plugin names
     */
    List<String> getPluginNames(boolean fullName);

    /**
     * Returns a List of disabled plugin names.
     *
     * @return list of disabled plugin names
     */
    List<String> getDisabledPluginNames(boolean fullName);

    /**
     * Returns a List of enabled plugin names.
     *
     * @return list of enabled plugin names
     */
    List<String> getEnabledPluginNames(boolean fullName);

    /**
     * Get the version of another plugin.
     *
     * @param name the name of the other plugin.
     * @return the version.
     */
    String getPluginVersion(String name);

    /**
     * Returns the commands a plugin has registered.
     *
     * @param plugin the plugin to deal with
     * @return the commands registered
     */
    String getUsages(Plugin plugin);

    /**
     * Find which plugin has a given command registered.
     *
     * @param command the command.
     * @return the plugin.
     */
    List<String> findByCommand(String command);

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    boolean isIgnored(Plugin plugin);

    /**
     * Checks whether the plugin is ignored.
     *
     * @param plugin the plugin to check
     * @return whether the plugin is ignored
     */
    boolean isIgnored(String plugin);

    /**
     * Loads and enables a plugin.
     *
     * @param name plugin's name
     * @return status message
     * @throws RuntimeException if command map or known commands field cannot be found
     */
    PluginResult load(String name);

    CommandMapWrap<?> getKnownCommands();

    /**
     * Loads and enables a plugin.
     *
     * @param plugin plugin to load
     * @return status message
     */
    default PluginResult load(Plugin plugin) {
        return load(plugin.getName());
    }

    /**
     * Unload a plugin.
     *
     * @param plugin the plugin to unload
     * @return the message to send to the user.
     */
    PluginResult unload(Plugin plugin);

    /**
     * Returns if the plugin is a Paper plugin.
     *
     * @param plugin the plugin to check
     * @return if the plugin is a Paper plugin
     */
    boolean isPaperPlugin(Plugin plugin);

    Set<Plugin> getPlugins();
}
