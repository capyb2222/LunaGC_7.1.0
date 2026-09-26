package emu.grasscutter.server.scheduler;

import javax.annotation.Nullable;
import lombok.Getter;

public final class AsyncServerTask implements Runnable {
    private final Runnable task;
    @Getter private final int taskId;
    @Nullable private final Runnable callback;

    private boolean started = false;
    private boolean finished = false;
    @Nullable private Object result = null;

    public AsyncServerTask(Runnable task, int taskId) {
        this(task, null, taskId);
    }

    public AsyncServerTask(Runnable task, @Nullable Runnable callback, int taskId) {
        this.task = task;
        this.callback = callback;
        this.taskId = taskId;
    }

    public boolean hasStarted() {
        return this.started;
    }

    public boolean isFinished() {
        return this.finished;
    }

    @Override
    public void run() {
        this.started = true;

        this.task.run();

        this.finished = true;
    }

    public void complete() {
        if (this.callback != null) this.callback.run();
    }

    @Nullable public Object getResult() {
        return this.result;
    }

    public void setResult(@Nullable Object result) {
        this.result = result;
    }
}
