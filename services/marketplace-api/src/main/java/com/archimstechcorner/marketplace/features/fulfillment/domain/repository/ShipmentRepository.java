package com.archimstechcorner.marketplace.features.fulfillment.domain.repository;

import com.archimstechcorner.marketplace.features.fulfillment.domain.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByPublicId(UUID publicId);
    List<Shipment> findBySellerOrder_Id(Long sellerOrderId);
    List<Shipment> findByCourierCodeAndTrackingNumber(String courierCode, String trackingNumber);
}
