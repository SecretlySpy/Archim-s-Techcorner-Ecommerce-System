package com.archimstechcorner.marketplace.features.catalog.application;

import com.archimstechcorner.marketplace.features.catalog.domain.model.Category;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.CategoryRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> getCategoryTree() {
        List<Category> roots = categoryRepository.findByParentIsNull();
        // Force-initialize the whole subtree in-tx so the controller can map it (open-in-view is off).
        roots.forEach(this::initSubtree);
        return roots;
    }

    private void initSubtree(Category category) {
        category.getSubCategories().forEach(this::initSubtree);
    }

    @Transactional
    public Category createCategory(Long parentId, String name, String slug, String description) {
        Category parent = null;
        if (parentId != null) {
            parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Parent category not found."));
        }

        Category category = Category.create(parent, name, slug, description);
        return categoryRepository.save(category);
    }
}
