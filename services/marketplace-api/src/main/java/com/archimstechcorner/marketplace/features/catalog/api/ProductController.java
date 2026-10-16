package com.archimstechcorner.marketplace.features.catalog.api;

import com.archimstechcorner.marketplace.features.catalog.application.ProductService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductStatus;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()") // ownership (caller must belong to the seller) is enforced in the service
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        List<ProductService.VariantInput> variants = (request.variants() == null) ? List.of() :
                request.variants().stream()
                        .map(v -> new ProductService.VariantInput(
                                v.sku(),
                                v.name(),
                                v.regularPrice(),
                                null,
                                v.stockQuantity(),
                                v.attributes()
                        ))
                        .toList();

        List<ProductService.SpecInput> specs = (request.specifications() == null) ? List.of() :
                request.specifications().stream()
                        .map(s -> new ProductService.SpecInput(s.name(), s.value(), s.displayOrder()))
                        .toList();

        List<ProductService.ImageInput> images = (request.images() == null) ? List.of() :
                request.images().stream()
                        .map(img -> new ProductService.ImageInput(img.url(), img.isPrimary(), img.displayOrder()))
                        .toList();

        Product product = productService.createProductAggregate(
                request.sellerPublicId(),
                request.categoryId(),
                request.brandId(),
                request.name(),
                request.slug(),
                request.description(),
                variants,
                specs,
                images,
                principal.userId()
        );

        return mapToResponse(productService.getProduct(product.getPublicId().toString()));
    }

    @PutMapping("/{publicId}/status")
    @PreAuthorize("hasAuthority('product.moderate')")
    public ProductResponse moderateProduct(@PathVariable String publicId, @Valid @RequestBody ModerateProductRequest request) {
        productService.moderateProduct(publicId, request.status());
        return mapToResponse(productService.getProduct(publicId));
    }

    @PostMapping("/{publicId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ProductResponse submitForApproval(@PathVariable String publicId,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        productService.submitForApproval(publicId, principal.userId());
        return mapToResponse(productService.getProduct(publicId));
    }

    /**
     * PUBLIC product feed (guest browsing) — the main shop stream. No authentication required;
     * returns only published (ACTIVE) products. Supports pagination, category filter, and search.
     * Declared as a literal path so it takes precedence over {@code /{publicId}}.
     */
    @GetMapping("/feed")
    public com.archimstechcorner.marketplace.features.catalog.application.FeedPage feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String sellerPublicId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "newest") String sort) {
        return productService.getFeed(page, size, categoryId, brandId, sellerPublicId, minPrice, maxPrice, q, sort);
    }

    @PostMapping("/{publicId}/specifications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public void addSpecification(@PathVariable String publicId,
                                 @Valid @RequestBody AddSpecificationRequest req,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        productService.addSpecification(publicId, req.name(), req.value(), req.displayOrder(), principal.userId());
    }

    @PostMapping("/{publicId}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public void addImage(@PathVariable String publicId,
                         @Valid @RequestBody AddImageRequest req,
                         @AuthenticationPrincipal AuthenticatedUser principal) {
        productService.addImage(publicId, req.url(), req.isPrimary(), req.displayOrder(), principal.userId());
    }

    /** T08: Autocomplete suggestions (public, ACTIVE, limited). */
    @GetMapping("/autocomplete")
    public List<com.archimstechcorner.marketplace.features.catalog.application.ProductFeedItem> autocomplete(
            @RequestParam(required = false) String q) {
        return productService.autocomplete(q);
    }

    /** Seller Center: list the caller's own products (any status) for a seller they belong to. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> listSellerProducts(@RequestParam String sellerPublicId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return productService.listSellerProducts(sellerPublicId, principal.userId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /** T10: Public storefront catalog for a seller — only ACTIVE products. */
    @GetMapping("/public")
    public List<ProductResponse> publicSellerProducts(@RequestParam String sellerPublicId) {
        return productService.listPublicSellerProducts(sellerPublicId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /** Public product detail — only published (ACTIVE) products are visible to guests. */
    @GetMapping("/{publicId}")
    public ProductResponse getProduct(@PathVariable String publicId) {
        return mapToResponse(productService.getPublishedProduct(publicId));
    }

    private ProductResponse mapToResponse(Product product) {
        List<VariantResponse> variants = product.getVariants().stream()
                .map(v -> new VariantResponse(
                        v.getPublicId().toString(),
                        v.getSku(),
                        v.getName(),
                        v.getRegularPrice(),
                        v.getSalePrice(),
                        v.getStockQuantity(),
                        v.getAttributes()
                ))
                .collect(Collectors.toList());

        List<ProductImageResponse> images = product.getImages() != null ? product.getImages().stream()
                .map(i -> new ProductImageResponse(i.getUrl(), i.isPrimary(), i.getDisplayOrder()))
                .sorted(java.util.Comparator.comparingInt(ProductImageResponse::displayOrder))
                .collect(Collectors.toList()) : List.of();

        List<ProductSpecResponse> specs = product.getSpecifications() != null ? product.getSpecifications().stream()
                .map(s -> new ProductSpecResponse(s.getName(), s.getSpecValue(), s.getDisplayOrder()))
                .sorted(java.util.Comparator.comparingInt(ProductSpecResponse::displayOrder))
                .collect(Collectors.toList()) : List.of();

        return new ProductResponse(
                product.getPublicId().toString(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus().name(),
                variants,
                images,
                specs
        );
    }

    public record CreateProductRequest(
            @NotBlank String sellerPublicId,
            @NotNull Long categoryId,
            Long brandId,
            @NotBlank String name,
            @NotBlank String slug,
            String description,
            List<VariantRequest> variants,
            List<AddSpecificationRequest> specifications,
            List<AddImageRequest> images
    ) {}

    public record VariantRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @NotNull BigDecimal regularPrice,
            int stockQuantity,
            String attributes
    ) {}

    public record ModerateProductRequest(
            @NotNull ProductStatus status
    ) {}

    public record ProductResponse(
            String id,
            String name,
            String slug,
            String description,
            String status,
            List<VariantResponse> variants,
            List<ProductImageResponse> images,
            List<ProductSpecResponse> specifications
    ) {}

    public record VariantResponse(
            String id,
            String sku,
            String name,
            BigDecimal regularPrice,
            BigDecimal salePrice,
            int stockQuantity,
            String attributes
    ) {}

    public record ProductImageResponse(String url, boolean primary, int displayOrder) {}
    public record ProductSpecResponse(String name, String value, int displayOrder) {}

    public record AddSpecificationRequest(
            @NotBlank String name,
            @NotBlank String value,
            int displayOrder
    ) {}

    public record AddImageRequest(
            @NotBlank String url,
            boolean isPrimary,
            int displayOrder
    ) {}
}
