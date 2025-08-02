package paper.com.rylinaux.plugman;

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

import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import paper.com.rylinaux.plugman.pluginmanager.ModernPaperPluginManager;
import paper.com.rylinaux.plugman.pluginmanager.PaperPluginManager;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles paper-specific initialization logic for PlugMan.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class PaperInitializer {

    private final JavaPlugin plugin;

    /**
     * Initialize the plugin manager based on server type, returning paper plugin manager if on paper
     */
    public PluginManager initializePaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        if (!ClassAccessor.classExists("io.papermc.paper.plugin.manager.PaperPluginManagerImpl")) return bukkitPluginManager;

        var version = Bukkit.getBukkitVersion().split("-")[0].split("\\.");

        var paperVersion = Integer.parseInt(version[1]) * 100;
        if (version.length >= 3) paperVersion += Integer.parseInt(version[2]);

        return paperVersion >= 2005?
                new ModernPaperPluginManager(bukkitPluginManager) :
                new PaperPluginManager(bukkitPluginManager);
    }

    /**
     * Show Paper warning if running on Paper server
     */
    public void showPaperWarningIfNeeded(PluginManager pluginManager) {
        if (!(pluginManager instanceof PaperPluginManager)) return;
        if (!plugin.getConfig().getBoolean("showPaperWarning", true)) return;

        plugin.getLogger().warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        plugin.getLogger().warning("It seems like you're running on paper.");
        plugin.getLogger().warning("PlugManX cannot interact with paper-plugins, yet.");
        plugin.getLogger().warning("Also, if you encounter any issues, please join my discord: https://discord.gg/GxEFhVY6ff");
        plugin.getLogger().warning("Or create an issue on GitHub: https://github.com/TheBlackEntity/PlugMan");
        plugin.getLogger().warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        plugin.getLogger().info("You can disable this warning by setting 'showPaperWarning' to false in the config.yml");
    }

}