package paper.com.rylinaux.plugman;

import bukkit.com.rylinaux.plugman.PlugMan;
import paper.com.rylinaux.plugman.commands.PaperCommandCreator;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import paper.com.rylinaux.plugman.util.PaperThreadUtil;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.ThreadUtil;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import manifold.rt.api.NoBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

@NoBootstrap
public class PaperPlugManBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext bootstrapContext) {
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        var plugMan = new PlugMan();
        plugMan.commandCreator = new PaperCommandCreator();

        plugMan.hook = () -> {
            var initializer = new PaperInitializer(plugMan);

            var registry = plugMan.getServiceRegistry();
            var bukkitManager = registry.getPluginManager();

            bukkitManager = initializer.initializePaperPluginManager((BukkitPluginManager) bukkitManager);

            registry.unregister(PluginManager.class);
            registry.register(PluginManager.class, bukkitManager);

            initializer.showPaperWarningIfNeeded(bukkitManager);

            registry.unregister(ThreadUtil.class);
            registry.register(ThreadUtil.class, new PaperThreadUtil());
        };

        return plugMan;
    }
}
