package bukkit.com.rylinaux.plugman.logging;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Writes crash dumps for PlugManX failures that need more context than console output.
 */
public final class CrashDumpWriter {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
    private static final DateTimeFormatter HUMAN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();

    private CrashDumpWriter() {
    }

    public static void write(String context, Throwable throwable) {
        if (throwable == null) return;

        try {
            var dumpDirectory = getDumpDirectory();
            Files.createDirectories(dumpDirectory.toPath());

            var dumpId = "PMX-" + FILE_TIMESTAMP.format(LocalDateTime.now(SERVER_ZONE));
            var dumpFile = new File(dumpDirectory, dumpId + ".log");
            Files.writeString(dumpFile.toPath(), createDump(context, throwable), StandardCharsets.UTF_8);
            var plugin = PlugManBukkit.getInstance();
            if (plugin != null) plugin.getLogger().warning("Crash dump written: " + dumpId + " (" + dumpFile.getPath() + ")");
        } catch (IOException | RuntimeException exception) {
            var plugin = PlugManBukkit.getInstance();
            if (plugin != null) plugin.getLogger().log(Level.WARNING, "Failed to write PlugManX crash dump", exception);
        }
    }

    private static File getDumpDirectory() {
        var plugin = PlugManBukkit.getInstance();
        var dataFolder = plugin == null ? new File("plugins", "PlugManX") : plugin.getDataFolder();
        return new File(dataFolder, "crash-dumps");
    }

    private static String createDump(String context, Throwable throwable) {
        var writer = new StringWriter();
        try (var printWriter = new PrintWriter(writer)) {
            printWriter.println("PlugManX crash dump");
            printWriter.println("===================");
            printWriter.println("Time: " + HUMAN_TIMESTAMP.format(LocalDateTime.now(SERVER_ZONE)));
            printWriter.println("Context: " + (context == null || context.isBlank() ? "unknown" : context));
            printWriter.println("Thread: " + Thread.currentThread().getName());
            printWriter.println("Server: " + safeServerVersion());
            printWriter.println("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
            printWriter.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            printWriter.println();
            appendThrowable(printWriter, throwable, "");
        }
        return writer.toString();
    }

    private static void appendThrowable(PrintWriter printWriter, Throwable throwable, String prefix) {
        printWriter.println(prefix + throwable);
        for (var element : throwable.getStackTrace()) printWriter.println(prefix + "\tat " + element);

        for (var suppressed : throwable.getSuppressed()) {
            printWriter.println(prefix + "Suppressed:");
            appendThrowable(printWriter, suppressed, prefix + "\t");
        }

        var cause = throwable.getCause();
        if (cause != null) {
            printWriter.println(prefix + "Caused by:");
            appendThrowable(printWriter, cause, prefix);
        }
    }

    private static String safeServerVersion() {
        try {
            return Bukkit.getName() + " " + Bukkit.getBukkitVersion() + " / " + Bukkit.getVersion();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }
}
