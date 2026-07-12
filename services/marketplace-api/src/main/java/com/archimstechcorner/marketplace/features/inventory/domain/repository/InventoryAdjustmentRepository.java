package com.archimstechcorner.marketplace.features.inventory.domain.repository;

import com.archimstechcorner.marketplace.features.inventory.domain.model.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for the append-only stock movement ledger. */
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    List<InventoryAdjustment> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId);
}
