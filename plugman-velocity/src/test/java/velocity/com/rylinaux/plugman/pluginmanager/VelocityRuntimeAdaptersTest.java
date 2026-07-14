package velocity.com.rylinaux.plugman.pluginmanager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VelocityRuntimeAdaptersTest {

    @Test
    void rejectsVersionsOlderThanVelocity34() {
        assertThrows(IllegalStateException.class, () -> VelocityRuntimeAdapters.find("3.3.9"));
    }

    @Test
    void selectsVelocity34AdapterForSupportedVelocity3Versions() {
        var selection = VelocityRuntimeAdapters.find("3.4.0-SNAPSHOT-523");

        assertEquals("Velocity 3.4 runtime adapter", selection.adapter().name());
        assertNull(selection.warning());
        assertEquals("Velocity 3.4 runtime adapter",
                VelocityRuntimeAdapters.find("3.9.0").adapter().name());
    }

    @Test
    void selectsVelocity40AdapterWithoutWarningForTestedVersion() {
        var selection = VelocityRuntimeAdapters.find("4.0.0");

        assertEquals("Velocity 4.0 runtime adapter", selection.adapter().name());
        assertNull(selection.warning());
    }

    @Test
    void allowsNewerVersionsWithCompatibilityWarning() {
        var selection = VelocityRuntimeAdapters.find("5.0.0-SNAPSHOT");

        assertEquals("Velocity 4.0 runtime adapter", selection.adapter().name());
        assertNotNull(selection.warning());
    }
}
