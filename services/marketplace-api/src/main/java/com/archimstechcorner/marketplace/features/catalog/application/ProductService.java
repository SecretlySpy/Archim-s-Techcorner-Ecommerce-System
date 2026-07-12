package com.archimstechcorner.marketplace.features.catalog.application;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Brand;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.BrandRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.CategoryRepository;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductRepository;
import com.archimstechcorner.marketplace.features.sellers.application.SellerService;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.model.SellerStatus;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Catalog write/read use cases. Every seller-scoped mutation verifies the caller owns the target seller
 * (tenant isolation) via {@link SellerService#requireOwnership}. Not-found/invalid-state conditions
 * raise {@link ApiException} so they map to 404/409 rather than an opaque 500.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SellerService sellerService;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          BrandRepository brandRepository,
                          SellerService sellerService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.sellerService = sellerService;
    }

    /** Creates a DRAFT product. The caller must be staff/owner of {@code sellerPublicId}. */
    @Transactional
    public Product createProduct(String sellerPublicId, Long categoryId, Long brandId,
                                 String name, String slug, String description, Long currentUserId) {
        // Ownership check is the authorization backstop — a user cannot list under another seller.
        Seller seller = sellerService.requireOwnership(sellerPublicId, currentUserId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found."));

        Brand brand = null;
        if (brandId != null) {
            brand = brandRepository.findById(brandId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Brand not found."));
        }

        Product product = Product.create(seller, category, brand, name, slug, description);
        return productRepository.save(product);
    }

    @Transactional
    public ProductVariant addVariant(String productPublicId, String sku, String name,
                                     BigDecimal regularPrice, int stockQuantity, String attributes) {
        Product product = requireProduct(productPublicId);
        ProductVariant variant = ProductVariant.create(product, sku, name, regularPrice, stockQuantity, attributes);
        product.addVariant(variant);
        return variant;
    }

    /** Seller submits a product for moderation. Requires ownership and an APPROVED (ACTIVE) seller. */
    @Transactional
    public void submitForApproval(String productPublicId, Long currentUserId) {
        Product product = requireProduct(productPublicId);
        Seller seller = product.getSeller();

        boolean owns = seller.getStaffMembers().stream()
                .anyMatch(staff -> staff.getUser().getId().equals(currentUserId));
        if (!owns) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not have access to this product.");
        }
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Seller must be approved before publishing products.");
        }
        if (product.getStatus() != ProductStatus.DRAFT && product.getStatus() != ProductStatus.REJECTED) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Product cannot be submitted from status " + product.getStatus() + ".");
        }

        product.setStatus(ProductStatus.PENDING_APPROVAL);
        productRepository.save(product);
    }

    /** Administrator moderation (permission {@code product.moderate}). */
    @Transactional
    public void moderateProduct(String productPublicId, ProductStatus newStatus) {
        Product product = requireProduct(productPublicId);
        product.setStatus(newStatus);
        productRepository.save(product);
    }

    /** Internal lookup (any status). Used by owning/admin flows. */
    @Transactional(readOnly = true)
    public Product getProduct(String productPublicId) {
        Product product = requireProduct(productPublicId);
        product.getVariants().size(); // force-init lazy collection in-tx (open-in-view is off)
        return product;
    }

    /** Public lookup — returns the product only if it is published (ACTIVE); otherwise 404. */
    @Transactional(readOnly = true)
    public Product getPublishedProduct(String productPublicId) {
        Product product = requireProduct(productPublicId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            // Do not reveal the existence of unapproved/draft listings to the public.
            throw new ApiException(ErrorCode.NOT_FOUND, "Product not found.");
        }
        product.getVariants().size(); // force-init lazy collection in-tx before it is mapped in the controller
        return product;
    }

    /** Lists a seller's own products (any status). Caller must own the seller. */
    @Transactional(readOnly = true)
    public java.util.List<Product> listSellerProducts(String sellerPublicId, Long currentUserId) {
        Seller seller = sellerService.requireOwnership(sellerPublicId, currentUserId);
        java.util.List<Product> products = productRepository.findBySellerOrderByCreatedAtDesc(seller);
        products.forEach(p -> p.getVariants().size()); // init lazy collections in-tx for controller mapping
        return products;
    }

    private Product requireProduct(String productPublicId) {
        UUID id;
        try {
            id = UUID.fromString(productPublicId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Product not found.");
        }
        return productRepository.findByPublicId(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product not found."));
    }
}
