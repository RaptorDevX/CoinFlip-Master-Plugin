package com.example.coinflip.gui;

import com.example.coinflip.config.PluginConfig;
import com.example.coinflip.model.CoinFlip;
import com.example.coinflip.service.CoinFlipManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.example.coinflip.CoinFlipPlugin;
import com.example.coinflip.scheduler.TaskHandle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.example.coinflip.config.PluginConfig.placeholder;

public final class GuiManager {
    private final CoinFlipPlugin plugin;
    private final PluginConfig config;
    private final CoinFlipManager coinFlipManager;

    public GuiManager(CoinFlipPlugin plugin, PluginConfig config, CoinFlipManager coinFlipManager) {
        this.plugin = plugin;
        this.config = config;
        this.coinFlipManager = coinFlipManager;
    }

    public void openMain(Player player, int page) {
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.getScheduler().runTaskForEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            int size = config.mainSize();
            CoinFlipHolder holder = new CoinFlipHolder(GuiType.MAIN, Math.max(0, page), null);
            Inventory inventory = Bukkit.createInventory(holder, size, config.text("gui.main.title", "&8CoinFlip"));
            holder.setInventory(inventory);

            if (config.getBoolean("gui.main.fill-empty-slots", true)) {
                ItemStack filler = namedItem(config.material("gui.main.filler-material", Material.BLACK_STAINED_GLASS_PANE), config.text("items.filler.name", " "), List.of());
                for (int i = 0; i < size; i++) {
                    inventory.setItem(i, filler);
                }
            }

            List<CoinFlip> flips = new ArrayList<>(coinFlipManager.getActiveCoinFlips());
            int contentSlots = Math.max(9, size - 9);
            int start = holder.getPage() * contentSlots;
            int end = Math.min(flips.size(), start + contentSlots);
            for (int i = start; i < end; i++) {
                int slot = i - start;
                CoinFlip coinFlip = flips.get(i);
                inventory.setItem(slot, coinFlipItem(coinFlip));
                holder.bind(slot, coinFlip.getId());
            }

            int previousSlot = config.getInt("gui.main.previous-slot", size - 9);
            int closeSlot = config.getInt("gui.main.close-slot", size - 5);
            int nextSlot = config.getInt("gui.main.next-slot", size - 1);
            safeSet(inventory, previousSlot, namedItem(config.material("items.previous.material", Material.ARROW), config.text("items.previous.name", "&ePrevious Page"), List.of()));
            safeSet(inventory, closeSlot, namedItem(config.material("items.close.material", Material.BARRIER), config.text("items.close.name", "&cClose"), List.of()));
            safeSet(inventory, nextSlot, namedItem(config.material("items.next.material", Material.ARROW), config.text("items.next.name", "&eNext Page"), List.of()));

            player.openInventory(inventory);
            coinFlipManager.play(player, "sounds.open");
            if (flips.isEmpty()) {
                sendActionBar(player, config.message("no-active-flips"));
            }
        });
    }

    public void openConfirm(Player player, CoinFlip coinFlip) {
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.getScheduler().runTaskForEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            CoinFlipHolder holder = new CoinFlipHolder(GuiType.CONFIRM, 0, coinFlip.getId());
            Inventory inventory = Bukkit.createInventory(holder, config.confirmSize(), config.text("gui.confirm.title", "&8Confirm CoinFlip"));
            holder.setInventory(inventory);

            fill(inventory);
            int acceptSlot = config.getInt("gui.confirm.accept-slot", 11);
            int declineSlot = config.getInt("gui.confirm.decline-slot", 15);
            safeSet(inventory, acceptSlot, namedItem(
                    config.material("items.accept.material", Material.LIME_CONCRETE),
                    replace(config.text("items.accept.name", "&a&lACCEPT"), coinFlip),
                    replace(config.lore("items.accept.lore"), coinFlip)
            ));
            safeSet(inventory, declineSlot, namedItem(
                    config.material("items.decline.material", Material.RED_CONCRETE),
                    replace(config.text("items.decline.name", "&c&lDECLINE"), coinFlip),
                    replace(config.lore("items.decline.lore"), coinFlip)
            ));
            safeSet(inventory, config.getInt("gui.animation.status-slot", 13), coinFlipItem(coinFlip));
            player.openInventory(inventory);
            coinFlipManager.play(player, "sounds.open");
        });
    }

    public void startAnimation(CoinFlip coinFlip, Player joiner) {
        if (joiner == null || !joiner.isOnline()) {
            return;
        }
        plugin.getScheduler().runTaskForEntity(joiner, () -> {
            if (!joiner.isOnline()) {
                return;
            }
            Player creator = Bukkit.getPlayer(coinFlip.getCreatorUuid());
            Inventory inventory = createAnimationInventory(coinFlip);
            joiner.openInventory(inventory);
            if (creator != null && creator.isOnline()) {
                plugin.getScheduler().runTaskForEntity(creator, () -> {
                    if (creator.isOnline()) {
                        creator.openInventory(inventory);
                    }
                });
            }

            int totalTicks = Math.max(20, config.getInt("settings.animation-seconds", 3) * 20);
            int interval = 5;
            final TaskHandle[] taskHandle = new TaskHandle[1];
            taskHandle[0] = plugin.getScheduler().runTaskTimer(new Runnable() {
                private int elapsed;
                private boolean swapped;

                @Override
                public void run() {
                    if (elapsed >= totalTicks) {
                        coinFlipManager.finish(coinFlip);
                        for (Player viewer : currentViewers(inventory)) {
                            if (viewer != null && viewer.isOnline()) {
                                plugin.getScheduler().runTaskForEntity(viewer, () -> {
                                    if (viewer.isOnline()) {
                                        viewer.closeInventory();
                                    }
                                });
                            }
                        }
                        if (taskHandle[0] != null) {
                            taskHandle[0].cancel();
                        }
                        return;
                    }

                    updateAnimation(inventory, coinFlip, swapped);
                    for (Player viewer : currentViewers(inventory)) {
                        coinFlipManager.play(viewer, "sounds.tick");
                        sendActionBar(viewer, replace(config.text("items.status.name", "&6&lFlipping Coin..."), coinFlip));
                    }
                    swapped = !swapped;
                    elapsed += interval;
                }
            }, 0L, interval);
        });
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CoinFlipHolder) {
                final Player p = player;
                plugin.getScheduler().runTaskForEntity(p, () -> {
                    if (p.isOnline()) {
                        p.closeInventory();
                    }
                });
            }
        }
    }

    public boolean isPreviousSlot(int slot) {
        return slot == config.getInt("gui.main.previous-slot", 45);
    }

    public boolean isNextSlot(int slot) {
        return slot == config.getInt("gui.main.next-slot", 53);
    }

    public boolean isCloseSlot(int slot) {
        return slot == config.getInt("gui.main.close-slot", 49);
    }

    public boolean isAcceptSlot(int slot) {
        return slot == config.getInt("gui.confirm.accept-slot", 11);
    }

    public boolean isDeclineSlot(int slot) {
        return slot == config.getInt("gui.confirm.decline-slot", 15);
    }

    private Inventory createAnimationInventory(CoinFlip coinFlip) {
        CoinFlipHolder holder = new CoinFlipHolder(GuiType.ANIMATION, 0, coinFlip.getId());
        Inventory inventory = Bukkit.createInventory(holder, config.animationSize(), config.text("gui.animation.title", "&8CoinFlip"));
        holder.setInventory(inventory);
        fill(inventory);
        safeSet(inventory, config.getInt("gui.animation.status-slot", 13), namedItem(
                config.material("items.status.material", Material.GOLD_INGOT),
                replace(config.text("items.status.name", "&6&lFlipping Coin..."), coinFlip),
                replace(config.lore("items.status.lore"), coinFlip)
        ));
        updateAnimation(inventory, coinFlip, false);
        return inventory;
    }

    private void updateAnimation(Inventory inventory, CoinFlip coinFlip, boolean swapped) {
        int left = config.getInt("gui.animation.left-head-slot", 11);
        int right = config.getInt("gui.animation.right-head-slot", 15);
        ItemStack creatorHead = head(coinFlip.getCreatorUuid(), coinFlip.getCreatorName(), "&e" + coinFlip.getCreatorName(), List.of("&7Creator"));
        ItemStack joinerHead = head(coinFlip.getJoinerUuid(), coinFlip.getJoinerName(), "&e" + coinFlip.getJoinerName(), List.of("&7Challenger"));
        safeSet(inventory, left, swapped ? joinerHead : creatorHead);
        safeSet(inventory, right, swapped ? creatorHead : joinerHead);
    }

    private Collection<Player> currentViewers(Inventory inventory) {
        List<Player> viewers = new ArrayList<>();
        for (org.bukkit.entity.HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player player) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private ItemStack coinFlipItem(CoinFlip coinFlip) {
        return head(
                coinFlip.getCreatorUuid(),
                coinFlip.getCreatorName(),
                replace(config.text("items.coinflip.name", "&6&l%creator%"), coinFlip),
                replace(config.lore("items.coinflip.lore"), coinFlip)
        );
    }

    private ItemStack head(java.util.UUID uuid, String ownerName, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(owner);
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = namedItem(config.material("gui.main.filler-material", Material.BLACK_STAINED_GLASS_PANE), config.text("items.filler.name", " "), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void safeSet(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.getScheduler().runTaskForEntity(player, () -> {
            if (player.isOnline()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            }
        });
    }

    private String replace(String text, CoinFlip coinFlip) {
        return config.applyPlaceholders(
                text,
                placeholder("creator", coinFlip.getCreatorName()),
                placeholder("joiner", coinFlip.getJoinerName() == null ? "" : coinFlip.getJoinerName()),
                placeholder("amount", config.money(coinFlip.getAmount()))
        );
    }

    private List<String> replace(List<String> text, CoinFlip coinFlip) {
        return config.applyPlaceholders(
                text,
                placeholder("creator", coinFlip.getCreatorName()),
                placeholder("joiner", coinFlip.getJoinerName() == null ? "" : coinFlip.getJoinerName()),
                placeholder("amount", config.money(coinFlip.getAmount()))
        );
    }
}
