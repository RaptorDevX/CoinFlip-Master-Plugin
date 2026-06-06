package com.example.coinflip.model;

import java.util.Objects;
import java.util.UUID;

public final class CoinFlip {
    private final UUID id;
    private final UUID creatorUuid;
    private final String creatorName;
    private final double amount;
    private final long createdAt;
    private UUID joinerUuid;
    private String joinerName;
    private CoinFlipStatus status;

    public CoinFlip(UUID id, UUID creatorUuid, String creatorName, double amount, long createdAt, CoinFlipStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.creatorUuid = Objects.requireNonNull(creatorUuid, "creatorUuid");
        this.creatorName = Objects.requireNonNull(creatorName, "creatorName");
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = Objects.requireNonNull(status, "status");
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public double getAmount() {
        return amount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getJoinerUuid() {
        return joinerUuid;
    }

    public String getJoinerName() {
        return joinerName;
    }

    public CoinFlipStatus getStatus() {
        return status;
    }

    public boolean isCreator(UUID uuid) {
        return creatorUuid.equals(uuid);
    }

    public void markRunning(UUID joinerUuid, String joinerName) {
        this.joinerUuid = Objects.requireNonNull(joinerUuid, "joinerUuid");
        this.joinerName = Objects.requireNonNull(joinerName, "joinerName");
        this.status = CoinFlipStatus.RUNNING;
    }
}
