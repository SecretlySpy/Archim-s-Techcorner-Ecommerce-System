package com.archimstechcorner.marketplace.features.inventory.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {}

    public static InventoryReservation create(Inventory inventory, Long orderId, int quantity, Instant expiresAt) {
        InventoryReservation res = new InventoryReservation();
        res.publicId = UUID.randomUUID();
        res.inventory = inventory;
        res.orderId = orderId;
        res.quantity = quantity;
        res.status = ReservationStatus.ACTIVE;
        res.expiresAt = expiresAt;
        res.createdAt = Instant.now();
        res.updatedAt = Instant.now();
        return res;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
        this.updatedAt = Instant.now();
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return status == ReservationStatus.ACTIVE && Instant.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Inventory getInventory() { return inventory; }
    public Long getOrderItemId() { return orderItemId; }
    public Long getOrderId() { return orderId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
