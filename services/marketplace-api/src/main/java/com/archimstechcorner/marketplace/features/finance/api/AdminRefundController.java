package com.archimstechcorner.marketplace.features.finance.api;

import com.archimstechcorner.marketplace.features.finance.application.RefundService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('refund.approve.standard')")
    public List<RefundService.RefundView> listPendingRefunds() {
        return refundService.listPendingRefunds();
    }

    @PostMapping("/{refundId}/approve")
    @PreAuthorize("hasAuthority('refund.approve.standard')")
    public RefundService.RefundView approveRefund(
            @PathVariable String refundId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return refundService.approveRefund(refundId, principal.userId());
    }

    @PostMapping("/{refundId}/reject")
    @PreAuthorize("hasAuthority('refund.approve.standard')")
    public RefundService.RefundView rejectRefund(
            @PathVariable String refundId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return refundService.rejectRefund(refundId, principal.userId());
    }
}
