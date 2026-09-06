# PlugManX Development Notes

This document covers maintenance tasks that are easy to miss when adding messages or updating Paper reload support.

## Adding Messages

Messages must work for new installations and existing installations with customized files.

1. Add the key to every bundled resource file:
   - `plugman-core/src/main/resources/messages.yml`
   - `plugman-core/src/main/resources/messages_de.yml`
   - `plugman-core/src/main/resources/messages_es.yml`
   - `plugman-core/src/main/resources/messages_cn.yml`
   - `plugman-core/src/main/resources/messages_jp.yml`
   - `plugman-core/src/main/resources/messages_ru.yml`
   - `plugman-core/src/main/resources/messages_tw.yml`
2. Add the default text to the appropriate defaults record in `MessageMigrationService`.
   General messages use `MessageDefaults`; bulk reload messages currently use `BulkMessageDefaults`.
3. Register the key in `MessageMigrationService#addMissingEntries` with `addMissingMessageEntry`.
   Use `addMissingNestedMessageEntry` only for nested sections such as `error.usage`.
4. Keep placeholder indexes identical in every language. For example, `{0}` and `{1}` must have the same meaning everywhere.
5. Do not replace an existing key during migration. The migration service must only backfill missing entries so customized messages remain untouched.

`PlugManConfigurationManager` runs the message migration during normal configuration loading, including for users already on the current config version. A config version bump is therefore not required when only adding message keys.

When a new command is added, also check its help message, permission in `plugin.yml`, command handler registration, tab completion, README command table, and all bundled language files.

## Paper API Changes

Paper plugin loading and unloading uses internal APIs that may change without compatibility guarantees. Prefer runtime capability checks and signature-based reflection over a Minecraft version check whenever possible.

### Finding the Relevant Paper API

Paper uses different JARs for its public API and its internal server implementation. Check the JAR that actually owns the changed class before modifying PlugManX.

#### Public Paper API

The public API is normally stored in the local Maven repository:

Windows:

```text
%USERPROFILE%\.m2\repository\io\papermc\paper\paper-api\<version>\paper-api-<version>.jar
```

Linux:

```text
~/.m2/repository/io/papermc/paper/paper-api/<version>/paper-api-<version>.jar
```

This JAR contains supported types such as events, lifecycle API interfaces, command APIs, plugin metadata interfaces, and general Bukkit/Paper API classes.

Search all cached Paper API versions on Windows with PowerShell:

```powershell
Get-ChildItem "$env:USERPROFILE\.m2\repository\io\papermc\paper\paper-api" -Recurse -Filter "*.jar"
```

Search them on Linux:

```bash
find "$HOME/.m2/repository/io/papermc/paper/paper-api" -type f -name '*.jar'
```

#### Paper Server Internals

PlugManX reload support also uses Paper implementation classes that are not included in `paper-api`. The Paper launcher usually creates the full runtime JAR inside the server directory:

Windows:

```text
<server>\versions\<version>\paper-<version>.jar
```

Linux:

```text
<server>/versions/<version>/paper-<version>.jar
```

For example on Windows:

```text
versions\26.2\paper-26.2.jar
```

On Linux:

```text
versions/26.2/paper-26.2.jar
```

Use this generated runtime JAR for provider storage, plugin manager implementation, lifecycle internals, CraftBukkit, and NMS inspection. The small Paper launcher JAR in the server root may only bootstrap or patch the server and may not contain the final runtime classes.

Find possible runtime JARs from the server root with:

Windows PowerShell:

```powershell
Get-ChildItem -Recurse -Filter "paper-*.jar"
```

Linux:

```bash
find . -type f -name 'paper-*.jar'
```

Common package locations are:

| Area | Package or class location |
| --- | --- |
| Paper plugin manager | `io.papermc.paper.plugin.manager` |
| Entrypoints | `io.papermc.paper.plugin.entrypoint` |
| Provider sources | `io.papermc.paper.plugin.provider.source` |
| Provider types | `io.papermc.paper.plugin.provider.type` |
| Provider storage | `io.papermc.paper.plugin.storage` |
| Lifecycle events | `io.papermc.paper.plugin.lifecycle` |
| Paper commands | `io.papermc.paper.command.brigadier` |
| CraftBukkit implementation | `org.bukkit.craftbukkit` |
| Minecraft server internals | `net.minecraft` |
| Recipe internals | `net.minecraft.world.item.crafting` |

