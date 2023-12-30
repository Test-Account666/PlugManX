package com.rylinaux.plugman;

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

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.rylinaux.plugman.messaging.MessageFormatter;
import com.rylinaux.plugman.util.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

/**
 * Plugin manager for Bukkit servers.
 *
 * @author rylinaux
 */
public class PlugMan extends JavaPlugin {
    private static boolean createdDummyPlugMan = false;
    /**
     * The instance of the plugin
     */
    private static PlugMan instance = null;

    static {
        try {
            PluginDescriptionFile.class.getDeclaredField("provides");
            if (new File("plugins", "PlugManDummy.jar").exists()) new File("plugins", "PlugManDummy.jar").delete();
        } catch (NoSuchFieldError | NoSuchFieldException ignored) {
            if (!new File("plugins", "PlugManDummy.jar").exists())
                System.out.println("'Legacy' server version detected, please restart the server in order to load Dummy PlugMan, please ignore all UnknownDependencyException messages about PlugMan if you did not restart.");
            PlugMan.saveResourceStatic("PlugManDummy.jar", true);
            PlugMan.createdDummyPlugMan = true;
        }
    }

    /**
     * HashMap that contains all mappings from resourcemaps.yml
     */
    private final HashMap<String, Map.Entry<Long, Boolean>> resourceMap = new HashMap<>();
    /**
     * Stores all file names + hashes for auto (re/un)load
     */
    private final HashMap<String, String> fileHashMap = new HashMap<>();
    /**
     * Stores all file names + plugin names for auto unload
     */
    private final HashMap<String, String> filePluginMap = new HashMap<>();
    private PluginUtil pluginUtil;
    private boolean notifyOnBrokenCommandRemoval;
    private boolean disableDownloadCommand;
    private Field lookupNamesField = null;
    /**
     * The command manager which adds all command we want so 1.13+ players can instantly tab-complete them
     */
    private BukkitCommandWrap bukkitCommandWrap = null;
    /**
     * List of plugins to ignore, partially.
     */
    private List<String> ignoredPlugins = null;
    /**
     * The message manager
     */
    private MessageFormatter messageFormatter = null;

    private static InputStream getResourceStatic(String filename) {
        try {
            URL url = PlugMan.class.getClassLoader().getResource(filename);
            if (url == null) return null;
            else {
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                return connection.getInputStream();
            }
        } catch (IOException var4) {
            return null;
        }
    }

