package com.archimstechcorner.marketplace.features.cart.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-model for the shopping cart. Items are grouped by seller so the checkout process can
 * split a multi-seller cart into per-seller sub-orders.
 */
public record CartView(
        List<SellerGroup> sellerGroups,
        BigDecimal cartTotal,
        int totalItems
) {

    /**
     * A group of cart items from the same seller. Mutable subtotal for building during the
     * grouping pass in {@link CartService#getCart}.
     */
    public static final class SellerGroup {
        private final String sellerPublicId;
        private final String sellerName;
        private final List<CartItemView> items = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;

        public SellerGroup(String sellerPublicId, String sellerName) {
            this.sellerPublicId = sellerPublicId;
            this.sellerName = sellerName;
        }

        public void addToSubtotal(BigDecimal amount) {
            this.subtotal = this.subtotal.add(amount);
        }

        public String sellerPublicId() { return sellerPublicId; }
        public String sellerName() { return sellerName; }
        public List<CartItemView> items() { return items; }
        public BigDecimal subtotal() { return subtotal; }
    }

    /**
     * A single cart line-item with enough detail for frontend display:
     * variant info, current price, quantity, line total, and available stock for soft warnings.
     */
    public record CartItemView(
            String variantPublicId,
            String productPublicId,
            String productName,
            String variantName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            int availableStock
    ) {}
}
