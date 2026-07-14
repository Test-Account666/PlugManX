package velocity.com.rylinaux.plugman.pluginmanager;

import java.util.List;
import java.util.regex.Pattern;

final class VelocityRuntimeAdapters {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final List<VelocityRuntimeAdapter> ADAPTERS = List.of(
            new Velocity34RuntimeAdapter(),
            new Velocity4RuntimeAdapter()
    );

    private VelocityRuntimeAdapters() {
    }

    static Selection find(String version) {
        if (compare(version, 3, 4, 0) < 0) {
            throw new IllegalStateException(
                    "The Velocity development runtime requires Velocity 3.4.0 or newer; detected " + version);
        }

        var adapter = ADAPTERS.stream()
                .filter(candidate -> candidate.supports(version))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Velocity development runtime adapter is available for version " + version));
        var warning = compare(version, 4, 0, 0) > 0
                ? "Velocity " + version + " is newer than the tested 4.0.0 runtime. "
                + "PlugManX will use the 4.0 adapter after capability checks, but reload compatibility is not guaranteed."
                : null;
        return new Selection(adapter, warning);
    }

    static int compare(String version, int major, int minor, int patch) {
        if (version == null) throw new IllegalStateException("Velocity version is unavailable");
        var matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            throw new IllegalStateException("Unsupported Velocity version format: " + version);
        }

        var majorComparison = Integer.compare(Integer.parseInt(matcher.group(1)), major);
        if (majorComparison != 0) return majorComparison;
        var minorComparison = Integer.compare(Integer.parseInt(matcher.group(2)), minor);
        if (minorComparison != 0) return minorComparison;
        return Integer.compare(Integer.parseInt(matcher.group(3)), patch);
    }

    record Selection(VelocityRuntimeAdapter adapter, String warning) {
    }
}
