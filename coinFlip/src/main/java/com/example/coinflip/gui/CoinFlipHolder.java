package com.example.coinflip.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CoinFlipHolder implements InventoryHolder {
    private final GuiType type;
    private final int page;
    private final UUID coinFlipId;
    private final Map<Integer, UUID> slotCoinFlips = new HashMap<>();
    private Inventory inventory;

    public CoinFlipHolder(GuiType type, int page, UUID coinFlipId) {
        this.type = type;
        this.page = page;
        this.coinFlipId = coinFlipId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public GuiType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    public UUID getCoinFlipId() {
        return coinFlipId;
    }

    public void bind(int slot, UUID coinFlipId) {
        slotCoinFlips.put(slot, coinFlipId);
    }

    public UUID getBoundCoinFlip(int slot) {
        return slotCoinFlips.get(slot);
    }
}
