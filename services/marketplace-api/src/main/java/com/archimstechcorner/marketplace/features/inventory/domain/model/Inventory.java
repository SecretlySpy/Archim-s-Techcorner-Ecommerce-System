package com.archimstechcorner.marketplace.features.inventory.domain.model;

import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A stock bucket for one (warehouse, variant). Authoritative source of sellable stock.
 * {@code sellable = onHand - reserved}. Reserved is moved by checkout reservations (future module).
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
        // JPA only
    }

    public static Inventory create(Warehouse warehouse, ProductVariant variant) {
        Inventory inv = new Inventory();
        inv.warehouse = warehouse;
        inv.variant = variant;
        inv.onHandQuantity = 0;
        inv.reservedQuantity = 0;
        inv.reorderThreshold = 0;
        inv.createdAt = Instant.now();
        inv.updatedAt = inv.createdAt;
        return inv;
    }

    public int sellable() {
        return onHandQuantity - reservedQuantity;
    }

    /** Sets on-hand to an absolute value; invariants (>=0, >= reserved) are validated by the service. */
    public void setOnHandQuantity(int onHandQuantity) {
        this.onHandQuantity = onHandQuantity;
        this.updatedAt = Instant.now();
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public ProductVariant getVariant() { return variant; }
    public int getOnHandQuantity() { return onHandQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getReorderThreshold() { return reorderThreshold; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
