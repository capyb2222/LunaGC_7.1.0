package emu.grasscutter.server.scheduler;

import java.util.concurrent.ConcurrentHashMap;

public final class ServerTaskScheduler {
    private final ConcurrentHashMap<Integer, ServerTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AsyncServerTask> asyncTasks = new ConcurrentHashMap<>();

    private int nextTaskId = 0;

    public void runTasks() {
        if (this.tasks.size() == 0) return;

        for (ServerTask task : this.tasks.values()) {
            if (task.shouldRun()) {
                task.run();
            }

            if (task.shouldCancel()) {
                this.cancelTask(task.getTaskId());
            }
        }

        for (AsyncServerTask task : this.asyncTasks.values()) {
            if (!task.hasStarted()) {
                Thread thread = new Thread(task);
                thread.start();
            } else if (task.isFinished()) {
                this.asyncTasks.remove(task.getTaskId());
                task.complete();
            }
        }
    }

    public ServerTask getTask(int taskId) {
        return this.tasks.get(taskId);
    }

    public AsyncServerTask getAsyncTask(int taskId) {
        return this.asyncTasks.get(taskId);
    }

    public void cancelTask(int taskId) {
        this.tasks.remove(taskId);
    }

    public int scheduleAsyncTask(Runnable runnable) {
        var taskId = this.nextTaskId++;
        this.asyncTasks.put(taskId, new AsyncServerTask(runnable, taskId));
        return taskId;
    }

    public int scheduleTask(Runnable runnable) {
        return this.scheduleDelayedRepeatingTask(runnable, -1, -1);
    }

    public int scheduleDelayedTask(Runnable runnable, int delay) {
        return this.scheduleDelayedRepeatingTask(runnable, -1, delay);
    }

    public int scheduleRepeatingTask(Runnable runnable, int period) {
        return this.scheduleDelayedRepeatingTask(runnable, period, 0);
    }

    public int scheduleDelayedRepeatingTask(Runnable runnable, int period, int delay) {
        var taskId = this.nextTaskId++;
        this.tasks.put(taskId, new ServerTask(runnable, taskId, period, delay));
        return taskId;
    }
}
