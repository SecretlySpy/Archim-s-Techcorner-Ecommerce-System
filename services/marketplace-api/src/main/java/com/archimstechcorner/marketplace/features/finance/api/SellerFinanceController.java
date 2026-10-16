package com.archimstechcorner.marketplace.features.finance.api;

import com.archimstechcorner.marketplace.features.finance.application.SellerBalanceService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sellers/{sellerId}")
public class SellerFinanceController {

    private final SellerBalanceService sellerBalanceService;

    public SellerFinanceController(SellerBalanceService sellerBalanceService) {
        this.sellerBalanceService = sellerBalanceService;
    }

    @GetMapping("/balance")
    @PreAuthorize("isAuthenticated()")
    public SellerBalanceService.SellerBalanceSummary getSellerBalance(
            @PathVariable String sellerId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean isAdminOrFinance = principal.authorities().contains("ROLE_ADMIN")
                || principal.authorities().contains("ROLE_FINANCE");
        return sellerBalanceService.getSellerBalance(sellerId, principal.userId(), isAdminOrFinance);
    }

    @GetMapping("/ledger")
    @PreAuthorize("isAuthenticated()")
    public List<SellerBalanceService.SellerLedgerView> listLedgerEntries(
            @PathVariable String sellerId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean isAdminOrFinance = principal.authorities().contains("ROLE_ADMIN")
                || principal.authorities().contains("ROLE_FINANCE");
        return sellerBalanceService.listLedgerEntries(sellerId, principal.userId(), isAdminOrFinance);
    }
}
