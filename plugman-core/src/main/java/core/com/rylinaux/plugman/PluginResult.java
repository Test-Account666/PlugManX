package core.com.rylinaux.plugman;

import java.util.Arrays;

/**
 * Represents the result of a plugin operation.
 * This unified class combines functionality from both Bukkit and Bungee implementations.
 */
public final class PluginResult {
    private final boolean success;
    private final String messageId;
    private final Object[] messageArgs;

    public PluginResult(boolean success, String messageId) {
        this.success = success;
        this.messageId = messageId;
        this.messageArgs = new Object[0];
    }

    public PluginResult(boolean success, String messageId, Object... messageArgs) {
        this.success = success;
        this.messageId = messageId;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    public boolean success() {
        return success;
    }

    public String messageId() {
        return messageId;
    }

    public Object[] messageArgs() {
        return messageArgs.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PluginResult that)) return false;
        return success == that.success
                && java.util.Objects.equals(messageId, that.messageId)
                && Arrays.equals(messageArgs, that.messageArgs);
    }

    @Override
    public int hashCode() {
        var result = java.util.Objects.hash(success, messageId);
        result = 31 * result + Arrays.hashCode(messageArgs);
        return result;
    }

    @Override
    public String toString() {
        return "PluginResult[success=" + success + ", messageId=" + messageId
                + ", messageArgs=" + Arrays.toString(messageArgs) + "]";
    }
}
