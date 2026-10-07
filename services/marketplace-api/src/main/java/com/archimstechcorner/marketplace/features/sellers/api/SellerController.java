package com.archimstechcorner.marketplace.features.sellers.api;

import com.archimstechcorner.marketplace.features.sellers.application.SellerOnboardingService;
import com.archimstechcorner.marketplace.features.sellers.application.SellerService;
import com.archimstechcorner.marketplace.features.sellers.application.SellerSummary;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStatus;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sellers")
public class SellerController {

    private final SellerOnboardingService onboardingService;
    private final SellerService sellerService;

    public SellerController(SellerOnboardingService onboardingService, SellerService sellerService) {
        this.onboardingService = onboardingService;
        this.sellerService = sellerService;
    }

    /** Self-service seller registration. The owner is always the authenticated principal. */
    @PostMapping("/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public OnboardSellerResponse onboardSeller(@Valid @RequestBody OnboardSellerRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        // Use the principal's own public id — never a client-supplied id — so a user cannot create a
        // seller owned by someone else. (The old Principal.getName() returned the record's toString.)
        Seller seller = onboardingService.onboardSeller(
                principal.publicId().toString(),
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

    /** Seller Center home: the sellers (and their stores) the caller belongs to. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<SellerSummary> mySellers(@AuthenticationPrincipal AuthenticatedUser principal) {
        return sellerService.listMySellers(principal.userId());
    }

    /** Administrator approval queue — sellers by status (default PENDING). Requires {@code seller.approve}. */
    @GetMapping
    @PreAuthorize("hasAuthority('seller.approve')")
    public List<SellerSummary> listSellers(@RequestParam(defaultValue = "PENDING") SellerStatus status) {
        return sellerService.listByStatus(status);
    }

    /** T10: Public seller storefront info (ACTIVE sellers only). */
    @GetMapping("/{publicId}")
    public SellerSummary publicSeller(@PathVariable String publicId) {
        Seller seller = sellerService.requireSeller(publicId);  // reuse, status check in view or here
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new com.archimstechcorner.marketplace.shared.error.ApiException(com.archimstechcorner.marketplace.shared.error.ErrorCode.NOT_FOUND, "Seller not found.");
        }
        return sellerService.listByStatus(SellerStatus.ACTIVE).stream()
                .filter(s -> s.sellerId().equals(publicId))
                .findFirst()
                .orElseThrow(() -> new com.archimstechcorner.marketplace.shared.error.ApiException(com.archimstechcorner.marketplace.shared.error.ErrorCode.NOT_FOUND, "Seller not found."));
    }

    /** Administrator approves a pending seller (PENDING → ACTIVE). Requires {@code seller.approve}. */
    @PostMapping("/{publicId}/approve")
    @PreAuthorize("hasAuthority('seller.approve')")
    public OnboardSellerResponse approve(@PathVariable String publicId,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        String status = sellerService.approve(publicId, actor.userId()).name();
        return new OnboardSellerResponse(publicId, status);
    }

    /** Administrator rejects/suspends a seller. Requires {@code seller.reject}. */
    @PostMapping("/{publicId}/reject")
    @PreAuthorize("hasAuthority('seller.reject')")
    public OnboardSellerResponse reject(@PathVariable String publicId,
                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        String status = sellerService.reject(publicId, actor.userId()).name();
        return new OnboardSellerResponse(publicId, status);
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
