package assembly.com.rylinaux.plugman;

/**
 * Reports when the Paper/Bukkit artifact is installed on Velocity.
 */
public final class WrongPlatformVelocityPlugin {

    private static final System.Logger LOGGER = System.getLogger("PlugManX");
    private static final String MESSAGE =
            "Wrong PlugManX JAR! This build is for Paper or Bukkit, not Velocity. "
                    + "Download and install the Velocity build instead.";

    /**
     * Reports the platform mismatch as soon as Velocity creates the plugin.
     */
    public WrongPlatformVelocityPlugin() {
        LOGGER.log(System.Logger.Level.ERROR, MESSAGE);
        throw new IllegalStateException(MESSAGE);
    }
}
