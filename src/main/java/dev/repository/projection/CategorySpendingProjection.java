package dev.portfolio.finance.repository.projection;

import java.math.BigDecimal;

public interface CategorySpendingProjection {

    Long getCategoryId();

    String getCategoryName();

    BigDecimal getAmountSpent();
}