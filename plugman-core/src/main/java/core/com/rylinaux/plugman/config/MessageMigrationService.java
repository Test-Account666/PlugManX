package core.com.rylinaux.plugman.config;

import core.com.rylinaux.plugman.logging.PluginLogger;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class MessageMigrationService {
    public static final String DEFAULT_MESSAGES_FILE = "messages.yml";
    public static final String GERMAN_MESSAGES_FILE = "messages_de.yml";

    private static final String MESSAGES_FOLDER = "messages";
    private static final String ERROR_SECTION = "error";
    private static final String USAGE_SECTION = "usage";
    private static final String HELP_SECTION = "help";
    private static final String DEPS_SECTION = "deps";
    private static final String RELOAD_MODE_SECTION = "reloadmode";
    private static final String RELOAD_SECTION = "reload";
    private static final String RESTART_SECTION = "restart";
    private static final BulkMessageDefaults DEFAULT_BULK_MESSAGES = new BulkMessageDefaults(
            "&cDependency cycle detected: {0}",
            "&9Reloaded {0} plugins in {1} seconds (&c{2} failed&9, &e{3} skipped&9).",
            "&9Restarted {0} plugins in {1} seconds (&c{2} failed&9, &e{3} skipped&9).");
    private static final BulkMessageDefaults GERMAN_BULK_MESSAGES = new BulkMessageDefaults(
            "&cAbhängigkeitszyklus erkannt: {0}",
            "&9{0} Plugins wurden in {1} Sekunden neu geladen (&c{2} fehlgeschlagen&9, &e{3} übersprungen&9).",
            "&9{0} Plugins wurden in {1} Sekunden neu gestartet (&c{2} fehlgeschlagen&9, &e{3} übersprungen&9).");
    private static final MessageDefaults DEFAULT_MESSAGES = new MessageDefaults(
            "&9Paper Plugins (&b{0}&9): {1}",
            "&9Bukkit Plugins (&e{0}&9): {1}",
            "&7- &a/{0} deps <plugin> &f- &7Show plugin dependency relationships.",
            "&7- &a/{0} reloadmode [ALL|REQUIRED_ONLY|OFF] &f- &7View or change dependent reload behavior.",
            "Plugin Dependencies: {0}",
            "&7- Depends: &a{0}",
            "&7- SoftDepends: &a{0}",
            "&7- Used by: &a{0}",
            "&9Dependent reload mode set to &a{0}&9.",
            "&9Current dependent reload mode: &a{0}&9.",
            "&cInvalid dependent reload mode: {0}. Use ALL, REQUIRED_ONLY, or OFF.",
            "&cCould not save dependent reload mode. Check the server log.",
            "&c{0} could not be enabled. Check the server log for the plugin error.",
            "&cPlugMan is configured to ignore that plugin.",
            "&cThat is not a valid plugin.",
            "&cYou do not have permission to do this.",
            "&cYou must specify a plugin.",
            "&cYou must specify a command.",
            "&cPaper plugins are currently not supported, I'm sorry.",
            "&7- &9Command: &7{0}",
            "&7- &9Description: &7{0}",
            "&7- &9Usage: &7{0}",
            "&c{0} loaded, but failed during onEnable. Check the stacktrace above.",
            "&cCould not load {0}. Missing required dependencies: {1}",
            "&cCould not reload {0}; active plugins depend on it: {1}. Reload or stop those plugins first, or restart the server.",
            "&cCould not restart {0}; active plugins depend on it: {1}. Restart or stop those plugins first, or restart the server.",
            "&cReloading {0} plugins can crash or destabilize the server. Run &e/plugman reload all {1}&c to confirm.",
            "&cRestarting {0} plugins can crash or destabilize the server. Run &e/plugman restart all {1}&c to confirm.");
    private static final MessageDefaults GERMAN_MESSAGES = new MessageDefaults(
            "&9Paper-Plugins (&b{0}&9): {1}",
            "&9Bukkit-Plugins (&e{0}&9): {1}",
            "&7- &a/{0} deps <plugin> &f- &7Zeigt Plugin-Abhängigkeiten an.",
            "&7- &a/{0} reloadmode [ALL|REQUIRED_ONLY|OFF] &f- &7Zeigt oder ändert das Dependent-Reload-Verhalten.",
            "Plugin-Abhängigkeiten: {0}",
            "&7- Abhängigkeiten: &a{0}",
            "&7- Soft-Abhängigkeiten: &a{0}",
            "&7- Genutzt von: &a{0}",
            "&9Dependent-Reload-Modus wurde auf &a{0}&9 gesetzt.",
            "&9Aktueller Dependent-Reload-Modus: &a{0}&9.",
            "&cUngültiger Dependent-Reload-Modus: {0}. Nutze ALL, REQUIRED_ONLY oder OFF.",
            "&cDependent-Reload-Modus konnte nicht gespeichert werden. Prüfe den Server-Log.",
            "&c{0} konnte nicht aktiviert werden. Prüfe den Server-Log für den Plugin-Fehler.",
            "&cPlugMan ist darauf konfiguriert, dieses Plugin zu ignorieren.",
            "&cDies ist kein gültiges Plugin.",
            "&cDu hast hierzu keine Berechtigung.",
            "&cBitte gib ein Plugin an.",
            "&cBitte gib einen Befehl an.",
            "&cPaper-Plugins werden derzeit leider nicht unterstützt.",
            "&7- &9Befehl: &7{0}",
            "&7- &9Beschreibung: &7{0}",
            "&7- &9Verwendung: &7{0}",
            "&c{0} wurde geladen, ist aber in onEnable fehlgeschlagen. Prüfe den Stacktrace oben.",
            "&c{0} konnte nicht geladen werden. Fehlende Pflicht-Abhängigkeiten: {1}",
            "&c{0} konnte nicht neu geladen werden; aktive Plugins hängen davon ab: {1}. Lade oder stoppe diese Plugins zuerst, oder starte den Server neu.",
            "&c{0} konnte nicht neu gestartet werden; aktive Plugins hängen davon ab: {1}. Starte oder stoppe diese Plugins zuerst, oder starte den Server neu.",
            "&cDas Neuladen von {0} Plugins kann den Server abstürzen lassen oder instabil machen. Führe &e/plugman reload all {1}&c aus, um zu bestätigen.",
            "&cDas Neustarten von {0} Plugins kann den Server abstürzen lassen oder instabil machen. Führe &e/plugman restart all {1}&c aus, um zu bestätigen.");
    private static final Map<String, MessageDefaults> MESSAGE_DEFAULTS = Map.of(
            DEFAULT_MESSAGES_FILE, DEFAULT_MESSAGES,
            GERMAN_MESSAGES_FILE, GERMAN_MESSAGES,
            "messages_es.yml", DEFAULT_MESSAGES,
            "messages_cn.yml", DEFAULT_MESSAGES,
            "messages_jp.yml", DEFAULT_MESSAGES,
            "messages_ru.yml", DEFAULT_MESSAGES,
            "messages_tw.yml", DEFAULT_MESSAGES
    );

    private final File dataFolder;
    private final PluginLogger logger;

    public void migrateToVersion4() {
        migrateMessages();
    }

    public void migrateToVersion5() {
        migrateMessages();
    }

    private void migrateMessages() {
        migrateMessagesFile(new File(dataFolder, DEFAULT_MESSAGES_FILE), MESSAGE_DEFAULTS.get(DEFAULT_MESSAGES_FILE));

        var messagesFolder = new File(dataFolder, MESSAGES_FOLDER);
        for (var entry : MESSAGE_DEFAULTS.entrySet()) {
            if (entry.getKey().equals(DEFAULT_MESSAGES_FILE)) continue;
            migrateMessagesFile(new File(messagesFolder, entry.getKey()), entry.getValue());
        }
    }

    private void migrateMessagesFile(File messagesFile, MessageDefaults defaults) {
        if (!messagesFile.exists()) return;

        try {
            var lines = Files.readAllLines(messagesFile.toPath(), StandardCharsets.UTF_8);
            var bulkDefaults = messagesFile.getName().equals(GERMAN_MESSAGES_FILE)
                    ? GERMAN_BULK_MESSAGES
                    : DEFAULT_BULK_MESSAGES;
            var updatedLines = addMissingEntries(lines, defaults, bulkDefaults);
            if (updatedLines == null) return;

            Files.write(messagesFile.toPath(), updatedLines, StandardCharsets.UTF_8);
            logger.info("Added missing messages to " + messagesFile.getName() + ".");
        } catch (IOException exception) {
            logger.warning("Failed to migrate " + messagesFile.getName() + " messages: " + exception.getMessage());
        }
    }

    private List<String> addMissingEntries(List<String> lines,
                                           MessageDefaults defaults,
                                           BulkMessageDefaults bulkDefaults) {
        var updatedLines = new ArrayList<>(lines);
        var changed = false;

        changed |= addMissingMessageEntry(updatedLines, "list", "paper", defaults.paperMessage());
        changed |= addMissingMessageEntry(updatedLines, "list", "bukkit", defaults.bukkitMessage());
        changed |= addMissingMessageEntry(updatedLines, HELP_SECTION, DEPS_SECTION, defaults.helpDepsMessage());
        changed |= addMissingMessageEntry(updatedLines, HELP_SECTION, RELOAD_MODE_SECTION, defaults.helpReloadModeMessage());
        changed |= addMissingMessageEntry(updatedLines, DEPS_SECTION, "header", defaults.depsHeaderMessage());
        changed |= addMissingMessageEntry(updatedLines, DEPS_SECTION, "depends", defaults.depsDependsMessage());
        changed |= addMissingMessageEntry(updatedLines, DEPS_SECTION, "softdepends", defaults.depsSoftDependsMessage());
        changed |= addMissingMessageEntry(updatedLines, DEPS_SECTION, "used-by", defaults.depsUsedByMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_MODE_SECTION, "changed", defaults.reloadModeChangedMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_MODE_SECTION, "current", defaults.reloadModeCurrentMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_MODE_SECTION, "invalid", defaults.reloadModeInvalidMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_MODE_SECTION, "save-failed", defaults.reloadModeSaveFailedMessage());
        changed |= addMissingMessageEntry(updatedLines, "enable", "failed", defaults.enableFailedMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "ignored", defaults.errorIgnoredMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "invalid-plugin", defaults.errorInvalidPluginMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "no-permission", defaults.errorNoPermissionMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "specify-plugin", defaults.errorSpecifyPluginMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "specify-command", defaults.errorSpecifyCommandMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "paper-plugin", defaults.errorPaperPluginMessage());
        changed |= addMissingMessageEntry(updatedLines, ERROR_SECTION, "dependency-cycle", bulkDefaults.dependencyCycleMessage());
        changed |= addMissingNestedMessageEntry(updatedLines, ERROR_SECTION, USAGE_SECTION, "command", defaults.errorUsageCommandMessage());
        changed |= addMissingNestedMessageEntry(updatedLines, ERROR_SECTION, USAGE_SECTION, "description", defaults.errorUsageDescriptionMessage());
        changed |= addMissingNestedMessageEntry(updatedLines, ERROR_SECTION, USAGE_SECTION, USAGE_SECTION, defaults.errorUsageUsageMessage());
        changed |= addMissingMessageEntry(updatedLines, "load", "enable-failed", defaults.loadEnableFailedMessage());
        changed |= addMissingMessageEntry(updatedLines, "load", "missing-dependencies", defaults.missingDependenciesMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_SECTION, "blocked-dependents", defaults.reloadBlockedDependentsMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_SECTION, "confirm-all", defaults.reloadConfirmAllMessage());
        changed |= addMissingMessageEntry(updatedLines, RELOAD_SECTION, "summary", bulkDefaults.reloadSummaryMessage());
        changed |= addMissingMessageEntry(updatedLines, RESTART_SECTION, "blocked-dependents", defaults.restartBlockedDependentsMessage());
        changed |= addMissingMessageEntry(updatedLines, RESTART_SECTION, "confirm-all", defaults.restartConfirmAllMessage());
        changed |= addMissingMessageEntry(updatedLines, RESTART_SECTION, "summary", bulkDefaults.restartSummaryMessage());

        return changed ? updatedLines : null;
    }

    private boolean addMissingMessageEntry(List<String> lines, String section, String key, String message) {
        var sectionIndex = findTopLevelSection(lines, section + ":");
        var sectionEnd = sectionIndex == -1 ? lines.size() : findSectionEnd(lines, sectionIndex);
        if (hasMessageEntry(lines, sectionIndex, sectionEnd, key)) return false;

        if (sectionIndex == -1) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            lines.add(section + ":");
            lines.add(formatMessageLine("  ", key, message));
            return true;
        }

        lines.add(sectionEnd, formatMessageLine("  ", key, message));
        return true;
    }

    private boolean addMissingNestedMessageEntry(List<String> lines, String section, String nestedSection, String key, String message) {
        var sectionIndex = findTopLevelSection(lines, section + ":");
        if (sectionIndex == -1) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            lines.add(section + ":");
            lines.add("  " + nestedSection + ":");
            lines.add(formatMessageLine("    ", key, message));
            return true;
        }

        var sectionEnd = findSectionEnd(lines, sectionIndex);
        var nestedIndex = findNestedSection(lines, sectionIndex, sectionEnd, nestedSection + ":");
        if (nestedIndex == -1) {
            lines.add(sectionEnd, "  " + nestedSection + ":");
            lines.add(sectionEnd + 1, formatMessageLine("    ", key, message));
            return true;
        }

        var nestedEnd = findNestedSectionEnd(lines, nestedIndex, sectionEnd);
        if (hasNestedMessageEntry(lines, nestedIndex, nestedEnd, key)) return false;

        lines.add(nestedEnd, formatMessageLine("    ", key, message));
        return true;
    }

    private String formatMessageLine(String indent, String key, String message) {
        return indent + key + ": '" + message.replace("'", "''") + "'";
    }

    private int findTopLevelSection(List<String> lines, String sectionName) {
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(sectionName) && !lines.get(i).startsWith(" ")) return i;
        }

        return -1;
    }

    private int findSectionEnd(List<String> lines, int sectionIndex) {
        for (var i = sectionIndex + 1; i < lines.size(); i++) {
            var line = lines.get(i);
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("#")) return i;
        }

        return lines.size();
    }

    private int findNestedSection(List<String> lines, int sectionIndex, int sectionEnd, String sectionName) {
        for (var i = sectionIndex + 1; i < sectionEnd; i++) {
            if (lines.get(i).trim().equals(sectionName) && lines.get(i).startsWith("  ") && !lines.get(i).startsWith("    ")) return i;
        }

        return -1;
    }

    private int findNestedSectionEnd(List<String> lines, int nestedIndex, int sectionEnd) {
        for (var i = nestedIndex + 1; i < sectionEnd; i++) {
            var line = lines.get(i);
            if (!line.isBlank() && line.startsWith("  ") && !line.startsWith("    ") && !line.startsWith("  #")) return i;
        }

        return sectionEnd;
    }

    private boolean hasMessageEntry(List<String> lines, int sectionIndex, int sectionEnd, String key) {
        if (sectionIndex == -1) return false;

        var expectedPrefix = "  " + key + ":";
        for (var i = sectionIndex + 1; i < sectionEnd; i++) {
            if (lines.get(i).startsWith(expectedPrefix)) return true;
        }

        return false;
    }

    private boolean hasNestedMessageEntry(List<String> lines, int nestedIndex, int nestedEnd, String key) {
        var expectedPrefix = "    " + key + ":";
        for (var i = nestedIndex + 1; i < nestedEnd; i++) {
            if (lines.get(i).startsWith(expectedPrefix)) return true;
        }

        return false;
    }

    private record MessageDefaults(String paperMessage,
                                   String bukkitMessage,
                                   String helpDepsMessage,
                                   String helpReloadModeMessage,
                                   String depsHeaderMessage,
                                   String depsDependsMessage,
                                   String depsSoftDependsMessage,
                                   String depsUsedByMessage,
                                   String reloadModeChangedMessage,
                                   String reloadModeCurrentMessage,
                                   String reloadModeInvalidMessage,
                                   String reloadModeSaveFailedMessage,
                                   String enableFailedMessage,
                                   String errorIgnoredMessage,
                                   String errorInvalidPluginMessage,
                                   String errorNoPermissionMessage,
                                   String errorSpecifyPluginMessage,
                                   String errorSpecifyCommandMessage,
                                   String errorPaperPluginMessage,
                                   String errorUsageCommandMessage,
                                   String errorUsageDescriptionMessage,
                                   String errorUsageUsageMessage,
                                   String loadEnableFailedMessage,
                                   String missingDependenciesMessage,
                                   String reloadBlockedDependentsMessage,
                                   String restartBlockedDependentsMessage,
                                   String reloadConfirmAllMessage,
                                   String restartConfirmAllMessage) {
    }

    private record BulkMessageDefaults(String dependencyCycleMessage,
                                       String reloadSummaryMessage,
                                       String restartSummaryMessage) {
    }
}
