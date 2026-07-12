package com.archimstechcorner.marketplace.features.sellers.application;

import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerRole;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStaff;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Store;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Warehouse;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerOnboardingService {

    private final SellerRepository sellerRepository;
    private final UserRepository userRepository;

    public SellerOnboardingService(SellerRepository sellerRepository, UserRepository userRepository) {
        this.sellerRepository = sellerRepository;
        this.userRepository = userRepository;
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

        // 1. Resolve User
        User user = userRepository.findByPublicId(java.util.UUID.fromString(publicUserId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + publicUserId));

        // 2. Create Seller
        Seller seller = Seller.create(sellerName, taxId);

        // 3. Make the user the OWNER of the seller
        SellerStaff owner = SellerStaff.create(seller, user, SellerRole.OWNER);
        seller.addStaff(owner);

        // 4. Create the initial Store
        Store store = Store.create(seller, storeName, storeDescription);
        seller.addStore(store);

        // 5. Create the initial Warehouse for the store
        Warehouse warehouse = Warehouse.create(store, warehouseName, addressLine1, city, state, country, postalCode);
        store.addWarehouse(warehouse);

        // 6. Persist everything (cascade handles the rest)
        return sellerRepository.save(seller);
    }
}
