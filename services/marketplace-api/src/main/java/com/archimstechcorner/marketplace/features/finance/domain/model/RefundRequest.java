package com.archimstechcorner.marketplace.features.finance.domain.model;

import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_order_id", nullable = false)
    private ParentOrder parentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_order_id")
    private SellerOrder sellerOrder;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RefundRequest() {}

    public static RefundRequest create(ParentOrder parentOrder, SellerOrder sellerOrder,
                                       BigDecimal requestedAmount, String reason) {
        RefundRequest req = new RefundRequest();
        req.publicId = UUID.randomUUID();
        req.parentOrder = parentOrder;
        req.sellerOrder = sellerOrder;
        req.requestedAmount = requestedAmount;
        req.reason = reason;
        req.status = "PENDING";
        req.createdAt = Instant.now();
        req.updatedAt = Instant.now();
        return req;
    }

    public void approve(User approver) {
        this.status = "APPROVED";
        this.approvedByUser = approver;
        this.updatedAt = Instant.now();
    }

    public void reject(User approver) {
        this.status = "REJECTED";
        this.approvedByUser = approver;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public ParentOrder getParentOrder() { return parentOrder; }
    public SellerOrder getSellerOrder() { return sellerOrder; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public User getApprovedByUser() { return approvedByUser; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
