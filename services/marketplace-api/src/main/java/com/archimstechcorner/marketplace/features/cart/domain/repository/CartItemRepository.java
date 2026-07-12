package com.archimstechcorner.marketplace.features.cart.domain.repository;

import com.archimstechcorner.marketplace.features.cart.domain.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link CartItem}. The unique (user_id, variant_id) constraint
 * is enforced at the DB level; the service layer performs upsert logic before save.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** All items in a user's cart, eagerly joined with variant → product → seller for display. */
    @Query("SELECT ci FROM CartItem ci " +
           "JOIN FETCH ci.variant v " +
           "JOIN FETCH v.product p " +
           "JOIN FETCH p.seller " +
           "WHERE ci.userId = :userId " +
           "ORDER BY ci.addedAt DESC")
    List<CartItem> findByUserIdWithDetails(Long userId);

    /** Simple lookup for upsert logic (add-to-cart: increment if exists, else create). */
    Optional<CartItem> findByUserIdAndVariantId(Long userId, Long variantId);

    /** Remove a specific item from the cart. */
    @Modifying
    void deleteByUserIdAndVariantId(Long userId, Long variantId);

    /** Clear the entire cart (post-checkout or explicit clear). */
    @Modifying
    void deleteAllByUserId(Long userId);
}
