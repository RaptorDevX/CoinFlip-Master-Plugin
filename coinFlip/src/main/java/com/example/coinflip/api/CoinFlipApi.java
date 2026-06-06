package com.example.coinflip.api;

import com.example.coinflip.model.CoinFlip;
import com.example.coinflip.service.CoinFlipManager;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class CoinFlipApi {
    private final CoinFlipManager manager;

    public CoinFlipApi(CoinFlipManager manager) {
        this.manager = manager;
    }

    public Collection<CoinFlip> getActiveCoinFlips() {
        return manager.getActiveCoinFlips();
    }

    public Optional<CoinFlip> getCoinFlip(UUID id) {
        return manager.getCoinFlip(id);
    }
}
