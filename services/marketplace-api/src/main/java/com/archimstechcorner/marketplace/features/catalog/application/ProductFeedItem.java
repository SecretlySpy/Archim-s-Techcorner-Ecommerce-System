package com.archimstechcorner.marketplace.features.catalog.application;

import java.math.BigDecimal;

/** A card in the public product feed (guest browsing). Mapped inside the service transaction. */
public record ProductFeedItem(
        String id,
        String name,
        String slug,
        String description,
        BigDecimal minPrice,
        String sellerName) {
}
