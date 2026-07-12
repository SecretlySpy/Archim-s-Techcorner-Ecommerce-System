package com.archimstechcorner.marketplace.features.sellers.domain.repository;

import com.archimstechcorner.marketplace.features.sellers.domain.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByPublicId(String publicId);
}
