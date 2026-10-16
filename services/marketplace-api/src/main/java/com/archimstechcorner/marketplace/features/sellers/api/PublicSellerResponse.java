package com.archimstechcorner.marketplace.features.sellers.api;

/**
 * Public, privacy-safe seller projection for storefront browsing.
 * Internal fields such as tax IDs, warehouse locations, and staff assignments are excluded.
 */
public record PublicSellerResponse(
        String sellerId,
        String sellerName,
        String storeName,
        String storeDescription
) {
}
