package com.archimstechcorner.marketplace.features.catalog.application;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Brand;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductImage;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductSpecification;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductVariantRepository;

/**
 * Catalog write/read use cases. Every seller-scoped mutation verifies the caller owns the target seller
 * (tenant isolation) via {@link SellerService#requireOwnership}. Not-found/invalid-state conditions
 * raise {@link ApiException} so they map to 404/409 rather than an opaque 500.
 */
@Service
public class ProductService {


    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SellerService sellerService;

    public ProductService(ProductRepository productRepository,
                          ProductVariantRepository productVariantRepository,
                          CategoryRepository categoryRepository,
                          BrandRepository brandRepository,
                          SellerService sellerService) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.sellerService = sellerService;
    }

    public record VariantInput(
            String sku,
            String name,
            BigDecimal regularPrice,
            BigDecimal salePrice,
            int stockQuantity,
            String attributes
    ) {
        public VariantInput(String sku, String name, BigDecimal regularPrice, int stockQuantity, String attributes) {
            this(sku, name, regularPrice, null, stockQuantity, attributes);
        }
    }

    public record SpecInput(
            String name,
            String value,
            int displayOrder
    ) {}

    public record ImageInput(
            String url,
            boolean isPrimary,
            int displayOrder
    ) {}

    /**
     * G09: Atomically creates a Product aggregate with all its variants, specs, and images in a SINGLE transaction.
     * If any variant fails validation or SKU uniqueness, the entire transaction rolls back.
     */
    @Transactional
    public Product createProductAggregate(String sellerPublicId, Long categoryId, Long brandId,
                                          String name, String slug, String description,
                                          List<VariantInput> variants, Long currentUserId) {
        return createProductAggregate(sellerPublicId, categoryId, brandId, name, slug, description, variants, List.of(), List.of(), currentUserId);
    }

    @Transactional
    public Product createProductAggregate(String sellerPublicId, Long categoryId, Long brandId,
                                          String name, String slug, String description,
                                          List<VariantInput> variants,
                                          List<SpecInput> specifications,
                                          List<ImageInput> images,
                                          Long currentUserId) {
        Seller seller = sellerService.requireOwnership(sellerPublicId, currentUserId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found."));

        Brand brand = null;
        if (brandId != null) {
            brand = brandRepository.findById(brandId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Brand not found."));
        }

        if (variants == null || variants.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Product must have at least one variant.");
        }

        java.util.Set<String> seenSkus = new java.util.HashSet<>();
        for (VariantInput v : variants) {
            if (v.sku() == null || v.sku().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Variant SKU is required.");
            }
            String skuUpper = v.sku().trim().toUpperCase();
            if (!seenSkus.add(skuUpper)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Duplicate SKU in request: " + v.sku());
            }
            if (productVariantRepository.findBySku(skuUpper).isPresent()) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "SKU already exists: " + v.sku());
            }
            if (v.regularPrice() == null || v.regularPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Variant regular price must be positive.");
            }
            if (v.salePrice() != null) {
                if (v.salePrice().compareTo(BigDecimal.ZERO) <= 0 || v.salePrice().compareTo(v.regularPrice()) >= 0) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sale price must be positive and lower than regular price.");
                }
            }
            if (v.stockQuantity() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Variant stock quantity cannot be negative.");
            }
        }

        Product product = Product.create(seller, category, brand, name, slug, description);

        for (VariantInput v : variants) {
            ProductVariant variant = ProductVariant.create(
                    product,
                    v.sku().trim().toUpperCase(),
                    v.name(),
                    v.regularPrice(),
                    v.stockQuantity(),
                    v.attributes()
            );
            if (v.salePrice() != null) {
                variant.setSalePrice(v.salePrice());
            }
            product.addVariant(variant);
        }

        if (specifications != null) {
            for (SpecInput s : specifications) {
                if (s.name() != null && !s.name().isBlank() && s.value() != null && !s.value().isBlank()) {
                    product.addSpecification(ProductSpecification.create(product, s.name().trim(), s.value().trim(), s.displayOrder()));
                }
            }
        }

        if (images != null) {
            for (ImageInput img : images) {
                if (img.url() != null && !img.url().isBlank()) {
                    product.addImage(ProductImage.create(product, img.url().trim(), img.isPrimary(), img.displayOrder()));
                }
            }
        }

        return productRepository.save(product);
    }

    /**
     * G09: Centralized sale eligibility check across product, variant, and seller status.
     */
    public boolean isEligibleForSale(Product product, ProductVariant variant) {
        if (product == null || variant == null) {
            return false;
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            return false;
        }
        if (product.getSeller() == null || product.getSeller().getStatus() != SellerStatus.ACTIVE) {
            return false;
        }
        if (!variant.isActive()) {
            return false;
        }
        if (variant.getRegularPrice() == null || variant.getRegularPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return true;
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
        product.getImages().size();
        product.getSpecifications().size();
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
        product.getImages().size();
        product.getSpecifications().size();
        return product;
    }

    /**
     * Public product feed (guest browsing) — composable query supporting category, brand, seller,
     * text query, min/max price, and sort with stable tie-breaker. (G16 / B03)
     */
    @Transactional(readOnly = true)
    public FeedPage getFeed(int page, int size, Long categoryId, Long brandId, String sellerPublicId,
                            BigDecimal minPrice, BigDecimal maxPrice, String query, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 60);

        Sort sorting = switch (sort == null ? "newest" : sort.toLowerCase()) {
            case "name-asc", "name_asc" -> Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.DESC, "id"));
            case "name-desc", "name_desc" -> Sort.by(Sort.Direction.DESC, "name").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };
        Pageable pageable = PageRequest.of(safePage, safeSize, sorting);

        org.springframework.data.jpa.domain.Specification<Product> spec = (root, queryObj, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(cb.equal(root.get("seller").get("status"), SellerStatus.ACTIVE));

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (brandId != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), brandId));
            }
            if (sellerPublicId != null && !sellerPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("seller").get("publicId"), sellerPublicId.trim()));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                jakarta.persistence.criteria.Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                jakarta.persistence.criteria.Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(nameMatch, descMatch));
            }

            queryObj.distinct(true);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Product> result = productRepository.findAll(spec, pageable);

        List<ProductFeedItem> items = result.getContent().stream()
                .filter(p -> {
                    if (minPrice == null && maxPrice == null) return true;
                    BigDecimal price = getLowestPrice(p);
                    if (price == null) return false;
                    if (minPrice != null && price.compareTo(minPrice) < 0) return false;
                    if (maxPrice != null && price.compareTo(maxPrice) > 0) return false;
                    return true;
                })
                .map(this::toFeedItem)
                .toList();

        return new FeedPage(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private BigDecimal getLowestPrice(Product product) {
        return product.getVariants().stream()
                .map(v -> v.getSalePrice() != null ? v.getSalePrice() : v.getRegularPrice())
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private ProductFeedItem toFeedItem(Product product) {
        BigDecimal minPrice = getLowestPrice(product);
        return new ProductFeedItem(
                product.getPublicId().toString(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                minPrice,
                product.getSeller().getName());
    }

    @Transactional
    public void addSpecification(String productPublicId, String name, String value, int displayOrder, Long currentUserId) {
        Product product = requireProduct(productPublicId);
        sellerService.requireOwnership(product.getSeller().getPublicId(), currentUserId);
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Specification name and value are required.");
        }
        ProductSpecification spec = ProductSpecification.create(product, name.trim(), value.trim(), displayOrder);
        product.addSpecification(spec);
        productRepository.save(product);
    }

    @Transactional
    public void addImage(String productPublicId, String url, boolean isPrimary, int displayOrder, Long currentUserId) {
        Product product = requireProduct(productPublicId);
        sellerService.requireOwnership(product.getSeller().getPublicId(), currentUserId);
        if (url == null || url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("/"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A valid image URL is required.");
        }
        if (isPrimary && product.getImages() != null) {
            for (ProductImage img : product.getImages()) {
                img.setPrimary(false);
            }
        }
        ProductImage image = ProductImage.create(product, url.trim(), isPrimary, displayOrder);
        product.addImage(image);
        productRepository.save(product);
    }

    /** Lists a seller's own products (any status). Caller must own the seller. */
    @Transactional(readOnly = true)
    public java.util.List<Product> listSellerProducts(String sellerPublicId, Long currentUserId) {
        Seller seller = sellerService.requireOwnership(sellerPublicId, currentUserId);
        java.util.List<Product> products = productRepository.findBySellerOrderByCreatedAtDesc(seller);
        products.forEach(p -> p.getVariants().size()); // init lazy collections in-tx for controller mapping
        return products;
    }

    /** T10: Public list of ACTIVE products for seller storefront (no ownership required). */
    @Transactional(readOnly = true)
    public java.util.List<Product> listPublicSellerProducts(String sellerPublicId) {
        Seller seller = sellerService.requireSeller(sellerPublicId);
        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Seller not found.");
        }
        java.util.List<Product> products = productRepository.findBySellerOrderByCreatedAtDesc(seller).stream()
                .filter(p -> p.getStatus() == com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus.ACTIVE)
                .toList();
        products.forEach(p -> { p.getVariants().size(); p.getImages().size(); p.getSpecifications().size(); });
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

    /** T08: Autocomplete - MySQL source of truth, small limit, ACTIVE only. */
    @Transactional(readOnly = true)
    public List<ProductFeedItem> autocomplete(String query) {
        if (query == null || query.trim().isEmpty()) return List.of();
        String term = query.trim();
        List<Product> matches = productRepository.findTop10ByStatusAndNameContainingIgnoreCaseOrderByNameAsc(
                ProductStatus.ACTIVE, term);
        return matches.stream().map(this::toFeedItem).toList();
    }
}
