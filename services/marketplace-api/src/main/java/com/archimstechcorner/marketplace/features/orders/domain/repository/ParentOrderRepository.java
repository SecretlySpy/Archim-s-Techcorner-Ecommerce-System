package com.archimstechcorner.marketplace.features.orders.domain.repository;

import com.archimstechcorner.marketplace.features.orders.domain.model.ParentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentOrderRepository extends JpaRepository<ParentOrder, Long> {
    Optional<ParentOrder> findByPublicId(UUID publicId);
    Optional<ParentOrder> findByIdempotencyKey(String idempotencyKey);
    List<ParentOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
}
