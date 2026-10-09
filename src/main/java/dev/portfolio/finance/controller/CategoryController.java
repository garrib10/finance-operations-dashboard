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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.dto.category.CategoryResponse;
import dev.portfolio.finance.dto.category.CategorySummaryListResponse;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.service.CategoryService;
import dev.portfolio.finance.service.CategorySummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategorySummaryService categorySummaryService;

    public CategoryController(
            CategoryService categoryService,
            CategorySummaryService categorySummaryService
    ) {
        this.categoryService = categoryService;
        this.categorySummaryService = categorySummaryService;
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

    /**
     * Usage of the signed-in user's categories for the server's current month, or for an
     * earlier month when both {@code month} and {@code year} are given (validated in the
     * service; malformed values are reported by name in {@code CategoryExceptionHandler}).
     */
    @GetMapping("/summary")
    public ResponseEntity<CategorySummaryListResponse> getCategorySummary(
            Authentication authentication,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year
    ) {
        return ResponseEntity.ok(
                categorySummaryService.getSummary(authentication.getName(), month, year)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            Authentication authentication,
            @PathVariable @Positive Long id
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
            @PathVariable @Positive Long id,
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
            @PathVariable @Positive Long id
    ) {
        categoryService.deleteCategory(
                authentication.getName(),
                id
        );

        return ResponseEntity.noContent().build();
    }
}