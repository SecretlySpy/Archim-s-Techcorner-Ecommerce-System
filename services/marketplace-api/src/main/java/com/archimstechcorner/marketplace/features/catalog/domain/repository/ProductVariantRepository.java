package com.archimstechcorner.marketplace.features.catalog.domain.repository;

import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByPublicId(UUID publicId);
    Optional<ProductVariant> findBySku(String sku);
}
