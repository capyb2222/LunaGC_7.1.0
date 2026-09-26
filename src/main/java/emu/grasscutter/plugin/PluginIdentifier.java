package emu.grasscutter.plugin;

public final class PluginIdentifier {
    public final String name, description, version;
    public final String[] authors;

    public PluginIdentifier(String name, String description, String version, String[] authors) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.authors = authors;
    }

    public static PluginIdentifier fromPluginConfig(PluginConfig config) {
        if (!config.validate())
            throw new IllegalArgumentException(
                    "A valid plugin config is required to convert into a plugin identifier.");
        return new PluginIdentifier(config.name, config.description, config.version, config.authors);
    }
}
