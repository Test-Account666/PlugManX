package assembly.com.rylinaux.plugman;

/**
 * Reports when the Paper/Bukkit artifact is installed on Velocity.
 */
public final class WrongPlatformVelocityPlugin {

    private static final String MESSAGE =
            "Wrong PlugManX JAR! This build is for Paper or Bukkit, not Velocity. "
                    + "Download and install the Velocity build instead.";

    /**
     * Reports the platform mismatch as soon as Velocity creates the plugin.
     */
    public WrongPlatformVelocityPlugin() {
        reportPlatformMismatch();
        throw new IllegalStateException(MESSAGE);
    }

    private void reportPlatformMismatch() {
        System.getLogger("PlugManX").log(System.Logger.Level.ERROR, MESSAGE);
    }
}
