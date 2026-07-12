package com.archimstechcorner.marketplace.features.sellers.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStatus;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Store;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seller administration and access checks used across features.
 *
 * <p>Approval/suspension are administrator actions (permission {@code seller.approve}); ownership
 * verification enforces tenant isolation (a user may only act on a seller they belong to) and is the
 * authorization backstop for catalog writes — RBAC scope {@code store} in rbac-matrix.md.
 */
@Service
public class SellerService {

    private final SellerRepository sellerRepository;
    private final AuditService auditService;

    public SellerService(SellerRepository sellerRepository, AuditService auditService) {
        this.sellerRepository = sellerRepository;
        this.auditService = auditService;
    }

    /** Loads a seller by public id or fails with a 404 (never a 500). */
    @Transactional(readOnly = true)
    public Seller requireSeller(String publicId) {
        return sellerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller not found."));
    }

    /**
     * Verifies the given user is staff/owner of the seller, returning the seller. Throws 403 otherwise.
     * This is the per-request ownership check that keeps seller A out of seller B's data.
     */
    @Transactional(readOnly = true)
    public Seller requireOwnership(String sellerPublicId, Long userId) {
        Seller seller = requireSeller(sellerPublicId);
        boolean belongs = seller.getStaffMembers().stream()
                .anyMatch(staff -> staff.getUser().getId().equals(userId));
        if (!belongs) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this seller.");
        }
        return seller;
    }

    /** Admin approval: PENDING → ACTIVE. Audited. Requires the {@code seller.approve} permission upstream. */
    @Transactional
    public SellerStatus approve(String sellerPublicId, Long actorUserId) {
        Seller seller = requireSeller(sellerPublicId);
        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Only a pending seller can be approved.");
        }
        seller.activate();
        sellerRepository.save(seller);
        auditService.record("SELLER_APPROVED", "SELLER", sellerPublicId, actorUserId, null);
        return seller.getStatus();
    }

    /** Admin rejection/suspension: moves the seller to SUSPENDED. Audited. */
    @Transactional
    public SellerStatus reject(String sellerPublicId, Long actorUserId) {
        Seller seller = requireSeller(sellerPublicId);
        seller.suspend();
        sellerRepository.save(seller);
        auditService.record("SELLER_REJECTED", "SELLER", sellerPublicId, actorUserId, null);
        return seller.getStatus();
    }

    /** Sellers the given user belongs to (Seller Center home). Mapped in-tx to a read model. */
    @Transactional(readOnly = true)
    public List<SellerSummary> listMySellers(Long userId) {
        return sellerRepository.findDistinctByStaffMembers_User_Id(userId).stream()
                .map(SellerService::toSummary)
                .toList();
    }

    /** Admin approval queue — sellers in a given status. */
    @Transactional(readOnly = true)
    public List<SellerSummary> listByStatus(SellerStatus status) {
        return sellerRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(SellerService::toSummary)
                .toList();
    }

    private static SellerSummary toSummary(Seller seller) {
        List<SellerSummary.StoreSummary> stores = seller.getStores().stream()
                .map(SellerService::toStoreSummary)
                .toList();
        return new SellerSummary(seller.getPublicId(), seller.getName(), seller.getStatus().name(), stores);
    }

    private static SellerSummary.StoreSummary toStoreSummary(Store store) {
        List<SellerSummary.WarehouseSummary> warehouses = store.getWarehouses().stream()
                .map(w -> new SellerSummary.WarehouseSummary(w.getPublicId(), w.getName()))
                .toList();
        return new SellerSummary.StoreSummary(store.getPublicId(), store.getName(), store.getStatus().name(), warehouses);
    }
}
