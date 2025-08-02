package core.com.rylinaux.plugman;

/**
 * Represents the result of a plugin operation.
 * This unified class combines functionality from both Bukkit and Bungee implementations.
 */
public record PluginResult(boolean success, String messageId) {
}