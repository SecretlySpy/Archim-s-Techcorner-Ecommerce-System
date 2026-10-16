package com.archimstechcorner.marketplace.features.orders.domain.model;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_quotes")
public class CheckoutQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "shipping_address_id", nullable = false)
    private Long shippingAddressId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "items_json", nullable = false, columnDefinition = "JSON")
    private String itemsJson;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CheckoutQuote() {}

    public static CheckoutQuote create(User user, Long shippingAddressId, BigDecimal subtotal,
                                       BigDecimal shippingFee, BigDecimal taxAmount, BigDecimal totalAmount,
                                       String currencyCode, String itemsJson, Instant expiresAt) {
        CheckoutQuote quote = new CheckoutQuote();
        quote.publicId = UUID.randomUUID();
        quote.user = user;
        quote.shippingAddressId = shippingAddressId;
        quote.subtotal = subtotal;
        quote.shippingFee = shippingFee;
        quote.taxAmount = taxAmount;
        quote.totalAmount = totalAmount;
        quote.currencyCode = currencyCode != null ? currencyCode : "PHP";
        quote.itemsJson = itemsJson;
        quote.used = false;
        quote.expiresAt = expiresAt;
        quote.createdAt = Instant.now();
        return quote;
    }

    public void markUsed() {
        this.used = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public User getUser() { return user; }
    public Long getShippingAddressId() { return shippingAddressId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getItemsJson() { return itemsJson; }
    public boolean isUsed() { return used; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
