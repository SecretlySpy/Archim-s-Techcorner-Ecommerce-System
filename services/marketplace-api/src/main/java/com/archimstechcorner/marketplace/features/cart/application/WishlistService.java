package com.archimstechcorner.marketplace.features.cart.application;

import com.archimstechcorner.marketplace.features.cart.domain.model.WishlistItem;
import com.archimstechcorner.marketplace.features.cart.domain.repository.WishlistItemRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Wishlist ("save for later") business logic. The wishlist references products (not variants)
 * since the user hasn't committed to a specific SKU yet. Adding the same product twice is
 * idempotent — it silently returns the existing wishlist without error.
 */
@Service
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistItemRepository wishlistRepository,
                           ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
    }

    /**
     * Add a product to the user's wishlist. Idempotent: re-adding is a no-op.
     * Only ACTIVE products can be wishlisted (prevents saving hidden drafts).
     */
    @Transactional
    public List<WishlistView> addToWishlist(Long userId, String productPublicId) {
        Product product = requireActiveProduct(productPublicId);

        // Idempotent: if already wishlisted, just return the current list.
        boolean alreadyExists = wishlistRepository
                .findByUserIdAndProductId(userId, product.getId())
                .isPresent();
        if (!alreadyExists) {
            wishlistRepository.save(WishlistItem.create(userId, product));
        }

        return getWishlist(userId);
    }

    /**
     * Remove a product from the wishlist. Silent if not present.
     */
    @Transactional
    public List<WishlistView> removeFromWishlist(Long userId, String productPublicId) {
        Product product = requireProductExists(productPublicId);
        wishlistRepository.deleteByUserIdAndProductId(userId, product.getId());
        return getWishlist(userId);
    }

    /**
     * Retrieve the user's full wishlist with basic product details.
     */
    @Transactional(readOnly = true)
    public List<WishlistView> getWishlist(Long userId) {
        return wishlistRepository.findByUserIdWithDetails(userId).stream()
                .map(wi -> {
                    Product p = wi.getProduct();
                    return new WishlistView(
                            p.getPublicId().toString(),
                            p.getName(),
                            p.getSlug(),
                            p.getStatus().name(),
                            wi.getAddedAt().toString()
                    );
                })
                .toList();
    }

    // ---- Private helpers ----

    private Product requireActiveProduct(String publicIdStr) {
        Product product = requireProductExists(publicIdStr);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only published products can be wishlisted.");
        }
        return product;
    }

    private Product requireProductExists(String publicIdStr) {
        UUID publicId;
        try {
            publicId = UUID.fromString(publicIdStr);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid product ID format.");
        }
        return productRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found."));
    }

    /**
     * Read-model for a wishlisted product. Enough info for the frontend card display.
     */
    public record WishlistView(
            String productPublicId,
            String name,
            String slug,
            String status,
            String addedAt
    ) {}
}
