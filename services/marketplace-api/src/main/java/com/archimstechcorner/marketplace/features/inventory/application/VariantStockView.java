package com.archimstechcorner.marketplace.features.inventory.application;

import java.util.List;

/** Aggregate stock for a variant across all its warehouse buckets. */
public record VariantStockView(
        String variantPublicId,
        String sku,
        int totalOnHand,
        int totalReserved,
        int totalSellable,
        List<InventoryBucketView> buckets) {
}
