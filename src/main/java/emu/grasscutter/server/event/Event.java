package emu.grasscutter.server.event;

import emu.grasscutter.Grasscutter;

public abstract class Event {
    private boolean cancelled = false;

    public boolean isCanceled() {
        return this.cancelled;
    }

    public void cancel() {
        if (this instanceof Cancellable) this.cancelled = true;
        else throw new UnsupportedOperationException("Event is not cancellable.");
    }

    public boolean call() {
        var pluginManager = Grasscutter.getPluginManager();
        if (pluginManager != null) pluginManager.invokeEvent(this);

        return !this.isCanceled();
    }
}
