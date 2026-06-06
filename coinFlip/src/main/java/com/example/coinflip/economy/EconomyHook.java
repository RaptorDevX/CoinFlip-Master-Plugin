package com.example.coinflip.economy;

import com.example.coinflip.CoinFlipPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * Robust wrapper hook for Vault Economy API operations.
 */
public final class EconomyHook {
    private final CoinFlipPlugin plugin;
    private Economy economy;

    public EconomyHook(CoinFlipPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                plugin.getLogger().warning("Vault plugin was not found! Economy features are disabled.");
                return false;
            }
            RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (provider == null) {
                plugin.getLogger().warning("No registered Vault economy provider found!");
                return false;
            }
            economy = provider.getProvider();
            return economy != null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred while setting up Vault economy hook:", t);
            return false;
        }
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Attempted to check balance but Vault economy is not initialized!");
            return false;
        }
        try {
            return economy.has(player, amount);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred while checking balance for " + player.getName() + ":", t);
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Attempted to withdraw money but Vault economy is not initialized!");
            return false;
        }
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred while withdrawing money from " + player.getName() + ":", t);
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Attempted to deposit money but Vault economy is not initialized!");
            return false;
        }
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred while depositing money to " + player.getName() + ":", t);
            return false;
        }
    }
}
