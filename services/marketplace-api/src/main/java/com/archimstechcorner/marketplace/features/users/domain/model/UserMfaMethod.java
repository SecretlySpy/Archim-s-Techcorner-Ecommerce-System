package com.archimstechcorner.marketplace.features.users.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_mfa_methods")
public class UserMfaMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "method_type", nullable = false, length = 50)
    private String methodType;

    @Column(name = "secret_key", nullable = false, length = 255)
    private String secretKey;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserMfaMethod() {
        // JPA requires default constructor
    }

    private UserMfaMethod(User user, String methodType, String secretKey, boolean verified) {
        this.user = user;
        this.methodType = methodType;
        this.secretKey = secretKey;
        this.verified = verified;
    }

    public static UserMfaMethod create(User user, String methodType, String secretKey) {
        return new UserMfaMethod(user, methodType, secretKey, false);
    }

    public void verify() {
        this.verified = true;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getMethodType() {
        return methodType;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
