package velocity.com.rylinaux.plugman.pluginmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityRuntimeAdaptersTest {

    @Test
    void rejectsVersionsOlderThanVelocity34() {
        assertThrows(IllegalStateException.class, () -> VelocityRuntimeAdapters.find("3.3.9"));
    }

    @ParameterizedTest(name = "Velocity {0} uses {1}")
    @CsvSource({
            "3.4.0, Velocity 3.4 runtime adapter, true",
            "3.5.1, Velocity 3.4 runtime adapter, true",
            "3.6.0-SNAPSHOT, Velocity 3.4 runtime adapter, true",
            "4.0.0, Velocity 4.0 runtime adapter, true"
    })
    void selectsAdapterForSupportedVelocityVersions(String version,
                                                     String expectedAdapter,
                                                     boolean packetRegistryCleanup) {
        var selection = VelocityRuntimeAdapters.find(version);

        assertEquals(expectedAdapter, selection.adapter().name());
        assertEquals(packetRegistryCleanup, selection.adapter().supportsPacketRegistryCleanup());
        assertNull(selection.warning());
    }

    @Test
    void allowsNewerVersionsWithCompatibilityWarning() {
        var selection = VelocityRuntimeAdapters.find("5.0.0-SNAPSHOT");

        assertEquals("Velocity 4.0 runtime adapter", selection.adapter().name());
        assertTrue(selection.adapter().supportsPacketRegistryCleanup());
        assertNotNull(selection.warning());
    }
}
