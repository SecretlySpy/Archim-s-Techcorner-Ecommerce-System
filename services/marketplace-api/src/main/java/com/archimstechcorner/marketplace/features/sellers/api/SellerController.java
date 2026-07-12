package com.archimstechcorner.marketplace.features.sellers.api;

import com.archimstechcorner.marketplace.features.sellers.application.SellerOnboardingService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/sellers")
public class SellerController {

    private final SellerOnboardingService onboardingService;

    public SellerController(SellerOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public OnboardSellerResponse onboardSeller(@Valid @RequestBody OnboardSellerRequest request, Principal principal) {
        Seller seller = onboardingService.onboardSeller(
                principal.getName(), // Assuming the Principal name is the user's public_id (from JWT subject)
                request.sellerName(),
                request.taxId(),
                request.storeName(),
                request.storeDescription(),
                request.warehouseName(),
                request.addressLine1(),
                request.city(),
                request.state(),
                request.country(),
                request.postalCode()
        );
        
        return new OnboardSellerResponse(seller.getPublicId(), seller.getStatus().name());
    }

    public record OnboardSellerRequest(
            @NotBlank String sellerName,
            String taxId,
            @NotBlank String storeName,
            String storeDescription,
            @NotBlank String warehouseName,
            @NotBlank String addressLine1,
            @NotBlank String city,
            @NotBlank String state,
            @NotBlank String country,
            @NotBlank String postalCode
    ) {}

    public record OnboardSellerResponse(
            String sellerId,
            String status
    ) {}
}
