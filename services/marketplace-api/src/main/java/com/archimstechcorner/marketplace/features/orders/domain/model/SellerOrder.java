package com.archimstechcorner.marketplace.features.orders.domain.model;

import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "seller_orders")
public class SellerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_order_id", nullable = false)
    private ParentOrder parentOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "sellerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected SellerOrder() {}

    public static SellerOrder create(ParentOrder parentOrder, Seller seller, BigDecimal subtotal) {
        SellerOrder order = new SellerOrder();
        order.publicId = UUID.randomUUID();
        order.parentOrder = parentOrder;
        order.seller = seller;
        order.subtotal = subtotal;
        order.status = SellerOrderStatus.PENDING;
        order.createdAt = Instant.now();
        return order;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setSellerOrder(this);
    }

    public void setParentOrder(ParentOrder parentOrder) {
        this.parentOrder = parentOrder;
    }

    public void setStatus(SellerOrderStatus status) {
        this.status = status;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public ParentOrder getParentOrder() { return parentOrder; }
    public Seller getSeller() { return seller; }
    public BigDecimal getSubtotal() { return subtotal; }
    public SellerOrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
}
