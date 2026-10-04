package com.archimstechcorner.marketplace.features.customers.domain.repository;

import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserId(Long userId);

    Optional<CustomerAddress> findByPublicId(UUID publicId);

    Optional<CustomerAddress> findByUserIdAndIsDefaultTrueAndAddressType(Long userId, String addressType);
}
