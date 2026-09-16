package velocity.com.rylinaux.plugman;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reports when the Velocity artifact is installed on a Bukkit-based server.
 */
public final class WrongPlatformPaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().severe(
                "Wrong PlugManX JAR! This build is for Velocity, not Paper or Bukkit. "
                        + "Download and install the Paper build instead.");
        getServer().getPluginManager().disablePlugin(this);
    }
}
