package com.archimstechcorner.marketplace.features.finance.domain.model;

import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_ledger_entries")
public class SellerLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_order_id")
    private SellerOrder sellerOrder;

    @Column(name = "transaction_type", nullable = false, length = 40)
    private String transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SellerLedgerEntry() {}

    public static SellerLedgerEntry create(Seller seller, SellerOrder sellerOrder, String transactionType,
                                           BigDecimal amount, String currencyCode) {
        SellerLedgerEntry entry = new SellerLedgerEntry();
        entry.publicId = UUID.randomUUID();
        entry.seller = seller;
        entry.sellerOrder = sellerOrder;
        entry.transactionType = transactionType;
        entry.amount = amount;
        entry.currencyCode = currencyCode != null ? currencyCode : "PHP";
        entry.createdAt = Instant.now();
        return entry;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public Seller getSeller() { return seller; }
    public SellerOrder getSellerOrder() { return sellerOrder; }
    public String getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getCreatedAt() { return createdAt; }
}
