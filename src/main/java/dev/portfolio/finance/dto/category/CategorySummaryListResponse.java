package dev.portfolio.finance.dto.category;

import java.util.List;

/**
 * Usage of every category the user owns, for the server's reporting month (so the page
 * labels the same month the figures describe). Rows are ordered by name, then ID.
 */
public record CategorySummaryListResponse(
        int month,
        int year,
        List<CategorySummaryResponse> categories
) {
}
