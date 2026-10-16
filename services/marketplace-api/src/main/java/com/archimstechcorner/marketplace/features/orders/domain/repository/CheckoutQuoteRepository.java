package com.archimstechcorner.marketplace.features.orders.domain.repository;

import com.archimstechcorner.marketplace.features.orders.domain.model.CheckoutQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutQuoteRepository extends JpaRepository<CheckoutQuote, Long> {
    Optional<CheckoutQuote> findByPublicId(UUID publicId);
}
