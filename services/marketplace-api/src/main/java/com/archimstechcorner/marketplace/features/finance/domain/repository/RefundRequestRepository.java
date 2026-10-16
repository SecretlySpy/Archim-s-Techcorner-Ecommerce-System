package com.archimstechcorner.marketplace.features.finance.domain.repository;

import com.archimstechcorner.marketplace.features.finance.domain.model.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByPublicId(UUID publicId);
    List<RefundRequest> findByParentOrder_Id(Long parentOrderId);
    List<RefundRequest> findByParentOrder_User_Id(Long userId);
    List<RefundRequest> findByStatus(String status);
}
