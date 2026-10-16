package com.archimstechcorner.marketplace.features.inventory.domain.repository;

import com.archimstechcorner.marketplace.features.inventory.domain.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Persistence for stock buckets. Private to the inventory feature. */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByWarehouse_IdAndVariant_Id(Long warehouseId, Long variantId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.warehouse.id = :warehouseId AND i.variant.id = :variantId")
    Optional<Inventory> findByWarehouse_IdAndVariant_IdWithLock(@Param("warehouseId") Long warehouseId, @Param("variantId") Long variantId);

    List<Inventory> findByVariant_Id(Long variantId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId ORDER BY i.id ASC")
    List<Inventory> findByVariant_IdWithLock(@Param("variantId") Long variantId);

    /** Low-stock buckets for a seller: sellable (on_hand - reserved) at or below the reorder threshold. */
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.warehouse.store.seller.id = :sellerId
              AND (i.onHandQuantity - i.reservedQuantity) <= i.reorderThreshold
            """)
    List<Inventory> findLowStockBySeller(@Param("sellerId") Long sellerId);
}
