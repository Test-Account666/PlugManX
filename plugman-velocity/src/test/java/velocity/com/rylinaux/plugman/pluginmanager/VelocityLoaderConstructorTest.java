package velocity.com.rylinaux.plugman.pluginmanager;

import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VelocityLoaderConstructorTest {

    @Test
    void prefersProxyServerConstructor() throws NoSuchMethodException {
        var constructor = VelocityExperimentalRuntime.findLoaderConstructor(UpstreamLoader.class, ServerImplementation.class);

        assertArrayEquals(new Class<?>[]{ProxyServer.class, Path.class}, constructor.getParameterTypes());
    }

    @Test
    void acceptsConstructorTakingServerImplementation() throws NoSuchMethodException {
        var constructor = VelocityExperimentalRuntime.findLoaderConstructor(ForkLoader.class, ServerImplementation.class);

        assertArrayEquals(new Class<?>[]{ServerImplementation.class, Path.class}, constructor.getParameterTypes());
    }

    @Test
    void rejectsLoaderWithoutMatchingConstructor() {
        assertThrows(NoSuchMethodException.class,
                () -> VelocityExperimentalRuntime.findLoaderConstructor(ForkLoader.class, Object.class));
    }

    static class ServerImplementation {
    }

    static class UpstreamLoader {
        UpstreamLoader(ProxyServer server, Path baseDirectory) {
        }
    }

    static class ForkLoader {
        ForkLoader(ServerImplementation server, Path baseDirectory) {
        }

        ForkLoader(String unrelated) {
        }
    }
}
