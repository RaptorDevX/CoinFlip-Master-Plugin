package com.example.coinflip.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Standard Spigot/Paper scheduler implementation using legacy BukkitScheduler.
 */
public final class BukkitSchedulerImpl implements PluginScheduler {
    private final Plugin plugin;

    public BukkitSchedulerImpl(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    @Override
    public TaskHandle runTaskLater(Runnable runnable, long delayTicks) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return task::cancel;
    }

    @Override
    public void runTask(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public void runTaskForEntity(org.bukkit.entity.Entity entity, Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}
