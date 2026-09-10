import { useEffect, useState } from "react";
import type { SubmitEvent as ReactSubmitEvent } from "react";

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { ApiError } from "../services/api";

import {
  createBudget,
  deleteBudget,
  getBudgetAnalytics,
  getBudgets,
  updateBudget,
} from "../services/budgetService";

import { getCategories } from "../services/categoryService";

import type {
  BudgetAnalyticsResponse,
  BudgetResponse,
  BudgetStatus,
  CreateBudgetRequest,
  UpdateBudgetRequest,
} from "../types/budget";

import type { CategoryResponse } from "../types/category";

import { formatCurrency } from "../utils/formatters";

interface BudgetFormState {
  categoryId: string;
  monthlyLimit: string;
  month: string;
  year: string;
}

interface BudgetChartData {
  category: string;
  limit: number;
  spent: number;
  utilization: number;
}

const monthOptions = Array.from({ length: 12 }, (_, index) => ({
  value: index + 1,
  label: new Intl.DateTimeFormat("en-US", {
    month: "long",
  }).format(new Date(2026, index, 1)),
}));

const currentYear = new Date().getFullYear();

const yearOptions = Array.from(
  { length: 7 },
  (_, index) => currentYear - 3 + index,
);

function getInitialBudgetForm(): BudgetFormState {
  const today = new Date();

  return {
    categoryId: "",
    monthlyLimit: "",
    month: String(today.getMonth() + 1),
    year: String(today.getFullYear()),
  };
}

function formatBudgetMonth(month: number, year: number): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "long",
    year: "numeric",
  }).format(new Date(year, month - 1, 1));
}

function formatBudgetStatus(status: BudgetStatus): string {
  switch (status) {
    case "ON_TRACK":
      return "On Track";

    case "CAUTION":
      return "Caution";

    case "WARNING":
      return "Warning";

    case "OVER_BUDGET":
      return "Over Budget";
  }
}

