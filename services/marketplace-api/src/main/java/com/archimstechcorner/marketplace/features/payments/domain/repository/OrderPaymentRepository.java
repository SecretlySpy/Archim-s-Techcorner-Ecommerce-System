package com.archimstechcorner.marketplace.features.payments.domain.repository;

import com.archimstechcorner.marketplace.features.payments.domain.model.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {
    Optional<OrderPayment> findByPublicId(UUID publicId);
    Optional<OrderPayment> findByIdempotencyKey(String idempotencyKey);
    Optional<OrderPayment> findByProviderPaymentId(String providerPaymentId);
    Optional<OrderPayment> findByOrderId(Long orderId);
}
