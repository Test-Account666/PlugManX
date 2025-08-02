package core.com.rylinaux.plugman.file.messaging;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2015 PlugMan
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;

/**
 * Class that allows reading from a YAML file embedded in the JAR.
 *
 * @author rylinaux
 */
@Getter
@RequiredArgsConstructor
public class MessageFile {
    private final YamlConfigurationProvider yamlProvider;
    private YamlConfigurationProvider config = null;

    /**
     * Construct the object.
     *
     * @param file         the file.
     * @param yamlProvider the YAML configuration provider
     */
    public MessageFile(File file, YamlConfigurationProvider yamlProvider) {
        this.yamlProvider = yamlProvider;
        config = yamlProvider.loadConfiguration(file);
    }

    public String getString(String key) {
        return yamlProvider.getString(key, null);
    }
}
