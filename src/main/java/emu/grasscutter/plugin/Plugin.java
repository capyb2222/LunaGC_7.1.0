package emu.grasscutter.plugin;

import ch.qos.logback.classic.Level;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.plugin.api.ServerHelper;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.FileUtils;
import java.io.*;
import java.net.URLClassLoader;
import lombok.EqualsAndHashCode;
import org.slf4j.*;

@EqualsAndHashCode
public abstract class Plugin {
    private final ServerHelper server = ServerHelper.getInstance();

    private PluginIdentifier identifier;
    private URLClassLoader classLoader;
    private File dataFolder;
    private Logger logger;

    @SuppressWarnings("unused")
    private void initializePlugin(PluginIdentifier identifier, URLClassLoader classLoader) {
        if (this.identifier != null) {
            Grasscutter.getLogger().warn(this.identifier.name + " had a reinitialization attempt.");
            return;
        }

        this.identifier = identifier;
        this.classLoader = classLoader;
        this.dataFolder = FileUtils.getPluginPath(identifier.name).toFile();
        this.logger = LoggerFactory.getLogger(identifier.name);

        if (Grasscutter.getLogger().isDebugEnabled())
            ((ch.qos.logback.classic.Logger) logger).setLevel(Level.DEBUG);

        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            Grasscutter.getLogger()
                    .warn("Failed to create plugin data folder for " + this.identifier.name);
        }
    }

    public final PluginIdentifier getIdentifier() {
        return this.identifier;
    }

    public final String getName() {
        return this.identifier.name;
    }

    public final String getDescription() {
        return this.identifier.description;
    }

    public final String getVersion() {
        return this.identifier.version;
    }

    public final GameServer getServer() {
        return this.server.getGameServer();
    }

    public final InputStream getResource(String resourceName) {
        return this.classLoader.getResourceAsStream(resourceName);
    }

    public final File getDataFolder() {
        return this.dataFolder;
    }

    public final ServerHelper getHandle() {
        return this.server;
    }

    public final Logger getLogger() {
        return this.logger;
    }

    public void onLoad() {}

    public void onEnable() {}

    public void onDisable() {}
}
