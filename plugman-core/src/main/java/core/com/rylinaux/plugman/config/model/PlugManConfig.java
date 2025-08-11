package core.com.rylinaux.plugman.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Jackson-based configuration model for PlugMan main configuration.
 * Replaces the annotation-based configuration system.
 *
 * @author rylinaux
 */
@Data
public class PlugManConfig {

    /**
     * Configuration version for migration purposes
     */
    @JsonProperty("version")
    private int version = 3;

    /**
     * Auto-load configuration settings
     */
    @JsonProperty("auto-load")
    private GenericLoadConfig autoLoad = new GenericLoadConfig();

    /**
     * Auto-unload configuration settings
     */
    @JsonProperty("auto-unload")
    private GenericLoadConfig autoUnload = new GenericLoadConfig();

    /**
     * Auto-reload configuration settings
     */
    @JsonProperty("auto-reload")
    private GenericLoadConfig autoReload = new GenericLoadConfig();

    /**
     * List of plugins to ignore
     */
    @JsonProperty("ignored-plugins")
    private List<String> ignoredPlugins = List.of();

    /**
     * Whether to notify on broken command removal
     */
    @JsonProperty("notify-on-broken-command-removal")
    private boolean notifyOnBrokenCommandRemoval = true;

    /**
     * Whether to show Paper warning
     */
    @JsonProperty("showPaperWarning")
    private boolean showPaperWarning = true;

    @Data
    public static class GenericLoadConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        @JsonProperty("check-every-seconds")
        private long checkEverySeconds = 10;
    }
}