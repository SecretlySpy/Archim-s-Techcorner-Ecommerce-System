package com.archimstechcorner.marketplace.features.catalog.api;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Brand;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.BrandRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/catalog/brands")
public class BrandController {

    private final BrandRepository brandRepository;

    public BrandController(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @GetMapping
    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream()
                .map(brand -> new BrandResponse(brand.getId(), brand.getName(), brand.getSlug(), brand.getLogoUrl()))
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('brand.create')")
    public BrandResponse createBrand(@Valid @RequestBody CreateBrandRequest request) {
        Brand brand = Brand.create(request.name(), request.slug(), request.logoUrl(), request.description());
        brand = brandRepository.save(brand);
        return new BrandResponse(brand.getId(), brand.getName(), brand.getSlug(), brand.getLogoUrl());
    }

    public record CreateBrandRequest(
            @NotBlank String name,
            @NotBlank String slug,
            String logoUrl,
            String description
    ) {}

    public record BrandResponse(
            Long id,
            String name,
            String slug,
            String logoUrl
    ) {}
}
