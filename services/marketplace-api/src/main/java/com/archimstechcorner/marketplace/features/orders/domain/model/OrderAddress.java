package com.archimstechcorner.marketplace.features.orders.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_addresses")
public class OrderAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ParentOrder order;

    @Column(name = "address_type", nullable = false, length = 20)
    private String addressType = "SHIPPING";

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "line1", nullable = false, length = 255)
    private String line1;

    @Column(name = "line2", length = 255)
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderAddress() {}

    public static OrderAddress create(ParentOrder order, String recipientName, String phone,
                                      String line1, String line2, String city,
                                      String state, String postalCode, String country) {
        OrderAddress address = new OrderAddress();
        address.order = order;
        address.addressType = "SHIPPING";
        address.recipientName = recipientName;
        address.phone = phone;
        address.line1 = line1;
        address.line2 = line2;
        address.city = city;
        address.state = state;
        address.postalCode = postalCode;
        address.country = (country != null && !country.isBlank()) ? country : "PH";
        address.createdAt = Instant.now();
        return address;
    }

    public void setOrder(ParentOrder order) {
        this.order = order;
    }

    public Long getId() { return id; }
    public ParentOrder getOrder() { return order; }
    public String getAddressType() { return addressType; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }
}
