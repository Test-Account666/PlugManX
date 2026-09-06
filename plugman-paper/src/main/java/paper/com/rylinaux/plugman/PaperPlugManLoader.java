package paper.com.rylinaux.plugman;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PaperPlugManLoader implements PluginLoader {
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final int MIN_LIBRARY_LOADER_VERSION = 2100;
    private static final int MAX_LIBRARY_LOADER_VERSION = 2602;
    private static final String JACKSON_VERSION = "2.13.5";
    private static final String SNAKEYAML_VERSION = "2.0";
    private static final String COMPILE_SCOPE = "compile";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        if (!shouldUsePaperLibraryLoader()) return;

        var resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("paper", "default", "https://repo.papermc.io/repository/maven-public/").build());
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());
        resolver.addDependency(new Dependency(new DefaultArtifact(jacksonArtifact("jackson-databind")), COMPILE_SCOPE));
        resolver.addDependency(new Dependency(new DefaultArtifact(jacksonArtifact("jackson-core")), COMPILE_SCOPE));
        resolver.addDependency(new Dependency(new DefaultArtifact(jacksonArtifact("jackson-annotations")), COMPILE_SCOPE));
        resolver.addDependency(new Dependency(new DefaultArtifact(snakeyamlArtifact()), COMPILE_SCOPE));

        classpathBuilder.addLibrary(resolver);
    }

    private boolean shouldUsePaperLibraryLoader() {
        var paperVersion = parsePaperVersion(readBukkitVersion());
        return paperVersion >= MIN_LIBRARY_LOADER_VERSION && paperVersion <= MAX_LIBRARY_LOADER_VERSION;
    }

    private String readBukkitVersion() {
        try {
            var bukkitClass = Class.forName("org.bukkit.Bukkit");
            var method = bukkitClass.getMethod("getBukkitVersion");
            return String.valueOf(method.invoke(null));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return "";
        }
    }

    private int parsePaperVersion(String bukkitVersion) {
        var numbers = new ArrayList<Integer>();
        var matcher = VERSION_NUMBER_PATTERN.matcher(bukkitVersion == null ? "" : bukkitVersion);
        while (matcher.find()) numbers.add(Integer.parseInt(matcher.group()));

        if (numbers.isEmpty()) return 0;
        if (numbers.get(0) == 1) return parseMinecraftVersion(numbers);

        var major = numbers.get(0);
        var minor = numbers.size() > 1 ? numbers.get(1) : 0;
        return major * 100 + minor;
    }

    private int parseMinecraftVersion(List<Integer> numbers) {
        var minor = numbers.size() > 1 ? numbers.get(1) : 0;
        var patch = numbers.size() > 2 ? numbers.get(2) : 0;
        return minor * 100 + patch;
    }

    private String jacksonArtifact(String artifactId) {
        return String.join(".", "com", "fasterxml", "jackson", "core") + ":" + artifactId + ":" + JACKSON_VERSION;
    }

    private String snakeyamlArtifact() {
        return String.join(".", "org", "yaml") + ":snakeyaml:" + SNAKEYAML_VERSION;
    }
}
