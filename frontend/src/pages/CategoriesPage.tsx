import { CategoryCard } from "../components/CategoryCard";
import { CategorySummaryStrip } from "../components/CategorySummaryStrip";
import { useAuth } from "../context/AuthContext";
import { useCategorySummary } from "../hooks/useCategorySummary";
import { formatReportingMonth, monthSpendingTotal } from "../utils/categorySummary";

function CategoriesPage() {
  const { user } = useAuth();
  const { summary, status, error, reload } = useCategorySummary();
  const dateFormat = user?.preferences?.dateFormat ?? "MEDIUM";

  const monthLabel = summary ? formatReportingMonth(summary.month, summary.year) : "";
  const monthName = monthLabel.split(" ")[0];

  return (
    <section className="categories-page" aria-labelledby="categories-heading">
      <div className="page-header">
        <h1 id="categories-heading">Categories</h1>
        <p>
          How each category is used
          {summary ? ` in ${monthLabel}` : " this month"}: spending, budgets, and activity.
        </p>
      </div>

      {status === "error" && (
        <div className="form-error categories-page__error" role="alert">
          <span>{error}</span>{" "}
          <button type="button" className="button button--secondary button--small" onClick={() => void reload()}>
            Try again
          </button>
        </div>
      )}

      {!summary && status === "loading" && <p role="status">Loading categories…</p>}

      {summary && (
        summary.categories.length === 0 ? (
          <p className="empty-state">You don’t have any categories yet.</p>
        ) : (
          <>
            <CategorySummaryStrip rows={summary.categories} monthLabel={monthLabel} />

            <div className="categories-page__list-header">
              <h2>All categories</h2>
              <p>{summary.categories.length} categories, A–Z</p>
            </div>

            <ul className="category-grid">
              {summary.categories.map((category) => (
                <li key={category.id}>
                  <CategoryCard
                    category={category}
                    monthTotal={monthSpendingTotal(summary.categories)}
                    monthName={monthName}
                    dateFormat={dateFormat}
                  />
                </li>
              ))}
            </ul>
          </>
        )
      )}
    </section>
  );
}

export default CategoriesPage;
