package core.com.rylinaux.plugman.commands.executables;

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

import core.com.rylinaux.plugman.PluginResult;
import core.com.rylinaux.plugman.commands.AbstractCommand;
import core.com.rylinaux.plugman.commands.CommandSender;
import core.com.rylinaux.plugman.config.PlugManConfigurationManager;
import core.com.rylinaux.plugman.plugins.Plugin;
import core.com.rylinaux.plugman.services.ServiceRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

abstract class CascadingPluginCommand extends AbstractCommand {
    private static final int CONFIRM_ALL_PLUGIN_THRESHOLD = 10;
    private static final String CONFIRM_ARGUMENT = "confirm";

    protected CascadingPluginCommand(CommandSender sender,
                                     String name,
                                     String description,
                                     String permission,
                                     String[] subPermissions,
                                     String usage,
                                     ServiceRegistry registry) {
        super(sender, name, description, permission, subPermissions, usage, registry);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!validateArguments(label, args, 2)) return;

        if (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("*")) {
            if (!hasPermission("all")) {
                sendNoPermissionMessage();
                return;
            }

            runAllPlugins(sender, args);
            return;
        }

        var target = getPluginManager().getPluginByName(args, 1);
        if (!validatePlugin(label, target)) return;

        runPluginWithCommandBatch(sender, target);
    }

    protected abstract String allSuccessMessage();

    protected abstract String allFailedMessage();

    protected abstract String allSummaryMessage();

    protected abstract String pluginSuccessMessage();

    protected boolean requiresAllConfirmation() {
        return false;
    }

    protected String allConfirmationMessage() {
        return null;
    }

    private void runAllPlugins(CommandSender sender, String[] args) {
        var managedPlugins = getPluginManager().getPlugins().stream().filter(plugin ->
                plugin != null && !getPluginManager().isIgnored(plugin)).toList();
        var plugins = managedPlugins.stream().filter(Plugin::isEnabled).toList();
        var skippedPlugins = managedPlugins.size() - plugins.size();

        if (requiresConfirmation(args, plugins.size())) {
            sender.sendMessage(allConfirmationMessage(), plugins.size(), CONFIRM_ARGUMENT);
            return;
        }

        var startedAt = System.nanoTime();
        var includeSoftDependencies = get(PlugManConfigurationManager.class).getPlugManConfig().shouldReloadSoftDependents();
        var dependencyPlan = createDependencyPlan(plugins, includeSoftDependencies);
        dependencyPlan.cycles().forEach(cycle -> sender.sendMessage("error.dependency-cycle", cycle));
        var result = executeBulkOperation(sender, dependencyPlan.loadOrder());
        sendBulkResult(sender, result, skippedPlugins, startedAt);
    }

    private BulkOperationResult executeBulkOperation(CommandSender sender, List<Plugin> loadOrder) {
        var unloadOrder = new ArrayList<>(loadOrder);
        Collections.reverse(unloadOrder);
        var unloadedPlugins = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        var failedPlugins = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        var successfulPlugins = 0;

        getPluginManager().beginCommandUpdateBatch();
        try {
            unloadPlugins(sender, unloadOrder, unloadedPlugins, failedPlugins);
            successfulPlugins = loadPlugins(sender, loadOrder, unloadedPlugins, failedPlugins);
        } finally {
            getPluginManager().endCommandUpdateBatch();
        }

        return new BulkOperationResult(successfulPlugins, failedPlugins);
    }

    private void unloadPlugins(CommandSender sender,
                               List<Plugin> unloadOrder,
                               Set<String> unloadedPlugins,
                               Set<String> failedPlugins) {
        for (var plugin : unloadOrder) {
            var result = getPluginManager().unload(plugin);
            if (result.success()) {
                unloadedPlugins.add(plugin.getName());
                continue;
            }

            sendOperationFailure(sender, plugin, result);
            failedPlugins.add(plugin.getName());
        }
    }

    private int loadPlugins(CommandSender sender,
                            List<Plugin> loadOrder,
                            Set<String> unloadedPlugins,
                            Set<String> failedPlugins) {
        var successfulPlugins = 0;
        for (var plugin : loadOrder) {
            if (!unloadedPlugins.contains(plugin.getName())) continue;

            var result = getPluginManager().load(plugin);
            if (result.success()) {
                successfulPlugins++;
                sender.sendMessage(pluginSuccessMessage(), plugin.getName());
                continue;
            }

            sendOperationFailure(sender, plugin, result);
            failedPlugins.add(plugin.getName());
        }
        return successfulPlugins;
    }

    private void sendOperationFailure(CommandSender sender, Plugin plugin, PluginResult result) {
        var messageArgs = result.messageArgs();
        sender.sendMessage(result.messageId(), messageArgs.length == 0
                ? new Object[]{plugin.getName()}
                : messageArgs);
    }

    private void sendBulkResult(CommandSender sender,
                                BulkOperationResult result,
                                int skippedPlugins,
                                long startedAt) {
        if (result.failedPlugins().isEmpty()) {
            sender.sendMessage(allSuccessMessage());
        } else {
            sender.sendMessage(allFailedMessage(), String.join(", ", result.failedPlugins()));
        }

        var elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        sender.sendMessage(allSummaryMessage(), result.successfulPlugins(),
                String.format(Locale.ROOT, "%.2f", elapsedSeconds), result.failedPlugins().size(), skippedPlugins);
    }

    static DependencyPlan createDependencyPlan(List<Plugin> plugins, boolean includeSoftDependencies) {
        var pluginsByName = new TreeMap<String, Plugin>(String.CASE_INSENSITIVE_ORDER);
        for (var plugin : plugins) pluginsByName.put(plugin.getName(), plugin);
        for (var plugin : plugins) {
            if (plugin.getProvides() == null) continue;
            for (var providedName : plugin.getProvides()) {
                if (providedName != null) pluginsByName.putIfAbsent(providedName, plugin);
            }
        }

        var loadOrder = new ArrayList<Plugin>();
        var states = new TreeMap<String, VisitState>(String.CASE_INSENSITIVE_ORDER);
        var path = new ArrayList<Plugin>();
        var cycles = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        for (var plugin : plugins) {
            visitDependencies(plugin, pluginsByName, includeSoftDependencies, states, path, cycles, loadOrder);
        }

        return new DependencyPlan(loadOrder, new ArrayList<>(cycles));
    }

    private static void visitDependencies(Plugin plugin,
                                          Map<String, Plugin> pluginsByName,
                                          boolean includeSoftDependencies,
                                          Map<String, VisitState> states,
                                          List<Plugin> path,
                                          Set<String> cycles,
                                          List<Plugin> loadOrder) {
        var state = states.get(plugin.getName());
        if (state == VisitState.VISITED) return;
        if (state == VisitState.VISITING) {
            addDependencyCycle(plugin, path, cycles);
            return;
        }

        states.put(plugin.getName(), VisitState.VISITING);
        path.add(plugin);

        visitDependencies(plugin.getDepend(), pluginsByName, includeSoftDependencies, states, path, cycles, loadOrder);
        if (includeSoftDependencies) {
            visitDependencies(plugin.getSoftDepend(), pluginsByName, true, states, path, cycles, loadOrder);
        }

        path.remove(path.size() - 1);
        states.put(plugin.getName(), VisitState.VISITED);
        loadOrder.add(plugin);
    }

    private static void visitDependencies(List<String> dependencyNames,
                                          Map<String, Plugin> pluginsByName,
                                          boolean includeSoftDependencies,
                                          Map<String, VisitState> states,
                                          List<Plugin> path,
                                          Set<String> cycles,
                                          List<Plugin> loadOrder) {
        if (dependencyNames == null) return;

        for (var dependencyName : dependencyNames) {
            if (dependencyName == null) continue;
            var dependency = pluginsByName.get(dependencyName);
            if (dependency == null) continue;
            visitDependencies(dependency, pluginsByName, includeSoftDependencies, states, path, cycles, loadOrder);
        }
    }

    private static void addDependencyCycle(Plugin repeatedPlugin, List<Plugin> path, Set<String> cycles) {
        var cycleStart = 0;
        while (cycleStart < path.size()
                && !path.get(cycleStart).getName().equalsIgnoreCase(repeatedPlugin.getName())) {
            cycleStart++;
        }

        var cycle = new ArrayList<String>();
        for (var index = cycleStart; index < path.size(); index++) cycle.add(path.get(index).getName());
        cycle.add(repeatedPlugin.getName());
        cycles.add(String.join(" -> ", cycle));
    }

    record DependencyPlan(List<Plugin> loadOrder, List<String> cycles) {
        DependencyPlan {
            loadOrder = List.copyOf(loadOrder);
            cycles = List.copyOf(cycles);
        }
    }

    private record BulkOperationResult(int successfulPlugins, Set<String> failedPlugins) {
        private BulkOperationResult {
            var sortedFailures = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            sortedFailures.addAll(failedPlugins);
            failedPlugins = Collections.unmodifiableSet(sortedFailures);
        }
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private boolean requiresConfirmation(String[] args, int pluginCount) {
        return requiresAllConfirmation()
                && pluginCount > CONFIRM_ALL_PLUGIN_THRESHOLD
                && (args.length < 3 || !args[2].equalsIgnoreCase(CONFIRM_ARGUMENT));
    }

    private boolean runPluginWithCommandBatch(CommandSender sender, Plugin target) {
        getPluginManager().beginCommandUpdateBatch();
        try {
            return runPlugin(sender, target);
        } finally {
            getPluginManager().endCommandUpdateBatch();
        }
    }

    private boolean runPlugin(CommandSender sender, Plugin target) {
        if (target == null) {
            sendInvalidPluginMessage();
            return false;
        }

        var dependents = findEnabledDependents(target);
        var unloadedDependents = new ArrayList<Plugin>();

        for (var dependent : dependents) {
            var result = getPluginManager().unload(dependent);
            if (!result.success()) {
                sender.sendMessage(result.messageId(), dependent.getName());
                loadDependents(sender, unloadedDependents);
                return false;
            }

            unloadedDependents.add(dependent);
        }

        var result = getPluginManager().unload(target);
        if (!result.success()) {
            sender.sendMessage(result.messageId(), target.getName());
            loadDependents(sender, unloadedDependents);
            return false;
        }

        result = getPluginManager().load(target);
        if (!result.success()) {
            sender.sendMessage(result.messageId(), result.messageArgs().length == 0 ? new Object[]{target.getName()} : result.messageArgs());
            loadDependents(sender, unloadedDependents);
            return false;
        }

        if (!loadDependents(sender, unloadedDependents)) return false;

        sender.sendMessage(pluginSuccessMessage(), target.getName());
        return true;
    }

    private boolean loadDependents(CommandSender sender, List<Plugin> dependents) {
        for (var i = dependents.size() - 1; i >= 0; i--) {
            var dependent = dependents.get(i);

            var result = getPluginManager().load(dependent);
            if (result.success()) continue;

            sender.sendMessage(result.messageId(), result.messageArgs().length == 0 ? new Object[]{dependent.getName()} : result.messageArgs());
            return false;
        }

        return true;
    }

    private List<Plugin> findEnabledDependents(Plugin target) {
        var dependents = new ArrayList<Plugin>();
        if (!get(PlugManConfigurationManager.class).getPlugManConfig().shouldReloadHardDependents()) return dependents;

        var visited = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        visited.add(target.getName());
        collectEnabledDependents(target.getName(), dependents, visited);
        return dependents;
    }

    private void collectEnabledDependents(String targetName, List<Plugin> dependents, Set<String> visited) {
        for (var plugin : getPluginManager().getPlugins()) {
            if (plugin == null || !plugin.isEnabled()) continue;
            if (getPluginManager().isIgnored(plugin)) continue;
            if (visited.contains(plugin.getName())) continue;
            if (!dependsOn(plugin, targetName)) continue;

            visited.add(plugin.getName());
            collectEnabledDependents(plugin.getName(), dependents, visited);
            dependents.add(plugin);
        }
    }

    private boolean dependsOn(Plugin plugin, String targetName) {
        if (containsIgnoreCase(plugin.getDepend(), targetName)) return true;

        return get(PlugManConfigurationManager.class).getPlugManConfig().shouldReloadSoftDependents()
                && containsIgnoreCase(plugin.getSoftDepend(), targetName);
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        return values != null && values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }
}
