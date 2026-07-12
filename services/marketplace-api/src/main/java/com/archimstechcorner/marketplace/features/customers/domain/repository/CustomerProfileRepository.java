package com.archimstechcorner.marketplace.features.customers.domain.repository;

import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for {@link CustomerProfile}. Private to the customers feature. */
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    Optional<CustomerProfile> findByUserId(Long userId);
}
