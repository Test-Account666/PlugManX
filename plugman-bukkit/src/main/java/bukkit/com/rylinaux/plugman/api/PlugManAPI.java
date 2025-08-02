package bukkit.com.rylinaux.plugman.api;

/*-
 * #%L
 * PlugManX Core
 * %%
 * Copyright (C) 2010 - 2025 plugmanx-core
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

import bukkit.com.rylinaux.plugman.PlugMan;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;

public class PlugManAPI {
    protected static final HashMap<Plugin, GentleUnload> gentleUnloads = new HashMap<>();

    /**
     * @return = Returns all plugins which should be unloaded gently
     */
    public static HashMap<Plugin, GentleUnload> getGentleUnloads() {
        return new HashMap<>(PlugManAPI.gentleUnloads);
    }

    /**
     * A gentle unload is when a plugin wants to unload itself, so PlugMan doesn't break stuff
     *
     * @param plugin       = Plugin which wants a gentle unload
     * @param gentleUnload = The class which will handle the gentle unload
     * @return = If the plugin is allowed to be unloaded gently
     */
    public static boolean pleaseAddMeToGentleUnload(Plugin plugin, GentleUnload gentleUnload) {
        if (plugin == null) return false;
        if (gentleUnload == null) return false;

        if (PlugManAPI.gentleUnloads.containsKey(plugin)) return false;

        PlugManAPI.gentleUnloads.put(plugin, gentleUnload);
        return true;
    }

    /**
     * If you don't want your plugin to be un-/reloaded, you can use this method to add it to the list of ignored plugins
     *
     * @param plugin = The plugin that should be ignored
     * @return = Whether the method was executed successfully
     */
    public static boolean iDoNotWantToBeUnOrReloaded(String plugin) {
        if (PlugMan.getInstance() == null) return false;

        var config = PlugMan.getInstance().<PlugManConfigurationManager>get(PlugManConfigurationManager.class);

        if (config.getIgnoredPlugins() == null) return false;

        config.getIgnoredPlugins().add(plugin);
        return true;
    }
}
