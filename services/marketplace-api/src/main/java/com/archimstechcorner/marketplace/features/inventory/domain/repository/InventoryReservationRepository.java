package com.archimstechcorner.marketplace.features.inventory.domain.repository;

import com.archimstechcorner.marketplace.features.inventory.domain.model.InventoryReservation;
import com.archimstechcorner.marketplace.features.inventory.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByPublicId(UUID publicId);
    List<InventoryReservation> findByOrderId(Long orderId);
    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant cutoff);
}
