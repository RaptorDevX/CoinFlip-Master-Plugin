package com.example.coinflip.service;

import com.example.coinflip.CoinFlipPlugin;
import com.example.coinflip.config.PluginConfig;
import com.example.coinflip.economy.EconomyHook;
import com.example.coinflip.model.CoinFlip;
import com.example.coinflip.model.CoinFlipStatus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.coinflip.config.PluginConfig.placeholder;

public final class CoinFlipManager {
    private final CoinFlipPlugin plugin;
    private final PluginConfig config;
    private final EconomyHook economy;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, CoinFlip> activeFlips = new ConcurrentHashMap<>();
    private final Map<UUID, CoinFlip> runningFlips = new ConcurrentHashMap<>();
    private final File storageFile;

    public CoinFlipManager(CoinFlipPlugin plugin, PluginConfig config, EconomyHook economy) {
        this.plugin = plugin;
        this.config = config;
        this.economy = economy;
        this.storageFile = new File(plugin.getDataFolder(), "active-coinflips.yml");
    }

    public synchronized CreateResult create(Player player, double amount) {
        double min = config.getDouble("settings.min-amount", 100);
        double max = config.getDouble("settings.max-amount", 1_000_000_000);
        if (amount < min) {
            return CreateResult.TOO_LOW;
        }
        if (amount > max) {
            return CreateResult.TOO_HIGH;
        }
        if (!economy.has(player, amount)) {
            return CreateResult.INSUFFICIENT_FUNDS;
        }
        if (!economy.withdraw(player, amount)) {
            return CreateResult.FAILED;
        }

        CoinFlip coinFlip = new CoinFlip(UUID.randomUUID(), player.getUniqueId(), player.getName(), amount, System.currentTimeMillis(), CoinFlipStatus.WAITING);
        activeFlips.put(coinFlip.getId(), coinFlip);
        saveActiveFlips();
        play(player, "sounds.create");
        return CreateResult.CREATED;
    }

    public synchronized JoinResult reserveForJoin(Player joiner, UUID coinFlipId) {
        CoinFlip coinFlip = activeFlips.get(coinFlipId);
        if (coinFlip == null || coinFlip.getStatus() != CoinFlipStatus.WAITING) {
            return JoinResult.unavailable();
        }
        if (coinFlip.isCreator(joiner.getUniqueId())) {
            return JoinResult.ownCoinFlip();
        }
        Player creator = Bukkit.getPlayer(coinFlip.getCreatorUuid());
        if (creator == null || !creator.isOnline()) {
            return JoinResult.unavailable();
        }
        if (!economy.has(joiner, coinFlip.getAmount())) {
            return JoinResult.insufficientFunds();
        }
        if (!economy.withdraw(joiner, coinFlip.getAmount())) {
            return JoinResult.failed();
        }

        coinFlip.markRunning(joiner.getUniqueId(), joiner.getName());
        activeFlips.remove(coinFlipId);
        runningFlips.put(coinFlipId, coinFlip);
        saveActiveFlips();
        play(joiner, "sounds.join");
        return JoinResult.joined(coinFlip);
    }

    public void finish(CoinFlip coinFlip) {
        UUID winnerUuid = random.nextBoolean() ? coinFlip.getCreatorUuid() : coinFlip.getJoinerUuid();
        String winnerName = winnerUuid.equals(coinFlip.getCreatorUuid()) ? coinFlip.getCreatorName() : coinFlip.getJoinerName();
        UUID loserUuid = winnerUuid.equals(coinFlip.getCreatorUuid()) ? coinFlip.getJoinerUuid() : coinFlip.getCreatorUuid();
        double pot = coinFlip.getAmount() * 2;

        OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerUuid);
        economy.deposit(winner, pot);