#### Inspecting Classes

List matching classes before guessing a package or nested class name:

Windows PowerShell:

```powershell
jar tf .\versions\26.2\paper-26.2.jar | Select-String "FileProviderSource|ProviderStorage|RecipeMap"
```

Linux:

```bash
jar tf ./versions/26.2/paper-26.2.jar | grep -E 'FileProviderSource|ProviderStorage|RecipeMap'
```

Use `javap` from the same JDK that runs or builds the server to inspect fields, method signatures, and bytecode:

Windows PowerShell:

```powershell
& "$env:JAVA_HOME\bin\javap.exe" -classpath .\versions\26.2\paper-26.2.jar -p `
  io.papermc.paper.plugin.provider.source.FileProviderSource

& "$env:JAVA_HOME\bin\javap.exe" -classpath .\versions\26.2\paper-26.2.jar -p -c `
  net.minecraft.world.item.crafting.RecipeManager
```

Linux:

```bash
"$JAVA_HOME/bin/javap" -classpath ./versions/26.2/paper-26.2.jar -p \
  io.papermc.paper.plugin.provider.source.FileProviderSource

"$JAVA_HOME/bin/javap" -classpath ./versions/26.2/paper-26.2.jar -p -c \
  net.minecraft.world.item.crafting.RecipeManager
```

Useful `javap` options:

- `-p` shows private and package-private members.
- `-c` shows method bytecode and reveals which internal method is actually called.
- `-s` shows JVM descriptors, which helps distinguish overloaded methods.

For nested classes, use the exact name returned by `jar tf`, including `$`, and quote it in the shell:

Windows PowerShell:

```powershell
& "$env:JAVA_HOME\bin\javap.exe" -classpath .\versions\26.2\paper-26.2.jar -p `
  'io.papermc.paper.plugin.provider.type.paper.PaperPluginParent$PaperServerPluginProvider'
```

Linux:

```bash
"$JAVA_HOME/bin/javap" -classpath ./versions/26.2/paper-26.2.jar -p \
  'io.papermc.paper.plugin.provider.type.paper.PaperPluginParent$PaperServerPluginProvider'
```

An IDE decompiler can be used for easier reading, but confirm reflection targets with `javap -p -s` against the actual runtime JAR. Paper source code and development bundles are useful references, but the generated JAR from the affected server is the final source of truth for a reported runtime failure.

When comparing Paper versions, run the same `jar tf` and `javap` commands against both runtime JARs. Record renamed classes, changed parameter types, new overloads, moved fields, and changed return types before adding a compatibility branch.

### Small API Adaptation Tutorial

Assume a new Paper version produces this debug message during recipe cleanup:

```text
NoSuchMethodException: location method not found in net.minecraft.resources.ResourceKey
```

1. Find the affected class in the runtime JAR.

   Windows PowerShell:

   ```powershell
   jar tf .\versions\26.2\paper-26.2.jar | Select-String "net/minecraft/resources/ResourceKey"
   ```

   Linux:

   ```bash
   jar tf ./versions/26.2/paper-26.2.jar | grep 'net/minecraft/resources/ResourceKey'
   ```

2. Inspect the available methods in both the previously working JAR and the new JAR.

   Windows PowerShell:

   ```powershell
   & "$env:JAVA_HOME\bin\javap.exe" -classpath .\versions\26.1\paper-26.1.jar -p -s net.minecraft.resources.ResourceKey
   & "$env:JAVA_HOME\bin\javap.exe" -classpath .\versions\26.2\paper-26.2.jar -p -s net.minecraft.resources.ResourceKey
   ```

   Linux:

   ```bash
   "$JAVA_HOME/bin/javap" -classpath ./versions/26.1/paper-26.1.jar -p -s net.minecraft.resources.ResourceKey
   "$JAVA_HOME/bin/javap" -classpath ./versions/26.2/paper-26.2.jar -p -s net.minecraft.resources.ResourceKey
   ```

3. Identify the smallest compatibility change. If `location()` was replaced by `identifier()`, keep support for both instead of checking only the Paper version:

   ```java
   private String resourceKeyString(Object key) throws ReflectiveOperationException {
       try {
           return String.valueOf(invokeNoArgMethod(key, "location"));
       } catch (NoSuchMethodException ignored) {
           // Newer Paper versions expose the identifier under a different method name.
       }

       try {
           return String.valueOf(invokeNoArgMethod(key, "identifier"));
       } catch (NoSuchMethodException ignored) {
           return String.valueOf(key);
       }
   }
   ```

4. Log which branch was selected when `paperReloadDebug` is enabled. This makes the next compatibility report easier to diagnose.
5. Test the same build on both Paper versions. Verify the complete reload twice, not only the reflection call, because stale state often appears on the second reload.

The same process applies when a method changes parameters. For example, if `FileProviderSource#prepareContext` changes, inspect all overloads with `javap -p -s`, then attempt compatible signatures in a safe order such as `Path`, `Object`, and `String`. If the method is optional on an older supported version, keep a documented fallback instead of treating its absence as a fatal error.

