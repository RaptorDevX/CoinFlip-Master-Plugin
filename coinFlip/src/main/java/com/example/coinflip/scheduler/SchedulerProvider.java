package com.example.coinflip.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * A runtime provider that detects the server environment and loads the appropriate scheduler.
 */
public final class SchedulerProvider {
    private static PluginScheduler scheduler;

    /**
     * Retrieves the correct PluginScheduler implementation for the running server.
     *
     * @param plugin The JavaPlugin instance.
     * @return The PluginScheduler instance.
     */
    public static synchronized PluginScheduler getScheduler(Plugin plugin) {
        if (scheduler == null) {
            if (isFolia()) {
                plugin.getLogger().info("Folia environment detected. Using regionized scheduler.");
                scheduler = new FoliaSchedulerImpl(plugin);
            } else {
                plugin.getLogger().info("Standard Bukkit/Paper environment detected. Using legacy scheduler.");
                scheduler = new BukkitSchedulerImpl(plugin);
            }
        }
        return scheduler;
    }

    /**
     * Checks if the server environment is Folia.
     *
     * @return True if running on Folia, false otherwise.
     */
    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
