package com.archimstechcorner.marketplace.features.catalog.domain.repository;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findBySlug(String slug);
}
