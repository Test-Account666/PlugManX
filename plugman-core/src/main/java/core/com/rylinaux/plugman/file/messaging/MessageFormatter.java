package core.com.rylinaux.plugman.file.messaging;

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

import core.com.rylinaux.plugman.config.YamlConfigurationProvider;
import core.com.rylinaux.plugman.messaging.ColorFormatter;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages custom messages.
 *
 * @author rylinaux
 */
@Getter
public class MessageFormatter {
    private final MessageFile messageFile;
    private final ColorFormatter colorFormatter;
    private final Map<String, String> defaultMessages;

    /**
     * Construct our object.
     *
     * @param yamlProvider   the YAML configuration provider
     * @param colorFormatter the color formatter
     */
    public MessageFormatter(YamlConfigurationProvider yamlProvider, ColorFormatter colorFormatter) {
        messageFile = new MessageFile(Path.of("plugins", "PlugManX", "messages.yml").toFile(), yamlProvider);
        this.colorFormatter = colorFormatter;
        defaultMessages = loadDefaultMessages();
    }

    /**
     * Returns the formatted version of the message.
     *
     * @param key  the key
     * @param args the args to replace
     * @return the formatted String
     */
    public String formatMessage(String key, Object... args) {
        return formatMessage(true, key, args);
    }

    public void reloadMessages() {
        messageFile.reload();
    }

    /**
     * Returns the formatted version of the message.
     *
     * @param prefix whether to prepend with the plugin's prefix
     * @param key    the key
     * @param args   the args to replace
     * @return the formatted String
     */
    public String formatMessage(boolean prefix, String key, Object... args) {
        var rawMessage = getMessage(key);
        if (rawMessage == null) return "Error: '" + key + "' not found in messages.yml";

        var message = prefix ? getPrefix() + rawMessage : rawMessage;

        for (var i = 0; i < args.length; i++) message = message.replace("{" + i + "}", String.valueOf(args[i]));
        return colorFormatter.translateAlternateColorCodes('&', message);
    }

    private String getMessage(String key) {
        var message = messageFile.getString(key);
        return message == null ? defaultMessages.get(key) : message;
    }

    private String getPrefix() {
        var prefix = getMessage("prefix");
        return prefix == null ? "" : prefix;
    }

    private Map<String, String> loadDefaultMessages() {
        var messages = new LinkedHashMap<String, String>();
        try (var input = getClass().getClassLoader().getResourceAsStream("messages.yml")) {
            if (input == null) return messages;

            var yamlData = new Yaml().load(new InputStreamReader(input, StandardCharsets.UTF_8));
            if (yamlData instanceof Map<?, ?> map) flattenMessages("", map, messages);
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        } catch (java.io.IOException ignored) {
            return new LinkedHashMap<>();
        }

        return messages;
    }

    private void flattenMessages(String prefix, Map<?, ?> source, Map<String, String> target) {
        for (var entry : source.entrySet()) {
            var key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> map) {
                flattenMessages(key, map, target);
                continue;
            }

            if (entry.getValue() != null) target.put(key, String.valueOf(entry.getValue()));
        }
    }

    /**
     * Add the prefix to a message.
     *
     * @param msg the message.
     * @return the message with the prefix.
     */
    public String prefix(String msg) {
        return colorFormatter.translateAlternateColorCodes('&', getPrefix() + msg);
    }
}