function BudgetPage() {
  const [budgets, setBudgets] = useState<BudgetResponse[]>([]);

  const [analytics, setAnalytics] = useState<
    Record<number, BudgetAnalyticsResponse>
  >({});

  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [form, setForm] = useState<BudgetFormState>(getInitialBudgetForm);
  const [editingBudgetId, setEditingBudgetId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [formErrorMessage, setFormErrorMessage] = useState("");
  const [validationErrors, setValidationErrors] = useState<
    Record<string, string>
  >({});

  const today = new Date();
  const [viewMonth, setViewMonth] = useState(String(today.getMonth() + 1));
  const [viewYear, setViewYear] = useState(String(today.getFullYear()));

  const filteredBudgets = budgets.filter(
    (budget) =>
      budget.month === Number(viewMonth) && budget.year === Number(viewYear),
  );

  const budgetChartData = filteredBudgets
    .map((budget) => {
      const budgetAnalytics = analytics[budget.id];

      if (!budgetAnalytics) {
        return null;
      }

      return {
        category: budget.categoryName,
        limit: budgetAnalytics.monthlyLimit,
        spent: budgetAnalytics.amountSpent,
        utilization: budgetAnalytics.percentageUsed,
      };
    })
    .filter((item): item is BudgetChartData => item !== null);

  async function loadBudgetData(): Promise<void> {
    const budgetResponse = await getBudgets();

    setBudgets(budgetResponse);

    if (budgetResponse.length === 0) {
      setAnalytics({});
      return;
    }

    const analyticsResponse = await Promise.all(
      budgetResponse.map((budget) => getBudgetAnalytics(budget.id)),
    );

    const analyticsByBudgetId = analyticsResponse.reduce<
      Record<number, BudgetAnalyticsResponse>
    >((result, budgetAnalytics) => {
      result[budgetAnalytics.budgetId] = budgetAnalytics;

      return result;
    }, {});

    setAnalytics(analyticsByBudgetId);
  }

  useEffect(() => {
    async function loadPageData(): Promise<void> {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const [budgetResponse, categoryResponse] = await Promise.all([
          getBudgets(),
          getCategories(),
        ]);

        setBudgets(budgetResponse);

        setCategories(categoryResponse);

        if (budgetResponse.length === 0) {
          setAnalytics({});
          return;
        }

        const analyticsResponse = await Promise.all(
          budgetResponse.map((budget) => getBudgetAnalytics(budget.id)),
        );

        const analyticsByBudgetId = analyticsResponse.reduce<
          Record<number, BudgetAnalyticsResponse>
        >((result, budgetAnalytics) => {
          result[budgetAnalytics.budgetId] = budgetAnalytics;

          return result;
        }, {});

        setAnalytics(analyticsByBudgetId);
      } catch (error) {
        if (error instanceof ApiError) {
          setErrorMessage(error.message);
        } else {
          setErrorMessage(
            "Unable to load budget information. Please try again.",
          );
        }
      } finally {
        setIsLoading(false);
      }
    }

    void loadPageData();
  }, []);

  function resetForm(): void {
    setForm(getInitialBudgetForm());

    setEditingBudgetId(null);

    setFormErrorMessage("");

    setValidationErrors({});
  }

  function handleEditBudget(budget: BudgetResponse): void {
    setEditingBudgetId(budget.id);

    setForm({
      categoryId: String(budget.categoryId),
      monthlyLimit: String(budget.monthlyLimit),
      month: String(budget.month),
      year: String(budget.year),
    });

    setFormErrorMessage("");

    setValidationErrors({});

    window.scrollTo({
      top: 0,
      behavior: "smooth",
    });
  }

  async function handleSubmit(
    event: ReactSubmitEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    setFormErrorMessage("");

    setValidationErrors({});

    if (!form.categoryId) {
      setFormErrorMessage("Please select a category.");

      return;
    }

    if (!form.monthlyLimit || Number(form.monthlyLimit) <= 0) {
      setFormErrorMessage("Monthly limit must be greater than 0.");

      return;
    }

    const request: CreateBudgetRequest | UpdateBudgetRequest = {
      categoryId: Number(form.categoryId),
      monthlyLimit: Number(form.monthlyLimit),
      month: Number(form.month),
      year: Number(form.year),
    };

    try {
      setIsSubmitting(true);

      if (editingBudgetId !== null) {
        await updateBudget(editingBudgetId, request);
      } else {
        await createBudget(request);
      }

      /*
       * Move the displayed budget period
       * to the month/year that was just
       * created or updated.
       */
      setViewMonth(String(request.month));

      setViewYear(String(request.year));

      resetForm();

      await loadBudgetData();
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.validationErrors) {
          setValidationErrors(error.validationErrors);
        } else {
          setFormErrorMessage(error.message);
        }
      } else {
        setFormErrorMessage(
          editingBudgetId !== null
            ? "Unable to update the budget. Please try again."
            : "Unable to create the budget. Please try again.",
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDeleteBudget(budget: BudgetResponse): Promise<void> {
    const confirmed = window.confirm(
      `Delete the ${budget.categoryName} budget?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      setErrorMessage("");

      await deleteBudget(budget.id);

      if (editingBudgetId === budget.id) {
        resetForm();
      }

      await loadBudgetData();
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to delete the budget. Please try again.");
      }
    }
  }

  if (isLoading) {
    return (
      <section>
        <h1>Budgets</h1>

        <p>Loading budgets...</p>
      </section>
    );
  }

  return (
    <section>
      <div>
        <h1>Budgets</h1>

        <p>Manage monthly spending limits and track budgets by category.</p>
      </div>

      {errorMessage && <p role="alert">{errorMessage}</p>}

      <section>
        <h2>{editingBudgetId !== null ? "Edit Budget" : "Create Budget"}</h2>

        <form className="budget-form" onSubmit={handleSubmit}>
          <label className="form-field">
            <span>Category</span>

            <select
              value={form.categoryId}
              onChange={(event) =>
                setForm({
                  ...form,
                  categoryId: event.target.value,
                })
              }
            >
              <option value="">Select category</option>

              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>

            {validationErrors.categoryId && (
              <span className="form-error">{validationErrors.categoryId}</span>
            )}
          </label>

          <label className="form-field">
            <span>Monthly Limit</span>

            <input
              type="number"
              min="0.01"
              step="0.01"
              value={form.monthlyLimit}
              onChange={(event) =>
                setForm({
                  ...form,
                  monthlyLimit: event.target.value,
                })
              }
              placeholder="500.00"
            />

            {validationErrors.monthlyLimit && (
              <span className="form-error">
                {validationErrors.monthlyLimit}
              </span>
            )}
          </label>

          <label className="form-field">
            <span>Month</span>

            <select
              value={form.month}
              onChange={(event) =>
                setForm({
                  ...form,
                  month: event.target.value,
                })
              }
            >
              {monthOptions.map((month) => (
                <option key={month.value} value={month.value}>
                  {month.label}
                </option>
              ))}
            </select>

            {validationErrors.month && (
              <span className="form-error">{validationErrors.month}</span>
            )}
          </label>

          <label className="form-field">
            <span>Year</span>

            <input
              type="number"
              min="2000"
              value={form.year}
              onChange={(event) =>
                setForm({
                  ...form,
                  year: event.target.value,
                })
              }
            />

            {validationErrors.year && (
              <span className="form-error">{validationErrors.year}</span>
            )}
          </label>

          <div>
            <button type="submit" className="button" disabled={isSubmitting}>
              {isSubmitting
                ? "Saving..."
                : editingBudgetId !== null
                  ? "Update Budget"
                  : "Create Budget"}
            </button>

            {editingBudgetId !== null && (
              <button
                type="button"
                className="button button--secondary"
                onClick={resetForm}
                disabled={isSubmitting}
              >
                Cancel Edit
              </button>
            )}
          </div>
        </form>

        {formErrorMessage && (
          <p role="alert" className="form-error">
            {formErrorMessage}
          </p>
        )}
      </section>

      <section className="budget-filter-section">
        <div>
          <h2>Budget Period</h2>

          <p>View budgets for a specific month.</p>
        </div>

        <div className="budget-period-filter">
          <label className="form-field">
            <span>Month</span>

            <select
              value={viewMonth}
              onChange={(event) => setViewMonth(event.target.value)}
            >
              {monthOptions.map((month) => (
                <option key={month.value} value={month.value}>
                  {month.label}
                </option>
              ))}
            </select>
          </label>

          <label className="form-field">
            <span>Year</span>

            <select
              value={viewYear}
              onChange={(event) => setViewYear(event.target.value)}
            >
              {yearOptions.map((year) => (
                <option key={year} value={year}>
                  {year}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      {budgetChartData.length > 0 && (
        <div className="budget-charts-grid">
          <section className="budget-chart-section">
            <div>
              <h2>Budget vs. Spending</h2>

              <p>Compare monthly limits with actual spending by category.</p>
            </div>

            <div className="budget-chart">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart
                  data={budgetChartData}
                  margin={{
                    top: 20,
                    right: 20,
                    left: 10,
                    bottom: 10,
                  }}
                >
                  <CartesianGrid strokeDasharray="3 3" />

                  <XAxis dataKey="category" />

                  <YAxis tickFormatter={(value) => `$${value}`} />

                  <Tooltip
                    formatter={(value) => formatCurrency(Number(value))}
                  />

                  <Bar
                    dataKey="limit"
                    name="Monthly Limit"
                    fill="var(--color-primary)"
                    radius={[6, 6, 0, 0]}
                  />

                  <Bar
                    dataKey="spent"
                    name="Amount Spent"
                    fill="var(--color-text-muted)"
                    radius={[6, 6, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          <section className="budget-chart-section">
            <div>
              <h2>Budget Utilization</h2>

              <p>Percentage of each monthly budget currently used.</p>
            </div>

            <div className="budget-chart">
              <ResponsiveContainer width="100%" height={280}>
                <BarChart
                  data={budgetChartData}
                  margin={{
                    top: 20,
                    right: 20,
                    left: 10,
                    bottom: 10,
                  }}
                >
                  <CartesianGrid strokeDasharray="3 3" />

                  <XAxis dataKey="category" />

                  <YAxis tickFormatter={(value) => `${value}%`} />

                  <Tooltip
                    formatter={(value) => `${Number(value).toFixed(1)}%`}
                  />

                  <Bar
                    dataKey="utilization"
                    name="Used"
                    fill="var(--color-primary)"
                    radius={[6, 6, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>
        </div>
      )}

      <section>
        <h2>Monthly Budgets</h2>

        {filteredBudgets.length === 0 ? (
          <p className="empty-state">
            No budgets found for{" "}
            {formatBudgetMonth(Number(viewMonth), Number(viewYear))}.
          </p>
        ) : (
          <div className="budget-grid">
            {filteredBudgets.map((budget) => {
              const budgetAnalytics = analytics[budget.id];

              return (
                <article key={budget.id} className="budget-card">
                  <div className="budget-card__header">
                    <div>
                      <h3>{budget.categoryName}</h3>

                      <span>
                        {formatBudgetMonth(budget.month, budget.year)}
                      </span>
                    </div>

                    {budgetAnalytics && (
                      <span
                        className={`budget-status budget-status--${budgetAnalytics.status.toLowerCase()}`}
                      >
                        {formatBudgetStatus(budgetAnalytics.status)}
                      </span>
                    )}
                  </div>

                  <div className="budget-card__content">
                    <div>
                      <span>Monthly Limit:</span>

                      <strong>{formatCurrency(budget.monthlyLimit)}</strong>
                    </div>

                    {budgetAnalytics && (
                      <>
                        <div>
                          <span>Amount Spent:</span>

                          <strong>
                            {formatCurrency(budgetAnalytics.amountSpent)}
                          </strong>
                        </div>

                        <div>
                          <span>Remaining:</span>

                          <strong>
                            {formatCurrency(budgetAnalytics.amountRemaining)}
                          </strong>
                        </div>

                        <div>
                          <span>Used:</span>

                          <strong>
                            {budgetAnalytics.percentageUsed.toFixed(1)}%
                          </strong>
                        </div>
                      </>
                    )}
                  </div>

                  <div className="budget-card__actions">
                    <button
                      type="button"
                      className="button button--secondary"
                      onClick={() => handleEditBudget(budget)}
                    >
                      Edit
                    </button>

                    <button
                      type="button"
                      className="button button--secondary"
                      onClick={() => {
                        void handleDeleteBudget(budget);
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </section>
  );
}

export default BudgetPage;
