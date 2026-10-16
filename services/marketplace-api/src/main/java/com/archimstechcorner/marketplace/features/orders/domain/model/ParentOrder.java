package com.archimstechcorner.marketplace.features.orders.domain.model;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parent_orders")
public class ParentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "parentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SellerOrder> sellerOrders = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrderAddress shippingAddress;

    protected ParentOrder() {}

    public static ParentOrder create(User user, BigDecimal totalAmount, String currencyCode, String idempotencyKey) {
        ParentOrder order = new ParentOrder();
        order.publicId = UUID.randomUUID();
        order.user = user;
        order.totalAmount = totalAmount;
        order.currencyCode = currencyCode != null ? currencyCode : "PHP";
        order.status = OrderStatus.PENDING_PAYMENT;
        order.idempotencyKey = idempotencyKey;
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();
        return order;
    }

    public void addSellerOrder(SellerOrder sellerOrder) {
        sellerOrders.add(sellerOrder);
        sellerOrder.setParentOrder(this);
    }

    public void setShippingAddress(OrderAddress address) {
        this.shippingAddress = address;
        if (address != null) {
            address.setOrder(this);
        }
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public User getUser() { return user; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<SellerOrder> getSellerOrders() { return sellerOrders; }
    public OrderAddress getShippingAddress() { return shippingAddress; }
}
