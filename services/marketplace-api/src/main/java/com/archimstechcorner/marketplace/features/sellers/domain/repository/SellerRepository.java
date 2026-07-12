package com.archimstechcorner.marketplace.features.sellers.domain.repository;

import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByPublicId(String publicId);

    /** Admin approval queue — sellers filtered by status, newest first. */
    List<Seller> findByStatusOrderByCreatedAtDesc(SellerStatus status);

    /** Sellers the given user belongs to (as owner or staff). */
    List<Seller> findDistinctByStaffMembers_User_Id(Long userId);
}
