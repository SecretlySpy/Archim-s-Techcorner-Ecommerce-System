package com.archimstechcorner.marketplace.features.cart.application;

import com.archimstechcorner.marketplace.features.cart.domain.model.CartItem;
import com.archimstechcorner.marketplace.features.cart.domain.repository.CartItemRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductVariantRepository;
import com.archimstechcorner.marketplace.features.payments.application.port.PaymentGateway;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shopping cart business logic. A user's cart is persistent (MySQL, source of truth per INV-ARCH-2)
 * and can hold items from multiple sellers. Stock is soft-checked (not reserved) until checkout.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Add: upsert — if the variant already exists in the cart, increment the quantity.</li>
 *   <li>Update: set an explicit quantity. If 0, removes the item.</li>
 *   <li>Only ACTIVE products with active variants can be added.</li>
 *   <li>Cart view groups items by seller for the checkout preview.</li>
 * </ul>
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final PaymentGateway paymentGateway;

    public CartService(CartItemRepository cartItemRepository,
                       ProductVariantRepository variantRepository,
                       PaymentGateway paymentGateway) {
        this.cartItemRepository = cartItemRepository;
        this.variantRepository = variantRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Add a variant to the cart (or increment if already present).
     *
     * @param userId           the authenticated user's internal id
     * @param variantPublicId  the variant to add
     * @param quantity         number of units to add (must be ≥ 1)
     * @return the full cart view after mutation
     */
    @Transactional
    public CartView addToCart(Long userId, String variantPublicId, int quantity) {
        if (quantity < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantity must be at least 1.");
        }

        ProductVariant variant = requireActiveVariant(variantPublicId);

        // Upsert: if the item already exists for this user+variant, just bump the quantity.
        Optional<CartItem> existing = cartItemRepository
                .findByUserIdAndVariantId(userId, variant.getId());

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            cartItemRepository.save(CartItem.create(userId, variant, quantity));
        }

        return getCart(userId);
    }

    /**
     * Set an explicit quantity for a cart item. If quantity is 0, the item is removed.
     */
    @Transactional
    public CartView updateQuantity(Long userId, String variantPublicId, int quantity) {
        ProductVariant variant = requireVariantExists(variantPublicId);

        if (quantity <= 0) {
            cartItemRepository.deleteByUserIdAndVariantId(userId, variant.getId());
        } else {
            CartItem item = cartItemRepository.findByUserIdAndVariantId(userId, variant.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Item not in cart."));
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return getCart(userId);
    }

    /**
     * Remove a specific item from the cart.
     */
    @Transactional
    public CartView removeFromCart(Long userId, String variantPublicId) {
        ProductVariant variant = requireVariantExists(variantPublicId);
        cartItemRepository.deleteByUserIdAndVariantId(userId, variant.getId());
        return getCart(userId);
    }

    /**
     * Clear the entire cart (e.g. after successful checkout).
     */
    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    /**
     * Retrieve the full cart view, grouped by seller for the checkout preview.
     * Each seller group shows its items, subtotal, and the seller's public id for order splitting.
     */
    @Transactional(readOnly = true)
    public CartView getCart(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserIdWithDetails(userId);

        // Group items by seller public id, preserving insertion order.
        Map<String, CartView.SellerGroup> groups = new LinkedHashMap<>();
        BigDecimal cartTotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem ci : items) {
            ProductVariant v = ci.getVariant();
            Product p = v.getProduct();
            String sellerPubId = p.getSeller().getPublicId();

            CartView.SellerGroup group = groups.computeIfAbsent(sellerPubId,
                    k -> new CartView.SellerGroup(sellerPubId, p.getSeller().getName()));

            // Use sale price if available, otherwise regular price (INV-DB-1: DECIMAL(15,2))
            BigDecimal unitPrice = v.getSalePrice() != null ? v.getSalePrice() : v.getRegularPrice();
            int currentStock = v.getStockQuantity();
            int effectiveQty = Math.min(ci.getQuantity(), Math.max(0, currentStock)); // T11: stock revalidation (clamp)
            if (effectiveQty != ci.getQuantity() && effectiveQty > 0) {
                // persist cap on read (simple reval; full tx in checkout)
                ci.setQuantity(effectiveQty);
                cartItemRepository.save(ci);
            }
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(effectiveQty));

            group.items().add(new CartView.CartItemView(
                    v.getPublicId().toString(),
                    p.getPublicId().toString(),
                    p.getName(),
                    v.getName(),
                    v.getSku(),
                    unitPrice,
                    effectiveQty,
                    lineTotal,
                    currentStock
            ));
            group.addToSubtotal(lineTotal);

            cartTotal = cartTotal.add(lineTotal);
            totalItems += ci.getQuantity();
        }

        return new CartView(List.copyOf(groups.values()), cartTotal, totalItems);
    }

    // ---- Private helpers ----

    /** Validates the variant exists, is active, and its parent product is ACTIVE. */
    private ProductVariant requireActiveVariant(String publicIdStr) {
        ProductVariant variant = requireVariantExists(publicIdStr);
        if (!variant.isActive()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This variant is no longer available.");
        }
        Product product = variant.getProduct();
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This product is not currently available for purchase.");
        }
        return variant;
    }

    /** Looks up a variant by its public UUID. */
    private ProductVariant requireVariantExists(String publicIdStr) {
        UUID publicId;
        try {
            publicId = UUID.fromString(publicIdStr);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid variant ID format.");
        }
        return variantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Variant not found."));
    }

    /** T11/T18: Revalidate + checkout stub using PaymentGateway port (T15/T16). 
     * Follows mandatory model: validate+reval in tx, then provider call outside.
     */
    @Transactional
    public com.archimstechcorner.marketplace.features.cart.api.CartController.CheckoutResult checkout(Long userId) {
        // Reval by loading view (clamps) - inside short tx
        CartView view = getCart(userId);
        if (view.totalItems() == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Cart is empty.");
        }
        String fakeOrderId = "ORD-" + java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase();

        // Provider call - NO MySQL lock held here (after commit of reval)
        PaymentGateway.HostedSession session = paymentGateway.createHostedSession(
            fakeOrderId, view.cartTotal(), "PHP", "http://localhost:3000/checkout?success=true", "http://localhost:3000/checkout?cancel=true");

        // Clear cart after "checkout" for this slice
        clearCart(userId);
        // In real, store session etc.
        return new com.archimstechcorner.marketplace.features.cart.api.CartController.CheckoutResult(fakeOrderId, "PENDING_PAYMENT", view.cartTotal(), session.redirectUrl());
    }
}
