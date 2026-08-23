package dev.portfolio.finance.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.service.CategoryService;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(
            CategoryService categoryService
    ) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            Authentication authentication,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse response =
                categoryService.createCategory(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories(
            Authentication authentication
    ) {
        List<CategoryResponse> response =
                categoryService.getAllCategories(
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        CategoryResponse response =
                categoryService.getCategoryById(
                        authentication.getName(),
                        id
                );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        CategoryResponse response =
                categoryService.updateCategory(
                        authentication.getName(),
                        id,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            Authentication authentication,
            @PathVariable Long id
    ) {
        categoryService.deleteCategory(
                authentication.getName(),
                id
        );

        return ResponseEntity.noContent().build();
    }
}