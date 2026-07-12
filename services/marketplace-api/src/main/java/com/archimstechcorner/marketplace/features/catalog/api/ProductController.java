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
        Product product = productService.createProduct(
                request.sellerPublicId(),
                request.categoryId(),
                request.brandId(),
                request.name(),
                request.slug(),
                request.description(),
                principal.userId()
        );

        if (request.variants() != null) {
            for (VariantRequest variantReq : request.variants()) {
                productService.addVariant(
                        product.getPublicId().toString(),
                        variantReq.sku(),
                        variantReq.name(),
                        variantReq.regularPrice(),
                        variantReq.stockQuantity(),
                        variantReq.attributes()
                );
            }
        }

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

    /** Seller Center: list the caller's own products (any status) for a seller they belong to. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProductResponse> listSellerProducts(@RequestParam String sellerPublicId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return productService.listSellerProducts(sellerPublicId, principal.userId()).stream()
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

        return new ProductResponse(
                product.getPublicId().toString(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus().name(),
                variants
        );
    }

    public record CreateProductRequest(
            @NotBlank String sellerPublicId,
            @NotNull Long categoryId,
            Long brandId,
            @NotBlank String name,
            @NotBlank String slug,
            String description,
            List<VariantRequest> variants
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
            List<VariantResponse> variants
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
}
