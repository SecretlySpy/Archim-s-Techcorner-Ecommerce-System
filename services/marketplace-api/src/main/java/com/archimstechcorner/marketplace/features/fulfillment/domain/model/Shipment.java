package com.archimstechcorner.marketplace.features.fulfillment.domain.model;

import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_order_id", nullable = false)
    private SellerOrder sellerOrder;

    @Column(name = "courier_code", nullable = false, length = 50)
    private String courierCode;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "shipped_at", nullable = false)
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {}

    public static Shipment create(SellerOrder sellerOrder, String courierCode, String trackingNumber) {
        Shipment shipment = new Shipment();
        shipment.publicId = UUID.randomUUID();
        shipment.sellerOrder = sellerOrder;
        shipment.courierCode = courierCode;
        shipment.trackingNumber = trackingNumber;
        shipment.status = "DISPATCHED";
        shipment.shippedAt = Instant.now();
        shipment.createdAt = Instant.now();
        shipment.updatedAt = Instant.now();
        return shipment;
    }

    public void markDelivered() {
        this.status = "DELIVERED";
        this.deliveredAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public SellerOrder getSellerOrder() { return sellerOrder; }
    public String getCourierCode() { return courierCode; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getStatus() { return status; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
