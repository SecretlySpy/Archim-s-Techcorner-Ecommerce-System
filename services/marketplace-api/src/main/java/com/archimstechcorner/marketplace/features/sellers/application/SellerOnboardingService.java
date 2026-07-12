package com.archimstechcorner.marketplace.features.sellers.application;

import com.archimstechcorner.marketplace.features.auditlogs.application.AuditService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerRole;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStaff;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Store;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Warehouse;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.features.users.application.RoleAdminService;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SellerOnboardingService {

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;
    private final RoleAdminService roleAdminService;
    private final AuditService auditService;

    public SellerOnboardingService(SellerRepository sellerRepository,
                                   UserRepository userRepository,
                                   RoleAdminService roleAdminService,
                                   AuditService auditService) {
        this.sellerRepository = sellerRepository;
        this.userRepository = userRepository;
        this.roleAdminService = roleAdminService;
        this.auditService = auditService;
    }

    @Transactional
    public Seller onboardSeller(
            String publicUserId,
            String sellerName,
            String taxId,
            String storeName,
            String storeDescription,
            String warehouseName,
            String addressLine1,
            String city,
            String state,
            String country,
            String postalCode) {

        // 1. Resolve the initiating user (their own public id, taken from the authenticated principal).
        UUID userPublicId = UUID.fromString(publicUserId);
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));

        // 2. Create the Seller (starts PENDING until an administrator approves it).
        Seller seller = Seller.create(sellerName, taxId);

        // 3. Make the user the OWNER staff member of the seller.
        SellerStaff owner = SellerStaff.create(seller, user, SellerRole.OWNER);
        seller.addStaff(owner);

        // 4. Create the initial Store.
        Store store = Store.create(seller, storeName, storeDescription);
        seller.addStore(store);

        // 5. Create the initial Warehouse for the store.
        Warehouse warehouse = Warehouse.create(store, warehouseName, addressLine1, city, state, country, postalCode);
        store.addWarehouse(warehouse);

        // 6. Persist everything (cascade handles stores/staff/warehouse).
        Seller saved = sellerRepository.save(seller);

        // 7. Grant the SELLER platform role so the user can access the Seller Center and manage drafts.
        //    (Publishing still requires the seller to be APPROVED — enforced in the catalog module.)
        //    The user must refresh their session to receive ROLE_SELLER in a new access token.
        roleAdminService.assignRole(userPublicId, "SELLER", null, user.getId());

        auditService.record("SELLER_ONBOARDED", "SELLER", saved.getPublicId(), user.getId(),
                "store=" + storeName);
        return saved;
    }
}
