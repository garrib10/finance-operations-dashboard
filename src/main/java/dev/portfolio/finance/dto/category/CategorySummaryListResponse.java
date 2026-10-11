package dev.portfolio.finance.dto.category;

import java.util.List;

/**
 * Usage of every category the user owns for one reporting month: {@code month} and
 * {@code year} are the month the figures describe (the requested one, or the server's
 * current month by default), and every {@code currentMonth*} row field refers to it.
 * {@code serverCurrentMonth} and {@code serverCurrentYear} are always the server's own
 * current month, so a client can tell whether it is looking at history. Rows are ordered
 * by name, then ID.
 */
public record CategorySummaryListResponse(
        int month,
        int year,
        int serverCurrentMonth,
        int serverCurrentYear,
        List<CategorySummaryResponse> categories
) {
}