        Player onlineWinner = Bukkit.getPlayer(winnerUuid);
        Player onlineLoser = Bukkit.getPlayer(loserUuid);
        if (onlineWinner != null) {
            final Player w = onlineWinner;
            plugin.getScheduler().runTaskForEntity(w, () -> {
                if (w.isOnline()) {
                    w.sendTitle(
                            replace(config.rawMessage("title-win"), coinFlip, winnerName, pot),
                            replace(config.rawMessage("subtitle-win"), coinFlip, winnerName, pot),
                            10, 50, 10
                    );
                    play(w, "sounds.win");
                }
            });
        }
        if (onlineLoser != null) {
            final Player l = onlineLoser;
            plugin.getScheduler().runTaskForEntity(l, () -> {
                if (l.isOnline()) {
                    l.sendTitle(
                            replace(config.rawMessage("title-lose"), coinFlip, winnerName, pot),
                            replace(config.rawMessage("subtitle-lose"), coinFlip, winnerName, pot),
                            10, 50, 10
                    );
                }
            });
        }

        if (config.getBoolean("settings.broadcast-winner", true)) {
            Bukkit.broadcastMessage(replace(config.message("winner-broadcast"), coinFlip, winnerName, pot));
        }

        runningFlips.remove(coinFlip.getId());
        saveActiveFlips();
    }

    public synchronized Optional<CoinFlip> getCoinFlip(UUID id) {
        return Optional.ofNullable(activeFlips.get(id));
    }

    public synchronized Collection<CoinFlip> getActiveCoinFlips() {
        List<CoinFlip> flips = new ArrayList<>(activeFlips.values());
        flips.sort(Comparator.comparingLong(CoinFlip::getCreatedAt));
        return Collections.unmodifiableList(flips);
    }

    public void loadAndRefundPreviousFlips() {
        if (!config.getBoolean("settings.refund-active-on-startup", true)) {
            loadActiveFlips();
            return;
        }
        List<CoinFlip> stored = readStoredFlips();
        for (CoinFlip coinFlip : stored) {
            refund(coinFlip, true);
        }
        synchronized (this) {
            activeFlips.clear();
            runningFlips.clear();
            saveActiveFlips();
        }
    }

    public void shutdown() {
        if (!config.getBoolean("settings.refund-active-on-disable", true)) {
            saveActiveFlips();
            return;
        }
        List<CoinFlip> toRefund;
        synchronized (this) {
            toRefund = new ArrayList<>(activeFlips.values());
            toRefund.addAll(runningFlips.values());
            activeFlips.clear();
            runningFlips.clear();
            saveActiveFlips();
        }
        for (CoinFlip coinFlip : toRefund) {
            refund(coinFlip, true);
        }
    }

    public void play(Player player, String path) {
        if (!config.soundsEnabled() || player == null || !player.isOnline()) {
            return;
        }
        Sound sound = config.sound(path);
        if (sound != null) {
            plugin.getScheduler().runTaskForEntity(player, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), sound, 0.8F, 1.0F);
                }
            });
        }
    }

    private void refund(CoinFlip coinFlip, boolean notify) {
        OfflinePlayer creator = Bukkit.getOfflinePlayer(coinFlip.getCreatorUuid());
        economy.deposit(creator, coinFlip.getAmount());
        Player onlineCreator = Bukkit.getPlayer(coinFlip.getCreatorUuid());
        if (notify && onlineCreator != null) {
            final Player c = onlineCreator;
            plugin.getScheduler().runTaskForEntity(c, () -> {
                if (c.isOnline()) {
                    c.sendMessage(config.applyPlaceholders(config.message("refunded"), placeholder("amount", config.money(coinFlip.getAmount()))));
                }
            });
        }

        if (coinFlip.getJoinerUuid() != null) {
            OfflinePlayer joiner = Bukkit.getOfflinePlayer(coinFlip.getJoinerUuid());
            economy.deposit(joiner, coinFlip.getAmount());
            Player onlineJoiner = Bukkit.getPlayer(coinFlip.getJoinerUuid());
            if (notify && onlineJoiner != null) {
                final Player j = onlineJoiner;
                plugin.getScheduler().runTaskForEntity(j, () -> {
                    if (j.isOnline()) {
                        j.sendMessage(config.applyPlaceholders(config.message("refunded"), placeholder("amount", config.money(coinFlip.getAmount()))));
                    }
                });
            }
        }
    }

    private void loadActiveFlips() {
        synchronized (this) {
            activeFlips.clear();
            runningFlips.clear();
            for (CoinFlip coinFlip : readStoredFlips()) {
                if (coinFlip.getStatus() == CoinFlipStatus.WAITING) {
                    activeFlips.put(coinFlip.getId(), coinFlip);
                } else {
                    refund(coinFlip, true);
                }
            }
            saveActiveFlips();
        }
    }

    private List<CoinFlip> readStoredFlips() {
        if (!storageFile.exists()) {
            return List.of();
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection section = yaml.getConfigurationSection("coinflips");
        if (section == null) {
            return List.of();
        }

        List<CoinFlip> flips = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID creatorUuid = UUID.fromString(section.getString(key + ".creator-uuid"));
                String creatorName = section.getString(key + ".creator-name", "Unknown");
                double amount = section.getDouble(key + ".amount");
                long createdAt = section.getLong(key + ".created-at", System.currentTimeMillis());
                CoinFlipStatus status = CoinFlipStatus.valueOf(section.getString(key + ".status", "WAITING"));
                CoinFlip coinFlip = new CoinFlip(id, creatorUuid, creatorName, amount, createdAt, status);
                String joinerUuid = section.getString(key + ".joiner-uuid");
                if (joinerUuid != null && !joinerUuid.isBlank()) {
                    coinFlip.markRunning(UUID.fromString(joinerUuid), section.getString(key + ".joiner-name", "Unknown"));
                }
                flips.add(coinFlip);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipping invalid stored CoinFlip entry: " + key);
            }
        }
        return flips;
    }

    private void saveActiveFlips() {
        FileConfiguration yaml = new YamlConfiguration();
        for (CoinFlip coinFlip : activeFlips.values()) {
            writeFlip(yaml, coinFlip);
        }
        for (CoinFlip coinFlip : runningFlips.values()) {
            writeFlip(yaml, coinFlip);
        }
        try {
            yaml.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save active CoinFlips: " + exception.getMessage());
        }
    }

    private void writeFlip(FileConfiguration yaml, CoinFlip coinFlip) {
            String path = "coinflips." + coinFlip.getId();
            yaml.set(path + ".creator-uuid", coinFlip.getCreatorUuid().toString());
            yaml.set(path + ".creator-name", coinFlip.getCreatorName());
            yaml.set(path + ".amount", coinFlip.getAmount());
            yaml.set(path + ".created-at", coinFlip.getCreatedAt());
            yaml.set(path + ".status", coinFlip.getStatus().name());
            if (coinFlip.getJoinerUuid() != null) {
                yaml.set(path + ".joiner-uuid", coinFlip.getJoinerUuid().toString());
                yaml.set(path + ".joiner-name", coinFlip.getJoinerName());
            }
    }

    private String replace(String message, CoinFlip coinFlip, String winnerName, double pot) {
        return config.applyPlaceholders(
                message,
                placeholder("creator", coinFlip.getCreatorName()),
                placeholder("joiner", coinFlip.getJoinerName()),
                placeholder("amount", config.money(coinFlip.getAmount())),
                placeholder("winner", winnerName),
                placeholder("pot", config.money(pot))
        );
    }

    public enum CreateResult {
        CREATED,
        TOO_LOW,
        TOO_HIGH,
        INSUFFICIENT_FUNDS,
        FAILED
    }

    public static final class JoinResult {
        private final JoinState state;
        private final CoinFlip coinFlip;

        private JoinResult(JoinState state, CoinFlip coinFlip) {
            this.state = state;
            this.coinFlip = coinFlip;
        }

        public static JoinResult joined(CoinFlip coinFlip) {
            return new JoinResult(JoinState.JOINED, coinFlip);
        }

        public static JoinResult unavailable() {
            return new JoinResult(JoinState.UNAVAILABLE, null);
        }

        public static JoinResult ownCoinFlip() {
            return new JoinResult(JoinState.OWN_COINFLIP, null);
        }

        public static JoinResult insufficientFunds() {
            return new JoinResult(JoinState.INSUFFICIENT_FUNDS, null);
        }

        public static JoinResult failed() {
            return new JoinResult(JoinState.FAILED, null);
        }

        public JoinState state() {
            return state;
        }

        public CoinFlip coinFlip() {
            return coinFlip;
        }
    }

    public enum JoinState {
        JOINED,
        UNAVAILABLE,
        OWN_COINFLIP,
        INSUFFICIENT_FUNDS,
        FAILED
    }
}
