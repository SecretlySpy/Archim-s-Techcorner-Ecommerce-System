package com.archimstechcorner.marketplace.features.orders.domain.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    PARTIALLY_SHIPPED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
