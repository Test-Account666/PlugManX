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
    private int version = 5;

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

    /**
     * Whether to log verbose Paper runtime reload debug output
     */
    @JsonProperty("paperReloadDebug")
    private boolean paperReloadDebug = false;

    /**
     * Controls which dependent plugins are automatically reloaded with a target plugin.
     * Supported values: ALL, REQUIRED_ONLY, OFF.
     */
    @JsonProperty("reloadDependentsMode")
    private String reloadDependentsMode = "REQUIRED_ONLY";

    public boolean shouldReloadHardDependents() {
        return !isReloadDependentsMode("OFF");
    }

    public boolean shouldReloadSoftDependents() {
        return isReloadDependentsMode("ALL");
    }

    private boolean isReloadDependentsMode(String expectedMode) {
        return reloadDependentsMode != null && reloadDependentsMode.equalsIgnoreCase(expectedMode);
    }

    @Data
    public static class GenericLoadConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        @JsonProperty("check-every-seconds")
        private long checkEverySeconds = 10;
    }
}
