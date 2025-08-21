package paper.com.rylinaux.plugman;

import bukkit.com.rylinaux.plugman.PlugManBukkit;
import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.ThreadUtil;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;
import paper.com.rylinaux.plugman.commands.OldPaperCommandCreator;
import paper.com.rylinaux.plugman.commands.PaperCommandCreator;
import paper.com.rylinaux.plugman.util.PaperThreadUtil;

@Slf4j
public class PaperPlugManBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext bootstrapContext) {
        // Not implementation required
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        var plugMan = new PlugManBukkit();

        try {
            plugMan.commandCreator = new PaperCommandCreator();
        } catch (Throwable throwable) {
            if (!(throwable instanceof ClassNotFoundException
                    || throwable instanceof NoClassDefFoundError
                    || throwable instanceof NoSuchMethodError
                    || throwable instanceof NoSuchMethodException)) throw new RuntimeException(throwable);

            plugMan.commandCreator = new OldPaperCommandCreator();
        }

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
