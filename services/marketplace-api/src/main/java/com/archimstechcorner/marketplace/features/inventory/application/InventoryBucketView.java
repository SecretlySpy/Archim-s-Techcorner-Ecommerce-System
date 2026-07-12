package com.archimstechcorner.marketplace.features.inventory.application;

/** Read model for one (warehouse, variant) stock bucket. Mapped inside the service transaction. */
public record InventoryBucketView(
        String warehousePublicId,
        String warehouseName,
        String variantPublicId,
        String sku,
        int onHand,
        int reserved,
        int sellable,
        int reorderThreshold) {
}
