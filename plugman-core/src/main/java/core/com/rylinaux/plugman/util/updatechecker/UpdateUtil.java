package core.com.rylinaux.plugman.util.updatechecker;

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

import com.google.gson.JsonArray;
import core.com.rylinaux.plugman.config.model.ResourceMappingsConfig;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.pojo.UpdateResult;
import core.com.rylinaux.plugman.util.CollectionUtil;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class UpdateUtil {
    private static final Pattern VERSION_FAMILY_NUMBERS_PATTERN = Pattern.compile("\\d+(\\.\\d+)*");

    /**
     * Check which plugins are up-to-date or not.
     *
     * @return a map of the plugins and the results.
     */
    public static Map<String, UpdateResult> checkUpToDate(PluginManager pluginManager, ResourceMappingsConfig resourceMappings) {
        var results = new TreeMap<String, UpdateResult>();
        for (var plugin : pluginManager.getPlugins()) results.put(plugin.getName(), checkUpToDate(plugin.getName(), pluginManager, resourceMappings));
        return results;
    }

    /**
     * Check if the installed plugin version is up-to-date with the Spigot version.
     *
     * @param pluginName the plugin name.
     * @return the reflective UpdateResult.
     */
    public static UpdateResult checkUpToDate(String pluginName, PluginManager pluginManager, ResourceMappingsConfig resourceMappings) {
        if (resourceMappings != null && resourceMappings.getResources() != null) {
            var resourceInfo = resourceMappings.getResources().get(pluginName.toLowerCase(Locale.ROOT));
            if (resourceInfo != null && resourceInfo.getId() != null && resourceInfo.getSpigotmc() != null)
                if (resourceInfo.getSpigotmc()) return SpiGetUtil.checkUpToDate(pluginName, resourceInfo.getId(), pluginManager);
                else return CurseForgeUtil.checkUpToDate(pluginName, resourceInfo.getId(), pluginManager);
        }

        var id = SpiGetUtil.getPluginId(pluginName);
        if (id < 0) {
            id = CurseForgeUtil.getPluginId(pluginName);
            if (id < 0) {
                var plugin = pluginManager.getPluginByName(pluginName);
                if (plugin == null) return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, pluginName);
                return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, plugin.getVersion());
            }
            return CurseForgeUtil.checkUpToDate(pluginName, null, pluginManager);
        }
        return SpiGetUtil.checkUpToDate(pluginName, null, pluginManager);
    }

    /**
     * Get the id of the plugin.
     *
     * @param name the name of the plugin.
     * @return the id of the plugin.
     */
    public static long getPluginId(String name, ResourceMappingsConfig resourceMappings) {
        if (resourceMappings != null && resourceMappings.getResources() != null) {
            var resourceInfo = resourceMappings.getResources().get(name.toLowerCase(Locale.ROOT));
            if (resourceInfo != null && resourceInfo.getId() != null && resourceInfo.getSpigotmc() != null)
                if (resourceInfo.getSpigotmc()) return SpiGetUtil.getPluginId(name);
                else return CurseForgeUtil.getPluginId(name);
        }

        var id = SpiGetUtil.getPluginId(name);
        if (id < 0) id = CurseForgeUtil.getPluginId(name);
        return id;

    }

    /**
     * Get the versions for a given plugin.
     *
     * @param id the plugin id.
     * @return the JSON encoded data.
     */
    public static JsonArray getPluginVersions(long id, ResourceMappingsConfig resourceMappings) {
        if (resourceMappings != null && resourceMappings.getResources() != null) for (var resourceInfo : resourceMappings.getResources().values()) {
            if (resourceInfo == null || resourceInfo.getId() == null || !resourceInfo.getId().equals(id)) continue;
            if (resourceInfo.getSpigotmc() != null && resourceInfo.getSpigotmc()) return SpiGetUtil.getPluginVersions(id);
            else return CurseForgeUtil.getPluginVersions(id);
        }

        var jsonArray = SpiGetUtil.getPluginVersions(id);
        if (jsonArray == null || jsonArray.isEmpty()) return CurseForgeUtil.getPluginVersions(id);
        return jsonArray;
    }

    protected static Boolean isActualVersion(String current, String latest) {
        if (current.equalsIgnoreCase(latest)) return true; // Strings are fully equals

        List<List<Integer>> currentNumbers;
        List<List<Integer>> latestNumbers;

        try {
            currentNumbers = parseNumbers(VERSION_FAMILY_NUMBERS_PATTERN.matcher(current));
            latestNumbers = parseNumbers(VERSION_FAMILY_NUMBERS_PATTERN.matcher(latest));
        } catch (NumberFormatException ex) {
            return null; // Unable to parse numbers to int
        }

        for (var familyIndex = 0; familyIndex < CollectionUtil.maxCollectionsSize(currentNumbers, latestNumbers); familyIndex++) {
            var currentFamily = CollectionUtil.getElementOrDefault(currentNumbers, familyIndex, ArrayList::new);
            var latestFamily = CollectionUtil.getElementOrDefault(latestNumbers, familyIndex, ArrayList::new);

            for (var numberIndex = 0; numberIndex < CollectionUtil.maxCollectionsSize(currentFamily, latestFamily); numberIndex++) {
                var currentValue = CollectionUtil.getElementOrDefault(currentFamily, numberIndex, () -> 0);
                var latestValue = CollectionUtil.getElementOrDefault(latestFamily, numberIndex, () -> 0);

                if (latestValue > currentValue) return false;
                else if (latestValue < currentValue) return true;
            }
        }
        return true; // Numbers amount equals, numbers values too
    }

    private static List<List<Integer>> parseNumbers(Matcher matcher) {
        var result = new ArrayList<List<Integer>>();
        while (matcher.find()) {
            var familyString = matcher.group();
            var family = Arrays.stream(familyString.split("\\.")).map(Integer::parseInt).collect(Collectors.toList());
            result.add(family);
        }
        return result;
    }
}
