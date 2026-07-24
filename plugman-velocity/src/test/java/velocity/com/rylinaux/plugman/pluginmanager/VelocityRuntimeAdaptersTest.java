package velocity.com.rylinaux.plugman.pluginmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityRuntimeAdaptersTest {

    @Test
    void rejectsVersionsOlderThanVelocity34() {
        assertThrows(IllegalStateException.class, () -> VelocityRuntimeAdapters.find("3.3.9"));
    }

    @ParameterizedTest(name = "Velocity {0} uses {1}")
    @CsvSource({
            "3.4.0, Velocity 3.4 runtime adapter, true, false",
            "3.5.1, Velocity 3.4 runtime adapter, true, false",
            "3.6.0-SNAPSHOT, Velocity 3.4 runtime adapter, true, false",
            "4.0.0, Velocity 4.x runtime adapter, true, false",
            "4.1.0-SNAPSHOT (git-b45716de-b9), Velocity 4.x runtime adapter, true, false"
    })
    void selectsAdapterForSupportedVelocityVersions(String version,
                                                     String expectedAdapter,
                                                     boolean packetRegistryCleanup,
                                                     boolean newerThanTested) {
        var selection = VelocityRuntimeAdapters.find(version);

        assertEquals(expectedAdapter, selection.adapter().name());
        assertEquals(packetRegistryCleanup, selection.adapter().supportsPacketRegistryCleanup());
        assertEquals(newerThanTested, selection.newerThanTested());
    }

    @Test
    void allowsNewerVersionsWithCompatibilityWarning() {
        var selection = VelocityRuntimeAdapters.find("5.0.0-SNAPSHOT");

        assertEquals("Velocity 4.x runtime adapter", selection.adapter().name());
        assertTrue(selection.adapter().supportsPacketRegistryCleanup());
        assertTrue(selection.newerThanTested());
    }
}
