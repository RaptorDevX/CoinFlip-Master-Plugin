package com.example.coinflip.command;

import com.example.coinflip.CoinFlipPlugin;
import com.example.coinflip.config.PluginConfig;
import com.example.coinflip.gui.GuiManager;
import com.example.coinflip.service.CoinFlipManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.example.coinflip.config.PluginConfig.placeholder;

public final class CoinFlipCommand implements CommandExecutor, TabCompleter {
    private final CoinFlipPlugin plugin;
    private final PluginConfig config;
    private final CoinFlipManager coinFlipManager;
    private final GuiManager guiManager;

    public CoinFlipCommand(CoinFlipPlugin plugin, PluginConfig config, CoinFlipManager coinFlipManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.config = config;
        this.coinFlipManager = coinFlipManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.message("players-only"));
                return true;
            }
            if (!player.hasPermission("coinflip.use")) {
                player.sendMessage(config.message("no-permission"));
                return true;
            }
            guiManager.openMain(player, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            handleCreate(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("coinflip.reload") && !sender.hasPermission("coinflip.admin")) {
                sender.sendMessage(config.message("no-permission"));
                return true;
            }
            plugin.reloadCoinFlip();
            sender.sendMessage(config.message("reload"));
            return true;
        }

        sender.sendMessage(config.message("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("coinflip.create")) {
                suggestions.add("create");
            }
            if (sender.hasPermission("coinflip.reload") || sender.hasPermission("coinflip.admin")) {
                suggestions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            suggestions.add("1000");
            suggestions.add("10000");
            suggestions.add("100k");
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        suggestions.removeIf(suggestion -> !suggestion.toLowerCase(Locale.ROOT).startsWith(current));
        return suggestions;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.message("players-only"));
            return;
        }
        if (!player.hasPermission("coinflip.create")) {
            player.sendMessage(config.message("no-permission"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(config.message("usage"));
            return;
        }

        Double amount = parseAmount(args[1]);
        if (amount == null || amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            player.sendMessage(config.message("invalid-amount"));
            coinFlipManager.play(player, "sounds.error");
            return;
        }

        amount = Math.round(amount * 100.0) / 100.0;

        CoinFlipManager.CreateResult result = coinFlipManager.create(player, amount);
        switch (result) {
            case CREATED -> player.sendMessage(config.applyPlaceholders(config.message("created"), placeholder("amount", config.money(amount))));
            case TOO_LOW -> {
                double min = config.getDouble("settings.min-amount", 100);
                player.sendMessage(config.applyPlaceholders(config.message("amount-too-low"), placeholder("min", config.money(min))));
                coinFlipManager.play(player, "sounds.error");
            }
            case TOO_HIGH -> {
                double max = config.getDouble("settings.max-amount", 1_000_000_000);
                player.sendMessage(config.applyPlaceholders(config.message("amount-too-high"), placeholder("max", config.money(max))));
                coinFlipManager.play(player, "sounds.error");
            }
            case INSUFFICIENT_FUNDS -> {
                player.sendMessage(config.message("insufficient-funds"));
                coinFlipManager.play(player, "sounds.error");
            }
            case FAILED -> {
                player.sendMessage(config.message("create-failed"));
                coinFlipManager.play(player, "sounds.error");
            }
        }
    }

    private Double parseAmount(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace(",", "").trim();
        double multiplier = 1;
        if (normalized.endsWith("k")) {
            multiplier = 1_000;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 1_000_000;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("b")) {
            multiplier = 1_000_000_000;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Double.parseDouble(normalized) * multiplier;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
