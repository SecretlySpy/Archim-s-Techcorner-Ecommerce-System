package com.archimstechcorner.marketplace.features.catalog.application;

import java.util.List;

/** A page of the public product feed. */
public record FeedPage(
        List<ProductFeedItem> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
