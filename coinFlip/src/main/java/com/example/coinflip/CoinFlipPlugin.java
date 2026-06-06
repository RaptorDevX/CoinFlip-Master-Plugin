package com.example.coinflip;

import com.example.coinflip.api.CoinFlipApi;
import com.example.coinflip.command.CoinFlipCommand;
import com.example.coinflip.config.PluginConfig;
import com.example.coinflip.economy.EconomyHook;
import com.example.coinflip.gui.GuiManager;
import com.example.coinflip.listener.GuiListener;
import com.example.coinflip.service.CoinFlipManager;
import com.example.coinflip.scheduler.PluginScheduler;
import com.example.coinflip.scheduler.SchedulerProvider;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CoinFlipPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private EconomyHook economyHook;
    private CoinFlipManager coinFlipManager;
    private GuiManager guiManager;
    private CoinFlipApi api;
    private PluginScheduler scheduler;

    @Override
    public void onEnable() {
        getLogger().info("========================================");
        getLogger().info("   Initializing CoinFlipMaster Plugin   ");
        getLogger().info("========================================");

        try {
            getLogger().info("[1/7] Loading configuration files...");
            saveDefaultConfig();
            pluginConfig = new PluginConfig(this);
            getLogger().info("[1/7] Configuration files loaded successfully.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to load configuration files!", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getLogger().info("[2/7] Initializing runtime scheduler provider...");
            scheduler = SchedulerProvider.getScheduler(this);
            getLogger().info("[2/7] Scheduler provider initialized successfully.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to initialize scheduler provider!", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getLogger().info("[3/7] Setting up Vault economy hook...");
            economyHook = new EconomyHook(this);
            if (!economyHook.setup()) {
                getLogger().severe("CRITICAL ERROR: Vault economy provider was not found! Disabling CoinFlipMaster.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("[3/7] Vault economy hook established successfully.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to hook into Vault economy!", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getLogger().info("[4/7] Loading plugin managers & API services...");
            coinFlipManager = new CoinFlipManager(this, pluginConfig, economyHook);
            guiManager = new GuiManager(this, pluginConfig, coinFlipManager);
            api = new CoinFlipApi(coinFlipManager);
            getLogger().info("[4/7] Managers and API services loaded successfully.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to load managers or services!", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getLogger().info("[5/7] Registering commands...");
            CoinFlipCommand command = new CoinFlipCommand(this, pluginConfig, coinFlipManager, guiManager);
            PluginCommand pluginCommand = getCommand("cf");
            if (pluginCommand != null) {
                pluginCommand.setExecutor(command);
                pluginCommand.setTabCompleter(command);
                getLogger().info("[5/7] Commands registered successfully.");
            } else {
                getLogger().warning("[5/7] Warning: Command '/cf' is not declared in plugin.yml! Command execution will not work.");
            }
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to register plugin commands!", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getLogger().info("[6/7] Registering GUI event listeners...");
            getServer().getPluginManager().registerEvents(new GuiListener(pluginConfig, coinFlipManager, guiManager), this);
            getLogger().info("[6/7] Event listeners registered successfully.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to register event listeners!", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            getLogger().info("[7/7] Checking for outstanding CoinFlips to refund...");
            coinFlipManager.loadAndRefundPreviousFlips();
            getLogger().info("[7/7] Outstandings checked and refunded successfully.");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to load and refund previous coinflips!", t);
        }

        getLogger().info("========================================");
        getLogger().info(" CoinFlipMaster enabled successfully!   ");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (coinFlipManager != null) {
            coinFlipManager.shutdown();
        }
    }

    public void reloadCoinFlip() {
        reloadConfig();
        pluginConfig.reload();
        if (guiManager != null) {
            guiManager.closeAll();
        }
    }

    public CoinFlipApi getApi() {
        return api;
    }

    public PluginScheduler getScheduler() {
        return scheduler;
    }
}
