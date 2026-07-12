package com.archimstechcorner.marketplace.features.catalog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_specifications")
public class ProductSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String name;

    @Column(name = "spec_value", nullable = false)
    private String specValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    protected ProductSpecification() {
        // JPA requires protected no-arg constructor
    }

    public static ProductSpecification create(Product product, String name, String specValue, int displayOrder) {
        ProductSpecification spec = new ProductSpecification();
        spec.product = product;
        spec.name = name;
        spec.specValue = specValue;
        spec.displayOrder = displayOrder;
        return spec;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecValue() {
        return specValue;
    }

    public void setSpecValue(String specValue) {
        this.specValue = specValue;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
