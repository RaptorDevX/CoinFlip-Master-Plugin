package com.example.coinflip.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PluginConfig {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public String message(String key) {
        String prefix = color(config.getString("messages.prefix", ""));
        return prefix + color(config.getString("messages." + key, key));
    }

    public String rawMessage(String key) {
        return color(config.getString("messages." + key, key));
    }

    public String text(String path, String fallback) {
        return color(config.getString(path, fallback));
    }

    public List<String> lore(String path) {
        List<String> lines = config.getStringList(path);
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(color(line));
        }
        return colored;
    }

    public Material material(String path, Material fallback) {
        String name = config.getString(path);
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    public Sound sound(String path) {
        String name = config.getString(path);
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean soundsEnabled() {
        return config.getBoolean("sounds.enabled", true);
    }

    public int mainSize() {
        return normalizeInventorySize(config.getInt("gui.main.size", 54));
    }

    public int confirmSize() {
        return normalizeInventorySize(config.getInt("gui.confirm.size", 27));
    }

    public int animationSize() {
        return normalizeInventorySize(config.getInt("gui.animation.size", 27));
    }

    public int getInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    public double getDouble(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    public String money(double amount) {
        return MONEY_FORMAT.format(amount);
    }

    public String applyPlaceholders(String input, Placeholder... placeholders) {
        String output = input;
        for (Placeholder placeholder : placeholders) {
            output = output.replace("%" + placeholder.key() + "%", placeholder.value());
        }
        return output;
    }

    public List<String> applyPlaceholders(List<String> input, Placeholder... placeholders) {
        List<String> output = new ArrayList<>(input.size());
        for (String line : input) {
            output.add(applyPlaceholders(line, placeholders));
        }
        return output;
    }

    public static Placeholder placeholder(String key, Object value) {
        return new Placeholder(key, String.valueOf(value));
    }

    private int normalizeInventorySize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public record Placeholder(String key, String value) {
    }
}
