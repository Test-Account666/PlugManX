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

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import paper.com.rylinaux.plugman.pluginmanager.PaperPluginManager;
import paper.com.rylinaux.plugman.reloadstrategy.LegacyPaperReloadStrategy;
import paper.com.rylinaux.plugman.reloadstrategy.ModernPaperReloadStrategy;
import paper.com.rylinaux.plugman.reloadstrategy.PaperReloadStrategy;
import paper.com.rylinaux.plugman.util.PaperReflectionNames;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles paper-specific initialization logic for PlugMan.
 *
 * @author rylinaux
 */
@RequiredArgsConstructor
public class PaperInitializer {

    private final PlugManBukkit plugin;
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final int MODERN_PAPER_VERSION = 2005;
    private static final int MODERN_CHAT_COLOR_VERSION = 2602;
    private static final String WARNING_BORDER_TEXT = "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~";
    private static final String LEGACY_GREEN = "\u00A7a";
    private static final String LEGACY_DARK_GRAY = "\u00A78";
    private static final String LEGACY_WARNING_COLOR = "\u00A7e";
    private static final String LEGACY_DETAIL_COLOR = "\u00A77";
    private static final String LEGACY_VALUE_COLOR = "\u00A7b";
    private static final String LEGACY_LINK_COLOR = "\u00A79";
    private static final Component COMPONENT_PREFIX = Component.text("[PlugManX] ", NamedTextColor.GREEN);
    private PaperReloadStrategy paperReloadStrategy;

    /**
     * Initialize the plugin manager based on server type, returning paper plugin manager if on paper
     */
    public PluginManager initializePaperPluginManager(BukkitPluginManager bukkitPluginManager) {
        if (!ClassAccessor.classExists(PaperReflectionNames.PAPER_PLUGIN_MANAGER)) {
            plugin.getLogger().warning("We're in a paper environment, but cannot find Paper's plugin manager?!");
            return bukkitPluginManager;
        }

        paperReloadStrategy = resolvePaperReloadStrategy();
        return paperReloadStrategy.createPluginManager(bukkitPluginManager);
    }

    private int parsePaperVersion(String bukkitVersion) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(bukkitVersion == null ? "" : bukkitVersion);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        if (numbers.isEmpty()) {
            plugin.getLogger().warning("Could not parse Paper version from '" + bukkitVersion + "'. Falling back to legacy plugin manager.");
            return 0;
        }
        if (numbers.get(0) == 1) {
            int minor = numbers.size() > 1 ? numbers.get(1) : 0;
            int patch = numbers.size() > 2 ? numbers.get(2) : 0;
            return minor * 100 + patch;
        }
        int major = numbers.get(0);
        int minor = numbers.size() > 1 ? numbers.get(1) : 0;
        return major * 100 + minor;
    }

    /**
     * Show Paper warning if running on Paper server
     */
    public void showPaperWarningIfNeeded(PluginManager pluginManager) {
        if (!(pluginManager instanceof PaperPluginManager)) return;

        var config = plugin.getServiceRegistry().get(PlugManConfigurationManager.class);
        if (!config.getPlugManConfig().isShowPaperWarning()) return;

        var strategy = paperReloadStrategy == null ? resolvePaperReloadStrategy() : paperReloadStrategy;

        sendColoredPaperWarning(
                LEGACY_DARK_GRAY + WARNING_BORDER_TEXT,
                Component.text(WARNING_BORDER_TEXT, NamedTextColor.DARK_GRAY)
        );
        sendColoredPaperWarning(
                LEGACY_WARNING_COLOR + "It seems like you're running on " + LEGACY_VALUE_COLOR + Bukkit.getName() + " (" + Bukkit.getVersion() + ")" + LEGACY_WARNING_COLOR + ".",
                Component.text("It seems like you're running on ", NamedTextColor.YELLOW)
                        .append(Component.text(Bukkit.getName() + " (" + Bukkit.getVersion() + ")", NamedTextColor.AQUA))
                        .append(Component.text(".", NamedTextColor.YELLOW))
        );
        if (config.getPlugManConfig().isPaperReloadDebug()) showPaperRuntimeDiagnostics(strategy);
        sendColoredPaperWarning(
                LEGACY_WARNING_COLOR + "PlugManX Paper plugin reload support is experimental.",
                Component.text("PlugManX Paper plugin reload support is experimental.", NamedTextColor.YELLOW)
        );
        sendColoredPaperWarning(
                LEGACY_WARNING_COLOR + "Also, if you encounter any issues, please join my discord: " + LEGACY_LINK_COLOR + "https://discord.gg/GxEFhVY6ff",
                Component.text("Also, if you encounter any issues, please join my discord: ", NamedTextColor.YELLOW)
                        .append(Component.text("https://discord.gg/GxEFhVY6ff", NamedTextColor.BLUE))
        );
        sendColoredPaperWarning(
                LEGACY_WARNING_COLOR + "Or create an issue on GitHub: " + LEGACY_LINK_COLOR + "https://github.com/Test-Account666/PlugManX",
                Component.text("Or create an issue on GitHub: ", NamedTextColor.YELLOW)
                        .append(Component.text("https://github.com/Test-Account666/PlugManX", NamedTextColor.BLUE))
        );
        sendColoredPaperWarning(
                LEGACY_DARK_GRAY + WARNING_BORDER_TEXT,
                Component.text(WARNING_BORDER_TEXT, NamedTextColor.DARK_GRAY)
        );

        plugin.getLogger().info("You can disable this warning by setting 'showPaperWarning' to false in the config.yml");
    }

    private void showPaperRuntimeDiagnostics(PaperReloadStrategy strategy) {
        sendDiagnosticLine("Detected server software: ", Bukkit.getName());
        sendDiagnosticLine("Paper version: ", Bukkit.getVersion());
        sendDiagnosticLine("Bukkit version: ", Bukkit.getBukkitVersion());
        sendDiagnosticLine("Paper reload strategy: ", strategy.getName());
        sendDiagnosticLine("Modern Paper lifecycle events available: ", formatAvailability(strategy.isModernLifecycleAvailable()));
    }

    private void sendDiagnosticLine(String label, String value) {
        sendColoredPaperWarning(
                LEGACY_DETAIL_COLOR + label + LEGACY_VALUE_COLOR + value,
                Component.text(label, NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.AQUA))
        );
    }

    private void sendColoredPaperWarning(String legacyMessage, Component modernMessage) {
        if (isModernChatColorAvailable()) {
            Bukkit.getConsoleSender().sendMessage(COMPONENT_PREFIX.append(modernMessage));
            return;
        }
        Bukkit.getConsoleSender().sendMessage(LEGACY_GREEN + "[PlugManX] " + legacyMessage);
    }

    private PaperReloadStrategy resolvePaperReloadStrategy() {
        var paperVersion = parsePaperVersion(Bukkit.getBukkitVersion());
        if (paperVersion >= MODERN_PAPER_VERSION) return new ModernPaperReloadStrategy();
        return new LegacyPaperReloadStrategy();
    }

    private boolean isModernChatColorAvailable() {
        return parsePaperVersion(Bukkit.getBukkitVersion()) >= MODERN_CHAT_COLOR_VERSION;
    }

    private String formatAvailability(boolean available) {
        return available ? "yes" : "no";
    }

}
