package com.archimstechcorner.marketplace.features.cart.domain.model;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
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
 * A "save for later" item on a user's wishlist. References a {@link Product} (not a variant)
 * because the user hasn't committed to a specific SKU yet. One row per (user, product);
 * adding the same product again is idempotent (service-level no-op).
 */
@Entity
@Table(name = "wishlist_items")
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    /** JPA-required no-arg constructor. */
    protected WishlistItem() {}

    /**
     * Factory: create a new wishlist item.
     */
    public static WishlistItem create(Long userId, Product product) {
        WishlistItem item = new WishlistItem();
        item.userId = userId;
        item.product = product;
        item.addedAt = LocalDateTime.now();
        return item;
    }

    // ---- Getters ----

    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public Product getProduct() { return product; }

    public LocalDateTime getAddedAt() { return addedAt; }
}
