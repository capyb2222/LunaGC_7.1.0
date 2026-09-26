package emu.grasscutter.server.scheduler;

import emu.grasscutter.Grasscutter;
import lombok.Getter;

public final class ServerTask implements Runnable {
    private final Runnable runnable;
    @Getter private final int taskId;
    private final int period, delay;
    @Getter private int ticks = 0;
    private boolean considerDelay = true;

    public ServerTask(Runnable runnable, int taskId, int period, int delay) {
        this.runnable = runnable;
        this.taskId = taskId;
        this.period = period;
        this.delay = delay;
    }

    public void cancel() {
        Grasscutter.getGameServer().getScheduler().cancelTask(this.taskId);
    }

    public boolean shouldRun() {
        ++this.ticks;
        if (this.delay != -1 && this.considerDelay) {
            var shouldRun = ticks >= this.delay;
            if (shouldRun) this.considerDelay = false;

            return shouldRun;
        } else if (this.period != -1) return ticks % this.period == 0;
        else return true;
    }

    public boolean shouldCancel() {
        return this.period == -1 && ticks >= delay;
    }

    @Override
    public void run() {
        try {
            this.runnable.run();
        } catch (Exception ex) {
            Grasscutter.getLogger().error("Exception during task: ", ex);
        }
    }
}
