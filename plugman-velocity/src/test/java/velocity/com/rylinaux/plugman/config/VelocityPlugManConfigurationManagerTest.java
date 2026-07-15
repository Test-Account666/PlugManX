package velocity.com.rylinaux.plugman.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityPlugManConfigurationManagerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void removesLegacyDevelopmentOptionsFromExistingConfiguration() throws IOException {
        var configPath = tempDirectory.resolve("config.yml");
        Files.writeString(configPath, """
                velocityReloadDebug: true
                velocityDevMode: true
                velocityCrashDumps: false
                velocityDevTestFunctions: true
                """);
        var configProvider = new VelocityConfigurationProvider(configPath);

        VelocityPlugManConfigurationManager.removeLegacyDevOptions(configProvider);
        configProvider.save();

        var migratedConfig = new VelocityConfigurationProvider(configPath);
        assertTrue(migratedConfig.getBoolean("velocityReloadDebug", false));
        assertFalse(migratedConfig.contains("velocityDevMode"));
        assertFalse(migratedConfig.contains("velocityCrashDumps"));
        assertFalse(migratedConfig.contains("velocityDevTestFunctions"));
    }
}
