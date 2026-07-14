package velocity.com.rylinaux.plugman.pluginmanager;

import java.util.List;

final class VelocityRuntimeAdapters {
    private static final List<VelocityRuntimeAdapter> ADAPTERS = List.of(new Velocity4RuntimeAdapter());

    private VelocityRuntimeAdapters() {
    }

    static VelocityRuntimeAdapter find(String version) {
        return ADAPTERS.stream()
                .filter(adapter -> adapter.supports(version))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No experimental Velocity runtime adapter is available for version " + version));
    }
}
