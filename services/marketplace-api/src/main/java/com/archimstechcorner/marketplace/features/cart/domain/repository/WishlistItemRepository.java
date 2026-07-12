package com.archimstechcorner.marketplace.features.cart.domain.repository;

import com.archimstechcorner.marketplace.features.cart.domain.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link WishlistItem}. One row per (user, product);
 * the unique constraint prevents duplicates.
 */
@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /** All wishlisted products for a user, with product details eagerly loaded. */
    @Query("SELECT wi FROM WishlistItem wi " +
           "JOIN FETCH wi.product p " +
           "WHERE wi.userId = :userId " +
           "ORDER BY wi.addedAt DESC")
    List<WishlistItem> findByUserIdWithDetails(Long userId);

    /** Lookup for idempotent add check. */
    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    /** Remove a specific product from the wishlist. */
    @Modifying
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
