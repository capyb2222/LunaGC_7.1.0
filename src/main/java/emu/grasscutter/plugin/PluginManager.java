package emu.grasscutter.plugin;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.event.*;
import emu.grasscutter.utils.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import javax.annotation.Nullable;
import lombok.*;

public final class PluginManager {
    @SuppressWarnings("FieldCanBeLocal")
    public static int API_VERSION = 2;

    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    private final Map<Class<? extends Event>, List<EventHandler<? extends Event>>> handlers =
            new LinkedHashMap<>();

    public PluginManager() {
        this.loadPlugins();
    }

    private void loadPlugins() {
        File pluginsDir = FileUtils.getPluginPath("").toFile();
        if (!pluginsDir.exists() && !pluginsDir.mkdirs()) {
            Grasscutter.getLogger()
                    .error(translate("plugin.directory_failed", pluginsDir.getAbsolutePath()));
            return;
        }

        File[] files = pluginsDir.listFiles();
        if (files == null) {
            return;
        }

        List<File> plugins =
                Arrays.stream(files).filter(file -> file.getName().endsWith(".jar")).toList();

        URL[] pluginNames = new URL[plugins.size()];
        plugins.forEach(
                plugin -> {
                    try {
                        pluginNames[plugins.indexOf(plugin)] = plugin.toURI().toURL();
                    } catch (MalformedURLException exception) {
                        Grasscutter.getLogger().warn(translate("plugin.unable_to_load"), exception);
                    }
                });

        URLClassLoader classLoader = new URLClassLoader(pluginNames);
        List<PluginData> dependencies = new ArrayList<>();

        for (var plugin : plugins) {
            try {
                URL url = plugin.toURI().toURL();
                try (URLClassLoader loader = new URLClassLoader(new URL[] {url})) {
                    URL configFile = loader.findResource("plugin.json");
                    InputStreamReader fileReader = new InputStreamReader(configFile.openStream());

                    PluginConfig pluginConfig = JsonUtils.loadToClass(fileReader, PluginConfig.class);
                    if (pluginConfig.api == null) {
                        Grasscutter.getLogger()
                                .warn(translate("plugin.invalid_api.not_present", plugin.getName()));
                        continue;
                    } else if (pluginConfig.api != API_VERSION) {
                        Grasscutter.getLogger()
                                .warn(
                                        translate(
                                                "plugin.invalid_api.lower",
                                                plugin.getName(),
                                                pluginConfig.api,
                                                API_VERSION));
                        continue;
                    }

                    if (!pluginConfig.validate()) {
                        Grasscutter.getLogger().warn(translate("plugin.invalid_config", plugin.getName()));
                        continue;
                    }

                    JarFile jarFile = new JarFile(plugin);
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.isDirectory()
                                || !entry.getName().endsWith(".class")
                                || entry.getName().contains("module-info")) continue;
                        String className = entry.getName().replace(".class", "").replace("/", ".");
                        classLoader.loadClass(className);
                    }

                    Class<?> pluginClass = classLoader.loadClass(pluginConfig.mainClass);
                    Plugin pluginInstance = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
                    fileReader.close();

                    if (pluginConfig.loadAfter != null && pluginConfig.loadAfter.length > 0) {
                        dependencies.add(
                                new PluginData(
                                        pluginInstance,
                                        PluginIdentifier.fromPluginConfig(pluginConfig),
                                        loader,
                                        pluginConfig.loadAfter));
                        continue;
                    }

                    this.loadPlugin(pluginInstance, PluginIdentifier.fromPluginConfig(pluginConfig), loader);
                } catch (ClassNotFoundException ignored) {
                    Grasscutter.getLogger().warn(translate("plugin.invalid_main_class", plugin.getName()));
                } catch (FileNotFoundException ignored) {
                    Grasscutter.getLogger().warn(translate("plugin.missing_config", plugin.getName()));
                }
            } catch (Exception exception) {
                Grasscutter.getLogger()
                        .error(translate("plugin.failed_to_load_plugin", plugin.getName()), exception);
            }
        }

        int depth = 0;
        final int maxDepth = 30;
        while (!dependencies.isEmpty()) {
            if (depth >= maxDepth) {
                Grasscutter.getLogger().error(translate("plugin.failed_to_load_dependencies"));
                break;
            }

            try {
                var pluginData = dependencies.get(0);

                if (!this.plugins.keySet().containsAll(List.of(pluginData.getDependencies()))) {
                    depth++;
                    continue;
                }

                dependencies.remove(pluginData);

                this.loadPlugin(
                        pluginData.getPlugin(), pluginData.getIdentifier(), pluginData.getClassLoader());
            } catch (Exception exception) {
                Grasscutter.getLogger().error(translate("plugin.failed_to_load"), exception);
                depth++;
            }
        }
    }

    private void loadPlugin(Plugin plugin, PluginIdentifier identifier, URLClassLoader classLoader) {
        Grasscutter.getLogger().info(translate("plugin.loading_plugin", identifier.name));

        try {
            Class<Plugin> pluginClass = Plugin.class;
            Method method =
                    pluginClass.getDeclaredMethod(
                            "initializePlugin", PluginIdentifier.class, URLClassLoader.class);
            method.setAccessible(true);
            method.invoke(plugin, identifier, classLoader);
            method.setAccessible(false);
        } catch (Exception ignored) {
            Grasscutter.getLogger().warn(translate("plugin.failed_add_id", identifier.name));
        }

        this.plugins.put(identifier.name, plugin);

        try {
            plugin.onLoad();
        } catch (Throwable exception) {
            Grasscutter.getLogger()
                    .error(translate("plugin.failed_to_load_plugin", identifier.name), exception);
        }
    }

    public void enablePlugins() {
        this.plugins.forEach(
                (name, plugin) -> {
                    Grasscutter.getLogger().info(translate("plugin.enabling_plugin", name));
                    try {
                        plugin.onEnable();
                        return;
                    } catch (NoSuchMethodError ignored) {
                        Grasscutter.getLogger().error(translate("plugin.invalid_api.outdated", name));
                    } catch (Throwable exception) {
                        Grasscutter.getLogger().error(translate("plugin.enabling_failed", name), exception);
                    }

                    this.disablePlugin(plugin);
                });
    }

    public void disablePlugins() {
        this.plugins.forEach(
                (name, plugin) -> {
                    Grasscutter.getLogger().info(translate("plugin.disabling_plugin", name));
                    this.disablePlugin(plugin);
                });
    }

    public void registerListener(EventHandler<? extends Event> listener) {
        if (!this.handlers.containsKey(listener.handles()))
            this.handlers.put(listener.handles(), new LinkedList<>());

        this.handlers.get(listener.handles()).add(listener);

        this.sortListeners();
    }

    public void removeListeners(Plugin plugin) {
        var newMap = new HashMap<Class<? extends Event>, List<EventHandler<? extends Event>>>();

        this.handlers.forEach(
                (event, handlers) -> {
                    newMap.put(event, new LinkedList<>());

                    handlers.forEach(
                            handler -> {
                                if (!handler.registrar().equals(plugin)) newMap.get(event).add(handler);
                            });
                });

        this.handlers.clear();
        this.handlers.putAll(newMap);
    }

    private void sortListeners() {
        var newMap = new HashMap<Class<? extends Event>, List<EventHandler<? extends Event>>>();

        this.handlers.forEach(
                (event, handlers) -> {
                    newMap.put(event, new LinkedList<>());

                    var sorted =
                            handlers.stream()
                                    .sorted(Comparator.comparingInt(handler -> handler.getPriority().ordinal()))
                                    .toList();
                    newMap.get(event).addAll(sorted);
                });

        this.handlers.clear();
        this.handlers.putAll(newMap);
    }

    public void invokeEvent(Event event) {
        var handlers = this.handlers.get(event.getClass());
        if (handlers == null) return;

        handlers.forEach(handler -> this.invokeHandler(event, handler));
    }

    @Nullable public Plugin getPlugin(String name) {
        return this.plugins.get(name);
    }

    public void enablePlugin(Plugin plugin) {
        try {
            plugin.onEnable();
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .error(translate("plugin.enabling_failed", plugin.getName()), exception);
        }
    }

    public void disablePlugin(Plugin plugin) {
        try {
            plugin.onDisable();
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .error(translate("plugin.disabling_failed", plugin.getName()), exception);
        }

        this.removeListeners(plugin);
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void invokeHandler(Event event, EventHandler<T> handler) {
        if (!event.isCanceled() || (event.isCanceled() && handler.ignoresCanceled()))
            handler.getCallback().consume((T) event);
    }

    @AllArgsConstructor
    @Getter
    static class PluginData {
        private Plugin plugin;
        private PluginIdentifier identifier;
        private URLClassLoader classLoader;
        private String[] dependencies;
    }
}
