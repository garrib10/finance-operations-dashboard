package dev.portfolio.finance.repository.projection;

import java.math.BigDecimal;

public interface CategorySpendingProjection {

    Long getCategoryId();

    String getCategoryName();

    /** Raw stored key; map with CategoryIcon.fromStoredKey before returning it. */
    String getCategoryIconKey();

    BigDecimal getAmountSpent();
}