    public static void saveResourceStatic(String resourcePath, boolean replace) {
        File dataFolder = new File("plugins");
        if (resourcePath != null && !resourcePath.equals("")) {
            resourcePath = resourcePath.replace('\\', '/');
            InputStream in = PlugMan.getResourceStatic(resourcePath);
            if (in == null)
                throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in PlugManX");
            else {
                File outFile = new File(dataFolder, resourcePath);
                int lastIndex = resourcePath.lastIndexOf(47);
                File outDir = new File(dataFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));
                if (!outDir.exists()) outDir.mkdirs();

                try {
                    OutputStream out = new FileOutputStream(outFile);
                    byte[] buf = new byte[1024];

                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);

                    out.close();
                    in.close();
                } catch (IOException var10) {
                    var10.printStackTrace();
                    System.out.println("Could not save " + outFile.getName() + " to " + outFile);
                }

            }
        } else throw new IllegalArgumentException("ResourcePath cannot be null or empty");
    }

    /**
     * Returns the instance of the plugin.
     *
     * @return the instance of the plugin
     */
    public static PlugMan getInstance() {
        return PlugMan.instance;
    }

    public PluginUtil getPluginUtil() {
        return this.pluginUtil;
    }

    /**
     * @return = If PlugManX should notify if a broken command is removed
     */
    public boolean isNotifyOnBrokenCommandRemoval() {
        return this.notifyOnBrokenCommandRemoval;
    }

    /**
     * @return = If PlugManX should disable the download command
     */
    public boolean isDisableDownloadCommand() {
        return disableDownloadCommand;
    }

    /**
     * For older server versions: Adds "PlugManX" as "PlugMan" to "lookupNames" field of "SimplePluginManager"
     * This is needed because of plugins which depend on "PlugMan", but server has "PlugManX" installed
     * Not needed on newer versions, because of new "provides" keyword in plugin.yml
     */
    private void addPluginToList() {
        try {
            this.lookupNamesField = SimplePluginManager.class.getDeclaredField("lookupNames");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (this.lookupNamesField == null) {
            Bukkit.getLogger().severe("Failed to add PlugMan to plugin list!\nNot a bukkit environment?");
            return;
        }

        this.lookupNamesField.setAccessible(true);

        Map lookupNames = null;
        try {
            lookupNames = (Map) this.lookupNamesField.get(Bukkit.getPluginManager());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (lookupNames == null) {
            Bukkit.getLogger().severe("Failed to add PlugMan to plugin list!\nNot a bukkit environment?");
            return;
        }

        lookupNames.remove("PlugMan");
        lookupNames.put("PlugMan", this);
    }

    @Override
    public void onLoad() {
        if (PlugMan.createdDummyPlugMan)
            this.addPluginToList();
    }

    @Override
    public void onEnable() {

        try {
            Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");

            this.pluginUtil = new PaperPluginUtil(new BukkitPluginUtil());
        } catch (Throwable ignored) {
            this.pluginUtil = new BukkitPluginUtil();
        }

        if (this.pluginUtil instanceof PaperPluginUtil) {
            Bukkit.getLogger().warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            Bukkit.getLogger().warning("It seems like you're running on paper.");
            Bukkit.getLogger().warning("PlugManX cannot interact with paper-plugins, yet.");
            Bukkit.getLogger().warning("Also, if you encounter any issues, please join my discord: https://discord.gg/GxEFhVY6ff");
            Bukkit.getLogger().warning("Or create an issue on GitHub: https://github.com/TheBlackEntity/PlugMan");
            Bukkit.getLogger().warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        }

        PlugMan.instance = this;

        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists())
            saveResource("messages.yml", true);

        if (!new File(getDataFolder(), "messages_jp.yml").exists())
            saveResource("messages_jp.yml", true);

        if (!new File(getDataFolder(), "messages_de.yml").exists())
            saveResource("messages_de.yml", true);
        //add zh_CN
        if (!new File(getDataFolder(), "messages_zh_CN.yml").exists())
            saveResource("messages_zh_CN.yml", true);

        FileConfiguration messageConfiguration = YamlConfiguration.loadConfiguration(messagesFile);

        if (!messageConfiguration.isSet("error.paper-plugin")) try {
            messageConfiguration.set("error.paper-plugin", "&cPaper plugins are currently not supported, I'm sorry.");
            messageConfiguration.save(messagesFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.messageFormatter = new MessageFormatter();

        this.getCommand("plugman").setExecutor(new PlugManCommandHandler());
        this.getCommand("plugman").setTabCompleter(new PlugManTabCompleter());

        this.initConfig();

        try {
            Class.forName("com.mojang.brigadier.CommandDispatcher");
            this.bukkitCommandWrap = new BukkitCommandWrap();
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            this.bukkitCommandWrap = new BukkitCommandWrap_Useless();
        }

        for (File file : new File("plugins").listFiles()) {
            if (file.isDirectory()) continue;
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
            String hash = null;
            try {
                hash = Files.asByteSource(file).hash(Hashing.md5()).toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.fileHashMap.put(file.getName(), hash);

            JarFile jarFile = null;
            try {
                jarFile = new JarFile(file);
            } catch (IOException e) {
                if (e instanceof ZipException) {
                    System.out.println("Possible broken plugin detected: " + file.getName());
                    continue;
                }
                e.printStackTrace();
                continue;
            }

            if (jarFile.getEntry("plugin.yml") == null) continue;

            InputStream stream;
            try {
                stream = jarFile.getInputStream(jarFile.getEntry("plugin.yml"));
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            if (stream == null) continue;

            PluginDescriptionFile descriptionFile = null;
            try {
                descriptionFile = new PluginDescriptionFile(stream);
            } catch (InvalidDescriptionException e) {
                e.printStackTrace();
                continue;
            }

            this.filePluginMap.put(file.getName(), descriptionFile.getName());
        }

        this.notifyOnBrokenCommandRemoval = this.getConfig().getBoolean("notify-on-broken-command-removal", true);

        this.disableDownloadCommand = this.getConfig().getBoolean("disable-download-command", false);

        boolean alerted = false;

        if (this.getConfig().getBoolean("auto-load.enabled", false)) {
            Bukkit.getLogger().warning("!!! The auto (re/un)load feature can break plugins, use with caution !!!");
            Bukkit.getLogger().warning("If anything breaks, a restart will probably fix it!");
            alerted = true;
            Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
                if (!new File("plugins").isDirectory()) return;
                for (File file : Arrays.stream(new File("plugins").listFiles()).filter(File::isFile).filter(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")).collect(Collectors.toList()))
                    if (!this.fileHashMap.containsKey(file.getName())) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.getConsoleSender().sendMessage(PlugMan.getInstance().getPluginUtil().load(file.getName().replace(".jar", "")));
                        });
                        String hash = null;
                        try {
                            hash = Files.asByteSource(file).hash(Hashing.md5()).toString();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        this.fileHashMap.put(file.getName(), hash);
                    }
            }, this.getConfig().getLong("auto-load.check-every-seconds") * 20, this.getConfig().getLong("auto-load.check-every-seconds") * 20);
        }

        if (this.getConfig().getBoolean("auto-unload.enabled", false)) {
            if (!alerted) {
                Bukkit.getLogger().warning("!!! The auto (re/un)load feature can break plugins, use with caution !!!");
                Bukkit.getLogger().warning("If anything breaks, a restart will probably fix it!");
                alerted = true;
            }
            Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
                if (!new File("plugins").isDirectory()) return;
                for (String fileName : this.fileHashMap.keySet())
                    if (!new File("plugins", fileName).exists()) {
                        Plugin plugin = Bukkit.getPluginManager().getPlugin(this.filePluginMap.get(fileName));
                        if (plugin == null) {
                            this.fileHashMap.remove(fileName);
                            this.filePluginMap.remove(fileName);
                            continue;
                        }
                        if (PlugMan.getInstance().getPluginUtil().isIgnored(plugin)) continue;
                        this.fileHashMap.remove(fileName);
                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.getConsoleSender().sendMessage(PlugMan.getInstance().getPluginUtil().unload(plugin));
                        });
                    }
            }, this.getConfig().getLong("auto-unload.check-every-seconds") * 20, this.getConfig().getLong("auto-unload.check-every-seconds") * 20);
        }

        if (this.getConfig().getBoolean("auto-reload.enabled", false)) {
            if (!alerted) {
                Bukkit.getLogger().warning("!!! The auto (re/un)load feature can break plugins, use with caution !!!");
                Bukkit.getLogger().warning("If anything breaks, a restart will probably fix it!");
                alerted = true;
            }
            Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
                if (!new File("plugins").isDirectory()) return;
                for (File file : Arrays.stream(new File("plugins").listFiles()).filter(File::isFile).filter(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")).collect(Collectors.toList())) {
                    if (!this.fileHashMap.containsKey(file.getName())) continue;
                    String hash = null;
                    try {
                        hash = Files.asByteSource(file).hash(Hashing.md5()).toString();
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (!hash.equalsIgnoreCase(this.fileHashMap.get(file.getName()))) {
                        Plugin plugin = Bukkit.getPluginManager().getPlugin(this.filePluginMap.get(file.getName()));
                        if (plugin == null) {
                            this.fileHashMap.remove(file.getName());
                            this.filePluginMap.remove(file.getName());
                            continue;
                        }

                        if (PlugMan.getInstance().getPluginUtil().isIgnored(plugin)) continue;

                        this.fileHashMap.remove(file.getName());
                        this.fileHashMap.put(file.getName(), hash);

                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.getConsoleSender().sendMessage(PlugMan.getInstance().getPluginUtil().unload(plugin));
                            Bukkit.getConsoleSender().sendMessage(PlugMan.getInstance().getPluginUtil().load(plugin.getName()));
                        });
                    }
                }
            }, this.getConfig().getLong("auto-reload.check-every-seconds") * 20, this.getConfig().getLong("auto-reload.check-every-seconds") * 20);
        }
    }

    @Override
    public void onDisable() {
        PlugMan.instance = null;
        this.messageFormatter = null;
        this.ignoredPlugins = null;
    }

    /**
     * Copy default config values
     */
    private void initConfig() {
        this.saveDefaultConfig();

        if (!this.getConfig().isSet("auto-load.enabled") || !this.getConfig().isSet("auto-unload.enabled") || !this.getConfig().isSet("auto-reload.enabled") || !this.getConfig().isSet("ignored-plugins") || !this.getConfig().isSet("notify-on-broken-command-removal")) {
            Bukkit.getLogger().severe("Invalid PlugMan config detected! Creating new one...");
            new File("plugins" + File.separator + "PlugManX", "config.yml").renameTo(new File("plugins" + File.separator + "PlugManX", "config.yml.old-" + System.currentTimeMillis()));
            this.saveDefaultConfig();
            Bukkit.getLogger().info("New config created!");
        }

        int configVersion = 0;

        if (getConfig().isSet("version")) {
            configVersion = getConfig().getInt("version");
        }

        if (configVersion < 1) {
            getConfig().set("version", 1);
            getConfig().set("disable-download-command", true);

            saveConfig();

            reloadConfig();


            Bukkit.getLogger().warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            Bukkit.getLogger().warning("PlugManX disabled the download command for you!");
            Bukkit.getLogger().warning("If you don't use it, please keep it disabled, as it can expose your server to vulnerabilities!");
            Bukkit.getLogger().warning("This message will only display once!");
            Bukkit.getLogger().warning("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        }

        this.ignoredPlugins = this.getConfig().getStringList("ignored-plugins");

        this.ignoredPlugins.add("PlugMan");
        this.ignoredPlugins.add("PlugManX");

        File resourcemapFile = new File(this.getDataFolder(), "resourcemaps.yml");
        if (!resourcemapFile.exists()) this.saveResource("resourcemaps.yml", true);

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(resourcemapFile);
        this.resourceMap.clear();
        for (String name : cfg.getConfigurationSection("Resources").getKeys(false)) {
            if (name.equalsIgnoreCase("PlugMan")) continue;
            try {
                long id = cfg.getLong("Resources." + name + ".ID");
                boolean spigotmc = cfg.getBoolean("Resources." + name + ".spigotmc");
                this.resourceMap.put(name.toLowerCase(Locale.ROOT), new Map.Entry<Long, Boolean>() {
                    @Override
                    public Long getKey() {
                        return id;
                    }

                    @Override
                    public Boolean getValue() {
                        return spigotmc;
                    }

                    @Override
                    public Boolean setValue(Boolean value) {
                        return spigotmc;
                    }
                });
            } catch (Exception e) {
                this.getLogger().severe("An error occurred while trying to load mappings for '" + name + "'");
                e.printStackTrace();
            }

        }

        this.resourceMap.put("plugman", new Map.Entry<Long, Boolean>() {
            @Override
            public Long getKey() {
                return 88135L;
            }

            @Override
            public Boolean getValue() {
                return true;
            }

            @Override
            public Boolean setValue(Boolean value) {
                return true;
            }
        });

        this.resourceMap.put("plugmanx", new Map.Entry<Long, Boolean>() {
            @Override
            public Long getKey() {
                return 88135L;
            }

            @Override
            public Boolean getValue() {
                return true;
            }

            @Override
            public Boolean setValue(Boolean value) {
                return true;
            }
        });
    }

    /**
     * Returns the list of ignored plugins.
     *
     * @return the ignored plugins
     */
    public List<String> getIgnoredPlugins() {
        return this.ignoredPlugins;
    }

    /**
     * Returns the message manager.
     *
     * @return the message manager
     */
    public MessageFormatter getMessageFormatter() {
        return this.messageFormatter;
    }

    /**
     * Returns the command manager.
     *
     * @return the command manager
     */
    public BukkitCommandWrap getBukkitCommandWrap() {
        return this.bukkitCommandWrap;
    }

    public HashMap<String, Map.Entry<Long, Boolean>> getResourceMap() {
        return this.resourceMap;
    }

    public HashMap<String, String> getFilePluginMap() {
        return this.filePluginMap;
    }
}
