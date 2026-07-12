package com.archimstechcorner.marketplace.features.cart.domain.model;

import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A single line-item in a user's shopping cart. References a specific {@link ProductVariant}
 * so the price, SKU, and stock are unambiguous. One row per (user, variant) — the unique
 * constraint in the migration enforces this; the service layer upserts on collision.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA-required no-arg constructor. */
    protected CartItem() {}

    /**
     * Factory: create a new cart item. Callers must never use {@code new CartItem()} directly —
     * the protected ctor is reserved for JPA hydration.
     */
    public static CartItem create(Long userId, ProductVariant variant, int quantity) {
        CartItem item = new CartItem();
        item.userId = userId;
        item.variant = variant;
        item.quantity = quantity;
        item.addedAt = LocalDateTime.now();
        item.updatedAt = item.addedAt;
        return item;
    }

    // ---- Getters & Setters ----

    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public ProductVariant getVariant() { return variant; }

    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getAddedAt() { return addedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
