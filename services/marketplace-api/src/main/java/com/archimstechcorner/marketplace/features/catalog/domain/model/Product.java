package com.archimstechcorner.marketplace.features.catalog.domain.model;

import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSpecification> specifications = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {
        // JPA requires protected no-arg constructor
    }

    public static Product create(Seller seller, Category category, Brand brand, String name, String slug, String description) {
        Product product = new Product();
        product.publicId = UUID.randomUUID();
        product.seller = seller;
        product.category = category;
        product.brand = brand;
        product.name = name;
        product.slug = slug;
        product.description = description;
        product.status = ProductStatus.DRAFT;
        product.createdAt = LocalDateTime.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public void addSpecification(ProductSpecification spec) {
        specifications.add(spec);
        spec.setProduct(this);
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Seller getSeller() {
        return seller;
    }

    public void setSeller(Seller seller) {
        this.seller = seller;
        this.updatedAt = LocalDateTime.now();
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
        this.updatedAt = LocalDateTime.now();
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
        this.updatedAt = LocalDateTime.now();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariant> variants) {
        this.variants.clear();
        if (variants != null) {
            this.variants.addAll(variants);
            for (ProductVariant variant : variants) {
                variant.setProduct(this);
            }
        }
    }

    public List<ProductSpecification> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(List<ProductSpecification> specifications) {
        this.specifications.clear();
        if (specifications != null) {
            this.specifications.addAll(specifications);
            for (ProductSpecification spec : specifications) {
                spec.setProduct(this);
            }
        }
    }

    public List<ProductImage> getImages() {
        return images;
    }

    public void setImages(List<ProductImage> images) {
        this.images.clear();
        if (images != null) {
            this.images.addAll(images);
            for (ProductImage image : images) {
                image.setProduct(this);
            }
        }
    }
}