Do not catch every reflection error and silently continue. Only `NoSuchMethodException` or another expected capability mismatch should select a fallback. Invocation failures from a method that was found should be logged and treated as a real load or unload failure.

### Main Compatibility Areas

- `PaperReflectionNames`: Paper internal class and field names.
- `PaperInitializer`: selection of legacy or modern reload strategies.
- `PaperPlugManLoader`: Paper library-loader compatibility range.
- `PaperPluginManager#loadPaperPluginWithProviderStorage`: provider creation, registration, and enable flow.
- `prepareProviderContext` and `registerProviders`: `FileProviderSource` method signatures.
- `invokeStorageRegister`, `invokeStorageEnter`, and `invokeStorageProcessProvided`: provider storage lifecycle.
- `createDependencyTree` and `invokeDependencyTreeAdd`: dependency-tree method names and overloads.
- Paper command lifecycle handling and lifecycle-owner context setup.
- `PaperPluginManager#unloadWithPaper`: common listeners, commands, permissions, recipes, and plugin-list cleanup.
- `PaperPluginManager#cleanupPaperPluginManager`: Paper instance-manager cleanup.
- `ModernPaperPluginManager`: provider storage, provider caches, event executors, and `SafeClassDefiner` cleanup.
- Recipe cleanup through Paper's internal `RecipeMap`; field names and recipe key methods can change between releases.

### Update Checklist

1. Reproduce the failure with `paperReloadDebug: true` and record the failing step and complete exception.
2. Compare the affected Paper classes and method signatures with the last supported version.
3. Update `PaperReflectionNames` when a class or field moved.
4. Add a runtime capability branch for changed methods or overloads. Keep the older branch while its Paper versions remain supported.
5. Match methods by compatible parameter types, not only by method name, when Paper exposes multiple overloads.
6. Verify both sides of the lifecycle: anything added to provider storage, commands, listeners, recipes, caches, or classloader registries during load must be removed during unload.
7. Keep failure behavior explicit. Once Paper provider loading starts, do not fall back to the Bukkit load path if that could register the same plugin twice.
8. Only raise version gates such as `MAX_LIBRARY_LOADER_VERSION` after the new Paper version has been tested. Do not assume an unchanged API from the version number alone.
9. Extend debug output for a new compatibility branch so future reports show which capability and overload were selected.

### Reload Test Matrix

Test at least the oldest supported Paper release, Paper 1.21.8, the current stable release, and the upcoming release before expanding a version gate.

For each version, test both `plugin.yml` and `paper-plugin.yml` plugins with:

- Commands and Paper lifecycle commands
- Hard and soft dependencies, including `provides`
- Listeners, scheduler tasks, and services
- Custom recipes
- Repeated load, unload, reload, and restart operations
- `reload all` with dependency ordering
- At least one online player, because command and recipe synchronization can follow a different and more expensive path

After unload, check that the unload leak detector reports no remaining tasks, services, listeners, commands, or plugin-owned threads. Also verify that a second load does not produce duplicate plugin, command, provider, lifecycle, or recipe registrations.

## Verification

Run the complete reactor build after maintenance changes:

Windows PowerShell:

```powershell
mvn.cmd clean package "-Dlicense.skipUpdateLicense=true"
```

Linux:

```bash
mvn clean package -Dlicense.skipUpdateLicense=true
```

The build must complete with all tests passing. The assembled server plugin is written to `target/PlugManX.jar`.
