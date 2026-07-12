package com.archimstechcorner.marketplace.features.catalog.api;

import com.archimstechcorner.marketplace.features.catalog.application.CategoryService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
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
@RequestMapping("/api/v1/catalog/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> getCategoryTree() {
        return categoryService.getCategoryTree().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('category.manage')")
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryService.createCategory(
                request.parentId(),
                request.name(),
                request.slug(),
                request.description()
        );
        return mapToResponse(category);
    }

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getSubCategories().stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
        );
    }

    public record CreateCategoryRequest(
            Long parentId,
            @NotBlank String name,
            @NotBlank String slug,
            String description
    ) {}

    public record CategoryResponse(
            Long id,
            String name,
            String slug,
            List<CategoryResponse> subCategories
    ) {}
}
