package paper.com.rylinaux.plugman;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import manifold.rt.api.NoBootstrap;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

@NoBootstrap
public class PaperPlugManLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://oss.sonatype.org/content/repositories/releases/").build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.fasterxml.jackson.core:jackson-databind:2.13.5"), "compile"));

        resolver.addDependency(new Dependency(new DefaultArtifact("systems.manifold:manifold-rt:2025.1.25"), "compile"));
        resolver.addDependency(new Dependency(new DefaultArtifact("systems.manifold:manifold-tuple-rt:2025.1.25"), "compile"));

        classpathBuilder.addLibrary(resolver);
    }
}
