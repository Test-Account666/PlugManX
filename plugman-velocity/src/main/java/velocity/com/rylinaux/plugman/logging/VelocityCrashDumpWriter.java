package velocity.com.rylinaux.plugman.logging;

import velocity.com.rylinaux.plugman.PlugManVelocity;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Writes diagnostic dumps for failures in the experimental Velocity runtime. */
public final class VelocityCrashDumpWriter {
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
    private static final DateTimeFormatter HUMAN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();

    private VelocityCrashDumpWriter() {
    }

    public static DumpResult write(String context, Throwable throwable) {
        if (throwable == null) return null;

        var plugin = PlugManVelocity.getInstance();
        if (plugin == null) return null;

        var dumpId = "PMX-VEL-" + FILE_TIMESTAMP.format(LocalDateTime.now(SERVER_ZONE))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var dumpFile = plugin.getDataDirectory().resolve("crash-dumps").resolve(dumpId + ".log");
        try {
            Files.createDirectories(dumpFile.getParent());
            Files.writeString(dumpFile, createDump(context, throwable), StandardCharsets.UTF_8);
            plugin.getLogger().warn("Velocity crash dump written: {} ({})", dumpId, dumpFile);
            return new DumpResult(dumpId, dumpFile);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warn("Failed to write Velocity crash dump {}", dumpId, exception);
            return null;
        }
    }

    private static String createDump(String context, Throwable throwable) {
        var plugin = PlugManVelocity.getInstance();
        var proxyVersion = plugin.getServer().getVersion();
        var writer = new StringWriter();
        try (var printWriter = new PrintWriter(writer)) {
            printWriter.println("PlugManX Velocity experimental runtime crash dump");
            printWriter.println("========================================");
            printWriter.println("Time: " + HUMAN_TIMESTAMP.format(LocalDateTime.now(SERVER_ZONE)));
            printWriter.println("Context: " + (context == null || context.isBlank() ? "unknown" : context));
            printWriter.println("Thread: " + Thread.currentThread().getName());
            printWriter.println("Proxy: " + proxyVersion.getName() + " " + proxyVersion.getVersion());
            printWriter.println("Java: " + System.getProperty("java.version") + " ("
                    + System.getProperty("java.vendor") + ")");
            printWriter.println("OS: " + System.getProperty("os.name") + " "
                    + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            printWriter.println();
            appendThrowable(printWriter, throwable, "");
        }
        return writer.toString();
    }

    private static void appendThrowable(PrintWriter writer, Throwable throwable, String prefix) {
        writer.println(prefix + throwable);
        for (var element : throwable.getStackTrace()) writer.println(prefix + "\tat " + element);
        for (var suppressed : throwable.getSuppressed()) {
            writer.println(prefix + "Suppressed:");
            appendThrowable(writer, suppressed, prefix + "\t");
        }
        if (throwable.getCause() != null) {
            writer.println(prefix + "Caused by:");
            appendThrowable(writer, throwable.getCause(), prefix);
        }
    }

    public record DumpResult(String id, Path file) {
    }
}
