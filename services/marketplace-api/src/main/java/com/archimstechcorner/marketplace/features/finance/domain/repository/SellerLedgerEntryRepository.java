package com.archimstechcorner.marketplace.features.finance.domain.repository;

import com.archimstechcorner.marketplace.features.finance.domain.model.SellerLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerLedgerEntryRepository extends JpaRepository<SellerLedgerEntry, Long> {
    Optional<SellerLedgerEntry> findByPublicId(UUID publicId);
    List<SellerLedgerEntry> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);
    List<SellerLedgerEntry> findBySellerOrder_Id(Long sellerOrderId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM SellerLedgerEntry e WHERE e.seller.id = :sellerId")
    BigDecimal calculateSellerBalance(@Param("sellerId") Long sellerId);
}
