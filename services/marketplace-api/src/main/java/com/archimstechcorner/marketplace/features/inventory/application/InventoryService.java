package com.archimstechcorner.marketplace.features.inventory.application;

import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductVariantRepository;
import com.archimstechcorner.marketplace.features.inventory.domain.model.Inventory;
import com.archimstechcorner.marketplace.features.inventory.domain.model.InventoryAdjustment;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryAdjustmentRepository;
import com.archimstechcorner.marketplace.features.inventory.domain.repository.InventoryRepository;
import com.archimstechcorner.marketplace.features.sellers.application.SellerService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Warehouse;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.WarehouseRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Authoritative stock management. Every mutation is owner-checked (the caller must belong to the seller
 * that owns BOTH the warehouse and the variant), records an immutable {@link InventoryAdjustment}, and
 * keeps the variant's denormalized total in sync. Overselling is prevented: on-hand can never go below
 * zero or below the reserved quantity.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final SellerService sellerService;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryAdjustmentRepository adjustmentRepository,
                            WarehouseRepository warehouseRepository,
                            ProductVariantRepository variantRepository,
                            SellerService sellerService) {
        this.inventoryRepository = inventoryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.warehouseRepository = warehouseRepository;
        this.variantRepository = variantRepository;
        this.sellerService = sellerService;
    }

    /** Sets on-hand to an absolute value and updates the reorder threshold. Records the delta. */
    @Transactional
    public InventoryBucketView setStock(String warehousePublicId, String variantPublicId,
                                        int quantity, int reorderThreshold, Long userId) {
        if (quantity < 0 || reorderThreshold < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Quantities must be non-negative.");
        }
        Target target = resolveAndAuthorize(warehousePublicId, variantPublicId, userId);
        Inventory inv = findOrCreateBucket(target);

        int before = inv.getOnHandQuantity();
        if (quantity < inv.getReservedQuantity()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Cannot set on-hand below the reserved quantity (" + inv.getReservedQuantity() + ").");
        }
        inv.setOnHandQuantity(quantity);
        inv.setReorderThreshold(reorderThreshold);
        inventoryRepository.save(inv);

        int delta = quantity - before;
        if (delta != 0) {
            recordAdjustment(inv, delta, "MANUAL_SET", before, quantity, userId);
        }
        syncVariantTotal(target.variant());
        return toBucketView(inv);
    }

    /** Applies a signed delta (e.g. receive +50, damage -3). Prevents overselling. */
    @Transactional
    public InventoryBucketView adjustStock(String warehousePublicId, String variantPublicId,
                                           int delta, String reasonCode, Long userId) {
        if (delta == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Delta must be non-zero.");
        }
        Target target = resolveAndAuthorize(warehousePublicId, variantPublicId, userId);
        Inventory inv = findOrCreateBucket(target);

        int before = inv.getOnHandQuantity();
        int after = before + delta;
        if (after < 0) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Insufficient stock: adjustment would make on-hand negative.");
        }
        if (after < inv.getReservedQuantity()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Adjustment would drop on-hand below the reserved quantity.");
        }
        inv.setOnHandQuantity(after);
        inventoryRepository.save(inv);

        String reason = (reasonCode == null || reasonCode.isBlank()) ? "MANUAL_ADJUST" : reasonCode;
        recordAdjustment(inv, delta, reason, before, after, userId);
        syncVariantTotal(target.variant());
        return toBucketView(inv);
    }

    /** Stock for one variant across all its warehouse buckets (owner only). */
    @Transactional(readOnly = true)
    public VariantStockView getVariantStock(String variantPublicId, Long userId) {
        ProductVariant variant = requireVariant(variantPublicId);
        sellerService.requireOwnership(variant.getProduct().getSeller().getPublicId(), userId);

        List<Inventory> buckets = inventoryRepository.findByVariant_Id(variant.getId());
        List<InventoryBucketView> views = buckets.stream().map(this::toBucketView).toList();
        int onHand = buckets.stream().mapToInt(Inventory::getOnHandQuantity).sum();
        int reserved = buckets.stream().mapToInt(Inventory::getReservedQuantity).sum();
        return new VariantStockView(variant.getPublicId().toString(), variant.getSku(),
                onHand, reserved, onHand - reserved, views);
    }

    /** Low-stock buckets for a seller (sellable <= reorder threshold). */
    @Transactional(readOnly = true)
    public List<InventoryBucketView> listLowStock(String sellerPublicId, Long userId) {
        Seller seller = sellerService.requireOwnership(sellerPublicId, userId);
        return inventoryRepository.findLowStockBySeller(seller.getId()).stream()
                .map(this::toBucketView)
                .toList();
    }

    // --- helpers ---

    private Target resolveAndAuthorize(String warehousePublicId, String variantPublicId, Long userId) {
        Warehouse warehouse = warehouseRepository.findByPublicId(warehousePublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Warehouse not found."));
        ProductVariant variant = requireVariant(variantPublicId);

        String warehouseSeller = warehouse.getStore().getSeller().getPublicId();
        String variantSeller = variant.getProduct().getSeller().getPublicId();
        if (!warehouseSeller.equals(variantSeller)) {
            // Prevent stocking another seller's product in your warehouse.
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Warehouse and product variant belong to different sellers.");
        }
        sellerService.requireOwnership(warehouseSeller, userId);
        return new Target(warehouse, variant);
    }

    private Inventory findOrCreateBucket(Target target) {
        return inventoryRepository
                .findByWarehouse_IdAndVariant_Id(target.warehouse().getId(), target.variant().getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.create(target.warehouse(), target.variant())));
    }

    private void recordAdjustment(Inventory inv, int delta, String reason, int before, int after, Long userId) {
        adjustmentRepository.save(InventoryAdjustment.create(inv.getId(), delta, reason, before, after, userId));
    }

    /** Keeps ProductVariant.stockQuantity (used in the product view) equal to the sum of on-hand. */
    private void syncVariantTotal(ProductVariant variant) {
        int total = inventoryRepository.findByVariant_Id(variant.getId()).stream()
                .mapToInt(Inventory::getOnHandQuantity)
                .sum();
        variant.setStockQuantity(total);
        variantRepository.save(variant);
    }

    private ProductVariant requireVariant(String variantPublicId) {
        UUID id;
        try {
            id = UUID.fromString(variantPublicId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Product variant not found.");
        }
        return variantRepository.findByPublicId(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product variant not found."));
    }

    private InventoryBucketView toBucketView(Inventory inv) {
        return new InventoryBucketView(
                inv.getWarehouse().getPublicId(),
                inv.getWarehouse().getName(),
                inv.getVariant().getPublicId().toString(),
                inv.getVariant().getSku(),
                inv.getOnHandQuantity(),
                inv.getReservedQuantity(),
                inv.sellable(),
                inv.getReorderThreshold());
    }

    private record Target(Warehouse warehouse, ProductVariant variant) {
    }
}
