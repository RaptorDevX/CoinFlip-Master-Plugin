package com.example.coinflip.scheduler;

/**
 * An abstraction layer for scheduling tasks to support both Spigot/Paper and Folia servers.
 */
public interface PluginScheduler {
    /**
     * Schedules a repeating task.
     *
     * @param runnable    The task to run.
     * @param delayTicks  The initial delay in ticks.
     * @param periodTicks The period in ticks between consecutive executions.
     * @return A TaskHandle representing the scheduled task.
     */
    TaskHandle runTaskTimer(Runnable runnable, long delayTicks, long periodTicks);

    /**
     * Schedules a delayed task.
     *
     * @param runnable   The task to run.
     * @param delayTicks The delay in ticks.
     * @return A TaskHandle representing the scheduled task.
     */
    TaskHandle runTaskLater(Runnable runnable, long delayTicks);

    /**
     * Executes a task on the primary server thread or global region.
     *
     * @param runnable The task to run.
     */
    void runTask(Runnable runnable);

    /**
     * Executes a task safely on the thread ticks of a specific Entity (such as a Player).
     *
     * @param entity   The entity whose regional thread should run the task.
     * @param runnable The task to run.
     */
    void runTaskForEntity(org.bukkit.entity.Entity entity, Runnable runnable);
}
