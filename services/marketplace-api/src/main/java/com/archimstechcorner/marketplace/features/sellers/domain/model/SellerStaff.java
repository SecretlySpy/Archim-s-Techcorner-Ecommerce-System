package com.archimstechcorner.marketplace.features.sellers.domain.model;

import com.archimstechcorner.marketplace.features.users.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seller_staff")
public class SellerStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private SellerRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SellerStaff() {
        // JPA only
    }

    private SellerStaff(Seller seller, User user, SellerRole role) {
        this.seller = seller;
        this.user = user;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public static SellerStaff create(Seller seller, User user, SellerRole role) {
        return new SellerStaff(seller, user, role);
    }

    // Getters

    public Long getId() {
        return id;
    }

    public Seller getSeller() {
        return seller;
    }

    public User getUser() {
        return user;
    }

    public SellerRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
