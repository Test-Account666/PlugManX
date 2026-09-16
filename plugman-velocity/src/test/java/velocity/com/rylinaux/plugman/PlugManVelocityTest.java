package velocity.com.rylinaux.plugman;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlugManVelocityTest {

    @Test
    void recognizesOnlyPlugManCommandsForBackendForwarding() {
        assertTrue(PlugManVelocity.isPlugManCommand("plugman"));
        assertTrue(PlugManVelocity.isPlugManCommand("plugman deps ExamplePlugin"));
        assertTrue(PlugManVelocity.isPlugManCommand(" /PLUGMAN list"));

        assertFalse(PlugManVelocity.isPlugManCommand("plugmanager list"));
        assertFalse(PlugManVelocity.isPlugManCommand("plugins"));
        assertFalse(PlugManVelocity.isPlugManCommand(null));

        assertEquals("plugmanvelocity deps ExamplePlugin",
                PlugManVelocity.toVelocityCommand("plugman deps ExamplePlugin"));
        assertEquals("plugmanvelocity list", PlugManVelocity.toVelocityCommand(" /PLUGMAN list"));
    }
}
