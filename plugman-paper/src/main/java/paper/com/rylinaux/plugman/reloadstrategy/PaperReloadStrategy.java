package paper.com.rylinaux.plugman.reloadstrategy;

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

import bukkit.com.rylinaux.plugman.pluginmanager.BukkitPluginManager;
import core.com.rylinaux.plugman.plugins.PluginManager;
import core.com.rylinaux.plugman.util.reflection.ClassAccessor;
import paper.com.rylinaux.plugman.util.PaperReflectionNames;

public interface PaperReloadStrategy {

    String getName();

    PluginManager createPluginManager(BukkitPluginManager bukkitPluginManager);

    default boolean isModernLifecycleAvailable() {
        return ClassAccessor.classExists(PaperReflectionNames.LIFECYCLE_EVENTS)
                && ClassAccessor.classExists(PaperReflectionNames.LIFECYCLE_EVENT_RUNNER)
                && ClassAccessor.classExists(PaperReflectionNames.PAPER_COMMANDS)
                && ClassAccessor.classExists(PaperReflectionNames.RELOADABLE_REGISTRAR_EVENT_CAUSE);
    }
}
