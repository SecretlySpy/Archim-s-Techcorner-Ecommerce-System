package com.archimstechcorner.marketplace.features.sellers.domain.repository;

import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByPublicId(String publicId);
}
