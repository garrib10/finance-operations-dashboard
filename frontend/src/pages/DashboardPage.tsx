import { useAuth } from "../context/AuthContext";
import { useEffect, useState } from "react";
import { ApiError } from "../services/api";
import { getDashboard } from "../services/dashboardService";
import type { DashboardResponse } from "../types/dashboard";
import { formatCurrency, formatDate } from "../utils/formatters";

function formatBudgetStatus(status: string): string {
  switch (status) {
    case "ON_TRACK":
      return "On Track";

    case "CAUTION":
      return "Caution";

    case "WARNING":
      return "Warning";

    case "OVER_BUDGET":
      return "Over Budget";

    default:
      return status;
  }
}

function clampProgressPercentage(percentage: number): number {
  return Math.min(Math.max(percentage, 0), 100);
}

function DashboardPage() {
  const { user } = useAuth();
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);

  const [isLoading, setIsLoading] = useState(true);

  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    async function loadDashboard() {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const response = await getDashboard();

        setDashboard(response);
      } catch (error) {
        if (error instanceof ApiError) {
          setErrorMessage(error.message);
        } else {
          setErrorMessage("Unable to load dashboard data. Please try again.");
        }
      } finally {
        setIsLoading(false);
      }
    }

    loadDashboard();
  }, []);

  if (isLoading) {
    return (
      <section className="page">
        <p>Loading dashboard...</p>
      </section>
    );
  }

  if (errorMessage) {
    return (
      <section className="page">
        <h1>Dashboard</h1>

        <p className="form-error" role="alert">
          {errorMessage}
        </p>
      </section>
    );
  }

  if (!dashboard) {
    return (
      <section className="page">
        <h1>Dashboard</h1>

        <p>No dashboard data is available.</p>
      </section>
    );
  }

  return (
    <section className="page">
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>

          <p>Overview of your financial activity.</p>
        </div>
      </div>

      <section className="dashboard-summary" aria-label="Financial summary">
        <article className="summary-card">
          <span className="summary-card__label">Current Balance</span>
          <strong
            className="summary-card__value"
            data-testid="summary-current-balance"
          >
            {formatCurrency(dashboard.currentBalance)}
          </strong>
        </article>

        <article className="summary-card">
          <span className="summary-card__label">Total Income</span>

          <strong
            className="summary-card__value"
            data-testid="summary-total-income"
          >
            {formatCurrency(dashboard.totalIncome)}
          </strong>
        </article>

        <article className="summary-card">
          <span className="summary-card__label">Total Expenses</span>

          <strong
            className="summary-card__value"
            data-testid="summary-total-expenses"
          >
            {formatCurrency(dashboard.totalExpenses)}
          </strong>
        </article>

        <article className="summary-card">
          <span className="summary-card__label">Monthly Income</span>

          <strong
            className="summary-card__value"
            data-testid="summary-monthly-income"
          >
            {formatCurrency(dashboard.monthlyIncome)}
          </strong>
        </article>

        <article className="summary-card">
          <span className="summary-card__label">Monthly Expenses</span>

          <strong
            className="summary-card__value"
            data-testid="summary-monthly-expenses"
          >
            {formatCurrency(dashboard.monthlyExpenses)}
          </strong>
        </article>
      </section>

      <div className="dashboard-grid">
        <section className="dashboard-panel">
          <div className="dashboard-panel__header">
            <div>
              <h2>Recent Transactions</h2>

              <p>Your five most recent transactions.</p>
            </div>
          </div>

          {dashboard.recentTransactions.length === 0 ? (
            <p className="empty-state">No transactions yet.</p>
          ) : (
            <div className="table-wrapper">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Description</th>
                    <th>Category</th>
                    <th>Type</th>
                    <th>Date</th>
                    <th>Amount</th>
                  </tr>
                </thead>

                <tbody>
                  {dashboard.recentTransactions.map((transaction) => (
                    <tr key={transaction.id}>
                      <td>{transaction.description}</td>

                      <td>{transaction.categoryName}</td>

                      <td>{transaction.type}</td>

                      <td>{formatDate(transaction.transactionDate, user?.preferences?.dateFormat)}</td>

                      <td>{formatCurrency(transaction.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="dashboard-panel">
          <div className="dashboard-panel__header">
            <div>
              <h2>Spending by Category</h2>

              <p>Current-month expense activity.</p>
            </div>
          </div>

          {dashboard.categorySpending.length === 0 ? (
            <p className="empty-state">No category spending this month.</p>
          ) : (
            <div className="category-spending-list">
              {dashboard.categorySpending.map((category) => (
                <div
                  className="category-spending-item"
                  key={category.categoryId}
                >
                  <span>{category.categoryName}</span>

                  <strong>{formatCurrency(category.amountSpent)}</strong>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

      <section className="dashboard-panel">
        <div className="dashboard-panel__header">
          <div>
            <h2>Monthly Budgets</h2>

            <p>Current-month budget performance.</p>
          </div>
        </div>

        {dashboard.budgetSummaries.length === 0 ? (
          <p className="empty-state">
            No budgets have been created for this month.
          </p>
        ) : (
          <div className="budget-summary-grid">
            {dashboard.budgetSummaries.map((budget) => (
              <article className="budget-summary-card" key={budget.budgetId}>
                <div className="budget-summary-card__header">
                  <h3>{budget.categoryName}</h3>

                  <span
                    className={`budget-status budget-status--${budget.status.toLowerCase()}`}
                  >
                    {formatBudgetStatus(budget.status)}
                  </span>
                </div>

                <dl className="budget-details">
                  <div>
                    <dt>Monthly Limit:</dt>

                    <dd>{formatCurrency(budget.monthlyLimit)}</dd>
                  </div>

                  <div>
                    <dt>Spent:</dt>

                    <dd>{formatCurrency(budget.amountSpent)}</dd>
                  </div>

                  <div>
                    <dt>Remaining:</dt>

                    <dd>{formatCurrency(budget.amountRemaining)}</dd>
                  </div>

                  <div>
                    <dt>Used:</dt>

                    <dd>{budget.percentageUsed.toFixed(1)}%</dd>
                  </div>
                </dl>

                <div
                  className="budget-progress"
                  role="progressbar"
                  aria-label={`${budget.categoryName} budget utilization`}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={clampProgressPercentage(budget.percentageUsed)}
                  aria-valuetext={`${budget.percentageUsed.toFixed(1)}% used`}
                >
                  <div
                    className="budget-progress__bar"
                    style={{
                      width: `${clampProgressPercentage(
                        budget.percentageUsed,
                      )}%`,
                    }}
                  />
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}

export default DashboardPage;
