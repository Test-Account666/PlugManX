package core.com.rylinaux.plugman.file;

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

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

/**
 * Manages file operations for PlugMan including plugin scanning, file tracking, and hash calculation.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class PlugManFileManager {

    private final PluginLogger logger;

    /**
     * Stores all file names + hashes for auto (re/un)load
     */
    private final Map<String, String> fileHashMap = new HashMap<>();

    /**
     * Stores all file names + plugin names for auto unload
     */
    @Getter
    private final Map<String, String> filePluginMap = new HashMap<>();

    public void scanExistingPlugins() {
        for (var file : new File("plugins").listFiles()) {
            if (file.isDirectory()) continue;
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;

            trackFile(file);
        }
    }

    public void trackFile(File file) {
        var hash = calculateFileHash(file);
        if (hash != null) fileHashMap.put(file.getName(), hash);

        var descriptionFile = getPluginDescription(file);
        if (descriptionFile != null) filePluginMap.put(file.getName(), descriptionFile.name());
    }

    public void untrackFile(String fileName) {
        fileHashMap.remove(fileName);
        filePluginMap.remove(fileName);
    }

    public boolean isFileTracked(String fileName) {
        return fileHashMap.containsKey(fileName);
    }

    public Set<String> getTrackedFileNames() {
        return new HashSet<>(fileHashMap.keySet());
    }

    public String getPluginNameForFile(String fileName) {
        return filePluginMap.get(fileName);
    }

    public boolean hasFileChanged(File file) {
        var currentHash = calculateFileHash(file);
        if (currentHash == null) return false;

        var storedHash = fileHashMap.get(file.getName());
        return storedHash != null && !currentHash.equalsIgnoreCase(storedHash);
    }

    public void updateFileHash(File file) {
        var hash = calculateFileHash(file);
        if (hash != null) fileHashMap.put(file.getName(), hash);
    }

    private String calculateFileHash(File file) {
        try {
            return Files.asByteSource(file).hash(Hashing.sha256()).toString();
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private PluginDescriptor getPluginDescription(File file) {
        try (var jarFile = new JarFile(file)) {
            if (jarFile.getEntry("plugin.yml") == null) return null;

            try (var stream = jarFile.getInputStream(jarFile.getEntry("plugin.yml"))) {
                if (stream == null) return null;
                return PluginDescriptor.fromInputStream(stream);
            }
        } catch (IOException exception) {
            if (exception instanceof ZipException) logger.info("Possible broken plugin detected: " + file.getName());
            else logger.severe("Error reading plugin description: " + exception.getMessage());
            return null;
        }
    }

    public List<File> getJarFiles() {
        var pluginsDir = new File("plugins");
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) return Collections.emptyList();

        return Arrays.stream(pluginsDir.listFiles())
                .filter(File::isFile)
                .filter(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .collect(Collectors.toList());
    }
}