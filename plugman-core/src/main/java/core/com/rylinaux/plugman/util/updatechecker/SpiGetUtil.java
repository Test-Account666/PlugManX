package core.com.rylinaux.plugman.util.updatechecker;

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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.pojo.UpdateResult;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utilities for dealing with the SpiGet API.
 *
 * @author rylinaux
 */
public class SpiGetUtil {

    /**
     * The base URL for the SpiGet API.
     */
    public static final String API_BASE_URL = "https://api.spiget.org/v2/";

    /**
     * Check which plugins are up-to-date or not.
     *
     * @return a map of the plugins and the results.
     */
    public static Map<String, UpdateResult> checkUpToDate(PluginManager pluginManager) {
        var results = new TreeMap<String, UpdateResult>();
        for (var plugin : pluginManager.getPlugins()) results.put(plugin.getName(), checkUpToDate(plugin.getName(), pluginManager));
        return results;
    }

    /**
     * Check if the installed plugin version is up-to-date with the Spigot version.
     *
     * @param pluginName the plugin name.
     * @return the reflective UpdateResult.
     */
    public static UpdateResult checkUpToDate(String pluginName, PluginManager pluginManager) {
        return checkUpToDate(pluginName, null, pluginManager);
    }

    /**
     * Check if the installed plugin version is up-to-date with the Spigot version.
     *
     * @param pluginName the plugin name.
     * @return the reflective UpdateResult.
     */
    public static UpdateResult checkUpToDate(String pluginName, Long pluginId, PluginManager pluginManager) {
        var idSpecified = pluginId != null;

        if (!idSpecified)
            pluginId = SpiGetUtil.getPluginId(pluginName);

        if (pluginId < 0) {
            var plugin = pluginManager.getPluginByName(pluginName);
            if (plugin == null)
                if (idSpecified) return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, pluginName);
                else return new UpdateResult(UpdateResult.ResultType.NOT_INSTALLED);
            return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, plugin.getVersion());
        }

        var versions = SpiGetUtil.getPluginVersions(pluginId);

        if (versions == null || versions.isEmpty()) {
            var plugin = pluginManager.getPluginByName(pluginName);
            if (plugin == null)
                if (idSpecified) return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, pluginName);
                else return new UpdateResult(UpdateResult.ResultType.NOT_INSTALLED);
            return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, plugin.getVersion());
        }

        var latest = versions.get(0).getAsJsonObject();

        var plugin = pluginManager.getPluginByName(pluginName);
        if (plugin == null) if (idSpecified) return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, "null");
        else return new UpdateResult(UpdateResult.ResultType.NOT_INSTALLED, "null", "null");

        var currentVersion = plugin.getVersion();
        var latestVersion = latest.get("name").getAsString();

        if (currentVersion == null) return new UpdateResult(UpdateResult.ResultType.NOT_INSTALLED, currentVersion, latestVersion);
        else if (latestVersion == null) return new UpdateResult(UpdateResult.ResultType.INVALID_PLUGIN, currentVersion, latestVersion);

        var isActual = UpdateUtil.isActualVersion(currentVersion, latestVersion);
        if (isActual != null && isActual) return new UpdateResult(UpdateResult.ResultType.UP_TO_DATE, currentVersion, latestVersion);
        else return new UpdateResult(UpdateResult.ResultType.OUT_OF_DATE, currentVersion, latestVersion);

    }

    /**
     * Get the id of the plugin.
     *
     * @param name the name of the plugin.
     * @return the id of the plugin.
     */
    public static long getPluginId(String name) {
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "search/resources/" + name + "?field=name&fields=id%2Cname"))
                    .header("User-Agent", "PlugMan")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                var body = response.body();

                var gson = new Gson();
                var object = gson.fromJson(body, JsonElement.class);

                if (object.isJsonArray()) {
                    var array = object.getAsJsonArray();

                    for (var o : array) {
                        var json = o.getAsJsonObject();
                        var pluginName = json.get("name").getAsString();
                        if (name.equalsIgnoreCase(pluginName))
                            return json.get("id").getAsLong();
                    }
                }

            } catch (IOException | InterruptedException exception) {
                exception.printStackTrace();
            }
        }

        return -1;
    }

    /**
     * Get the versions for a given plugin.
     *
     * @param id the plugin id.
     * @return the JSON encoded data.
     */
    @SneakyThrows
    public static JsonArray getPluginVersions(long id) {
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "resources/" + id + "/versions?sort=-releaseDate"))
                    .header("User-Agent", "PlugMan")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                var body = response.body();

                var gson = new Gson();
                return gson.fromJson(body, JsonArray.class);

            } catch (IOException | InterruptedException exception) {
                exception.printStackTrace();
            }
        }

        return null;
    }
}
