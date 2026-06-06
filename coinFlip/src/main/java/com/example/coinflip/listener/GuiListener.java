package com.example.coinflip.listener;

import com.example.coinflip.config.PluginConfig;
import com.example.coinflip.gui.CoinFlipHolder;
import com.example.coinflip.gui.GuiManager;
import com.example.coinflip.gui.GuiType;
import com.example.coinflip.model.CoinFlip;
import com.example.coinflip.service.CoinFlipManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Optional;
import java.util.UUID;

import static com.example.coinflip.config.PluginConfig.placeholder;

public final class GuiListener implements Listener {
    private final PluginConfig config;
    private final CoinFlipManager coinFlipManager;
    private final GuiManager guiManager;

    public GuiListener(PluginConfig config, CoinFlipManager coinFlipManager, GuiManager guiManager) {
        this.config = config;
        this.coinFlipManager = coinFlipManager;
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof CoinFlipHolder holder)) {
            return;
        }
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (holder.getType() == GuiType.MAIN) {
            handleMainClick(player, holder, rawSlot);
        } else if (holder.getType() == GuiType.CONFIRM) {
            handleConfirmClick(player, holder, rawSlot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CoinFlipHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMainClick(Player player, CoinFlipHolder holder, int slot) {
        if (guiManager.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }
        if (guiManager.isPreviousSlot(slot)) {
            guiManager.openMain(player, Math.max(0, holder.getPage() - 1));
            return;
        }
        if (guiManager.isNextSlot(slot)) {
            guiManager.openMain(player, holder.getPage() + 1);
            return;
        }

        UUID coinFlipId = holder.getBoundCoinFlip(slot);
        if (coinFlipId == null) {
            return;
        }
        Optional<CoinFlip> optionalCoinFlip = coinFlipManager.getCoinFlip(coinFlipId);
        if (optionalCoinFlip.isEmpty()) {
            player.sendMessage(config.message("coinflip-unavailable"));
            coinFlipManager.play(player, "sounds.error");
            guiManager.openMain(player, holder.getPage());
            return;
        }
        CoinFlip coinFlip = optionalCoinFlip.get();
        if (coinFlip.isCreator(player.getUniqueId())) {
            player.sendMessage(config.message("own-coinflip"));
            coinFlipManager.play(player, "sounds.error");
            return;
        }
        Player creator = org.bukkit.Bukkit.getPlayer(coinFlip.getCreatorUuid());
        if (creator == null || !creator.isOnline()) {
            player.sendMessage(config.message("coinflip-unavailable"));
            coinFlipManager.play(player, "sounds.error");
            guiManager.openMain(player, holder.getPage());
            return;
        }
        guiManager.openConfirm(player, coinFlip);
    }

    private void handleConfirmClick(Player player, CoinFlipHolder holder, int slot) {
        if (guiManager.isDeclineSlot(slot)) {
            guiManager.openMain(player, 0);
            return;
        }
        if (!guiManager.isAcceptSlot(slot)) {
            return;
        }

        CoinFlipManager.JoinResult result = coinFlipManager.reserveForJoin(player, holder.getCoinFlipId());
        switch (result.state()) {
            case JOINED -> {
                CoinFlip coinFlip = result.coinFlip();
                player.sendMessage(config.applyPlaceholders(
                        config.message("joined"),
                        placeholder("creator", coinFlip.getCreatorName()),
                        placeholder("amount", config.money(coinFlip.getAmount()))
                ));
                guiManager.startAnimation(coinFlip, player);
            }
            case OWN_COINFLIP -> {
                player.sendMessage(config.message("own-coinflip"));
                coinFlipManager.play(player, "sounds.error");
                guiManager.openMain(player, 0);
            }
            case INSUFFICIENT_FUNDS -> {
                player.sendMessage(config.message("insufficient-funds"));
                coinFlipManager.play(player, "sounds.error");
                guiManager.openMain(player, 0);
            }
            case UNAVAILABLE, FAILED -> {
                player.sendMessage(config.message("coinflip-unavailable"));
                coinFlipManager.play(player, "sounds.error");
                guiManager.openMain(player, 0);
            }
        }
    }
}
