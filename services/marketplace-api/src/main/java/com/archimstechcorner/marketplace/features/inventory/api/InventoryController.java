package com.archimstechcorner.marketplace.features.inventory.api;

import com.archimstechcorner.marketplace.features.inventory.application.InventoryBucketView;
import com.archimstechcorner.marketplace.features.inventory.application.InventoryService;
import com.archimstechcorner.marketplace.features.inventory.application.VariantStockView;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Seller stock management. All endpoints require authentication; ownership (the caller must belong to
 * the seller that owns the warehouse and variant) is enforced in {@link InventoryService}.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Set on-hand to an absolute value (and the reorder threshold) for a (warehouse, variant). */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public InventoryBucketView setStock(@Valid @RequestBody SetStockRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return inventoryService.setStock(request.warehousePublicId(), request.variantPublicId(),
                request.quantity(), request.reorderThreshold(), principal.userId());
    }

    /** Apply a signed adjustment (receive/damage/correction). */
    @PostMapping("/adjustments")
    @PreAuthorize("isAuthenticated()")
    public InventoryBucketView adjust(@Valid @RequestBody AdjustStockRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return inventoryService.adjustStock(request.warehousePublicId(), request.variantPublicId(),
                request.delta(), request.reasonCode(), principal.userId());
    }

    /** Stock for a variant across warehouses (owner). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public VariantStockView variantStock(@RequestParam String variantPublicId,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        return inventoryService.getVariantStock(variantPublicId, principal.userId());
    }

    /** Low-stock buckets for a seller (owner). */
    @GetMapping("/low-stock")
    @PreAuthorize("isAuthenticated()")
    public List<InventoryBucketView> lowStock(@RequestParam String sellerPublicId,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return inventoryService.listLowStock(sellerPublicId, principal.userId());
    }

    public record SetStockRequest(
            @NotBlank String warehousePublicId,
            @NotBlank String variantPublicId,
            @NotNull @PositiveOrZero Integer quantity,
            @NotNull @PositiveOrZero Integer reorderThreshold) {
    }

    public record AdjustStockRequest(
            @NotBlank String warehousePublicId,
            @NotBlank String variantPublicId,
            @NotNull Integer delta,
            String reasonCode) {
    }
}
