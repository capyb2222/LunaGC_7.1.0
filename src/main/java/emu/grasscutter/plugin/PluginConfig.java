package emu.grasscutter.plugin;

public final class PluginConfig {
    public String name, description, version;
    public String mainClass;
    public Integer api;
    public String[] authors;
    public String[] loadAfter;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean validate() {
        return name != null && description != null && mainClass != null && api != null;
    }
}
