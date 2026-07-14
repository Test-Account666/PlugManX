package paper.com.rylinaux.plugman.util;

/*
 * #%L
 * PlugMan
 * %%
 * Copyright (C) 2010 - 2014 PlugMan
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import lombok.experimental.UtilityClass;

@UtilityClass
public class PaperReflectionNames {
    public static final String PAPER_PLUGIN_MANAGER = "io.papermc.paper.plugin.manager.PaperPluginManagerImpl";
    public static final String LAUNCH_ENTRYPOINT_HANDLER = "io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler";
    public static final String ENTRYPOINT = "io.papermc.paper.plugin.entrypoint.Entrypoint";
    public static final String ENTRYPOINT_HANDLER = "io.papermc.paper.plugin.entrypoint.EntrypointHandler";
    public static final String SIMPLE_PROVIDER_STORAGE = "io.papermc.paper.plugin.storage.SimpleProviderStorage";
    public static final String SERVER_PLUGIN_PROVIDER_STORAGE = "io.papermc.paper.plugin.storage.ServerPluginProviderStorage";
    public static final String BOOTSTRAP_PROVIDER_STORAGE = "io.papermc.paper.plugin.storage.BootstrapProviderStorage";
    public static final String PLUGIN_PROVIDER = "io.papermc.paper.plugin.provider.PluginProvider";
    public static final String PAPER_SERVER_PLUGIN_PROVIDER = "io.papermc.paper.plugin.provider.type.paper.PaperPluginParent$PaperServerPluginProvider";
    public static final String FILE_PROVIDER_SOURCE = "io.papermc.paper.plugin.provider.source.FileProviderSource";
    public static final String REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer";
    public static final String LIFECYCLE_EVENTS = "io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents";
    public static final String LIFECYCLE_EVENT_RUNNER = "io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner";
    public static final String PAPER_COMMANDS = "io.papermc.paper.command.brigadier.PaperCommands";
    public static final String RELOADABLE_REGISTRAR_EVENT_CAUSE = "io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent$Cause";
    public static final String RELOADABLE_REGISTRAR_EVENT = "io.papermc.paper.plugin.lifecycle.event.registrar.RegistrarEventImpl$ReloadableImpl";
    public static final String PAPER_REGISTRAR = "io.papermc.paper.plugin.lifecycle.event.registrar.PaperRegistrar";
    public static final String LIFECYCLE_EVENT_OWNER = "io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner";
    public static final String SAFE_CLASS_DEFINER = "com.destroystokyo.paper.event.executor.asm.SafeClassDefiner";

    public static final String INSTANCE_FIELD = "INSTANCE";
    public static final String PLUGIN_FIELD = "PLUGIN";
    public static final String BOOTSTRAPPER_FIELD = "BOOTSTRAPPER";
    public static final String COMMANDS_FIELD = "COMMANDS";
    public static final String RELOAD_FIELD = "RELOAD";
}
