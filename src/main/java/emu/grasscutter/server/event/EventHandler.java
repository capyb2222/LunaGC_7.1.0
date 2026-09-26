package emu.grasscutter.server.event;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.plugin.Plugin;
import emu.grasscutter.utils.objects.EventConsumer;

public final class EventHandler<T extends Event> {
    public static <T extends Event> void newHandler(
            Plugin plugin, Class<T> eventClass, EventConsumer<T> listener) {
        new EventHandler<>(eventClass)
                .priority(HandlerPriority.NORMAL)
                .listener(listener)
                .register(plugin);
    }

    public static <T extends Event> void newHandler(
            Plugin plugin, Class<T> eventClass, EventConsumer<T> listener, HandlerPriority priority) {
        new EventHandler<>(eventClass).listener(listener).priority(priority).register(plugin);
    }

    public static <T extends Event> void newHandler(
            Plugin plugin,
            Class<T> eventClass,
            EventConsumer<T> listener,
            HandlerPriority priority,
            boolean handleCanceled) {
        new EventHandler<>(eventClass)
                .listener(listener)
                .priority(priority)
                .ignore(handleCanceled)
                .register(plugin);
    }

    private final Class<T> eventClass;
    private EventConsumer<T> listener;
    private HandlerPriority priority;
    private boolean handleCanceled;
    private Plugin plugin;

    public EventHandler(Class<T> eventClass) {
        this.eventClass = eventClass;
    }

    public Class<T> handles() {
        return this.eventClass;
    }

    public EventConsumer<T> getCallback() {
        return this.listener;
    }

    public HandlerPriority getPriority() {
        return this.priority;
    }

    public boolean ignoresCanceled() {
        return this.handleCanceled;
    }

    public Plugin registrar() {
        return this.plugin;
    }

    public EventHandler<T> listener(EventConsumer<T> listener) {
        this.listener = listener;
        return this;
    }

    public EventHandler<T> priority(HandlerPriority priority) {
        this.priority = priority;
        return this;
    }

    public EventHandler<T> ignore(boolean ignore) {
        this.handleCanceled = ignore;
        return this;
    }

    public void register(Plugin plugin) {
        this.plugin = plugin;
        Grasscutter.getPluginManager().registerListener(this);
    }
}
