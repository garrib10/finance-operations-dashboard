package dev.portfolio.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_category_user_name",
                        columnNames = {"user_id", "name"}
                )
        }
)
public class Category extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "budget_enabled", nullable = false)
    private boolean budgetEnabled;

    protected Category() {
    }

    public Category(
            User user,
            String name,
            boolean budgetEnabled
    ) {
        this.user = user;
        this.name = name;
        this.budgetEnabled = budgetEnabled;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public boolean isBudgetEnabled() {
        return budgetEnabled;
    }

    public void update(
            String name,
            boolean budgetEnabled
    ) {
        this.name = name;
        this.budgetEnabled = budgetEnabled;
    }
}