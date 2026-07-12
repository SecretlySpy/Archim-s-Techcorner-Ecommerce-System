package com.archimstechcorner.marketplace.features.inventory.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Immutable record of a single stock movement (append-only ledger). */
@Entity
@Table(name = "inventory_adjustments")
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "delta", nullable = false)
    private int delta;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "quantity_before", nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryAdjustment() {
        // JPA only
    }

    public static InventoryAdjustment create(Long inventoryId, int delta, String reasonCode,
                                             int quantityBefore, int quantityAfter, Long actorUserId) {
        InventoryAdjustment adj = new InventoryAdjustment();
        adj.inventoryId = inventoryId;
        adj.delta = delta;
        adj.reasonCode = reasonCode;
        adj.quantityBefore = quantityBefore;
        adj.quantityAfter = quantityAfter;
        adj.actorUserId = actorUserId;
        adj.createdAt = Instant.now();
        return adj;
    }

    public Long getId() { return id; }
    public Long getInventoryId() { return inventoryId; }
    public int getDelta() { return delta; }
    public String getReasonCode() { return reasonCode; }
    public int getQuantityBefore() { return quantityBefore; }
    public int getQuantityAfter() { return quantityAfter; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
