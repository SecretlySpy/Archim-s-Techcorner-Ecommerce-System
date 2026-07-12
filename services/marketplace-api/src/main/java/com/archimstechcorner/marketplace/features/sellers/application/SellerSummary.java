package com.archimstechcorner.marketplace.features.sellers.application;

import java.util.List;

/** Read model for a seller and its stores/warehouses (mapped inside the service transaction). */
public record SellerSummary(String sellerId, String name, String status, List<StoreSummary> stores) {

    public record StoreSummary(String storeId, String name, String status, List<WarehouseSummary> warehouses) {
    }

    public record WarehouseSummary(String warehouseId, String name) {
    }
}
