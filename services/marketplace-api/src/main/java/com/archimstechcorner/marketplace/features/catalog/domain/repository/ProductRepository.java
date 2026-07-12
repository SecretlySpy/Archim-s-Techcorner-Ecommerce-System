package com.archimstechcorner.marketplace.features.catalog.domain.repository;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByPublicId(UUID publicId);
    Optional<Product> findBySlug(String slug);
    Page<Product> findBySeller(Seller seller, Pageable pageable);
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /** A seller's own products (any status), newest first — for the Seller Center list. */
    List<Product> findBySellerOrderByCreatedAtDesc(Seller seller);
}
