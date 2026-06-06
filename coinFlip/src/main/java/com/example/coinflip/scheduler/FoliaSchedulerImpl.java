package com.example.coinflip.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Folia scheduler implementation utilizing Folia's GlobalRegionScheduler.
 */
public final class FoliaSchedulerImpl implements PluginScheduler {
    private final Plugin plugin;

    public FoliaSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) {
        // Ensure delay is at least 1 tick to prevent instant-execution conflicts in Folia
        long initialDelay = Math.max(1, delayTicks);
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> runnable.run(),
                initialDelay,
                periodTicks
        );
        return task::cancel;
    }

    @Override
    public TaskHandle runTaskLater(Runnable runnable, long delayTicks) {
        long delay = Math.max(1, delayTicks);
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(
                plugin,
                scheduledTask -> runnable.run(),
                delay
        );
        return task::cancel;
    }

    @Override
    public void runTask(Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().run(
                plugin,
                scheduledTask -> runnable.run()
        );
    }

    @Override
    public void runTaskForEntity(org.bukkit.entity.Entity entity, Runnable runnable) {
        entity.getScheduler().run(
                plugin,
                scheduledTask -> runnable.run(),
                null
        );
    }
}
