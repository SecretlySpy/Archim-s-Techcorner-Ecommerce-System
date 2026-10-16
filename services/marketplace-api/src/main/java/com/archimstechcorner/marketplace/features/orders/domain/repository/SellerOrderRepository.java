package com.archimstechcorner.marketplace.features.orders.domain.repository;

import com.archimstechcorner.marketplace.features.orders.domain.model.SellerOrder;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerOrderRepository extends JpaRepository<SellerOrder, Long> {
    Optional<SellerOrder> findByPublicId(UUID publicId);
    List<SellerOrder> findBySellerOrderByCreatedAtDesc(Seller seller);
    List<SellerOrder> findByParentOrder_Id(Long parentOrderId);
}
