import { useEffect, useRef, useState } from "react";
import { CategoryCard } from "../components/CategoryCard";
import { CategoryDeleteConfirm } from "../components/CategoryDeleteConfirm";
import { CategoryDiscoveryToolbar } from "../components/CategoryDiscoveryToolbar";
import { CategoryForm } from "../components/CategoryForm";
import { CategoryRefreshNotice } from "../components/CategoryRefreshNotice";
import { resolveIconKey } from "../components/categoryIconRegistry";
import { CategorySpendingTable } from "../components/CategorySpendingTable";
import { CategorySummaryStrip } from "../components/CategorySummaryStrip";
import { StatusBanner } from "../components/StatusBanner";
import { useAuth } from "../context/AuthContext";
import { useCategories } from "../context/CategoryContext";
import { useCategorySummary } from "../hooks/useCategorySummary";
import { ApiError } from "../services/api";
import { CATEGORY_BUILT_IN, CATEGORY_IN_USE, CATEGORY_NOT_FOUND } from "../services/categoryService";
import type { CategorySummary } from "../types/category";
import { EMPTY_CATEGORY_DRAFT, type CategoryDraft } from "../utils/categoryForm";
import {
  DEFAULT_DISCOVERY,
  discoverCategories,
  matchesFilter,
  matchesSearch,
  type CategoryDiscovery,
} from "../utils/categoryDiscovery";
import { formatReportingMonth, monthSpendingTotal } from "../utils/categorySummary";
import { categoryCardIds } from "../utils/categoryUsage";

const LIST_HEADING_ID = "categories-list-heading";
const CREATE_BUTTON_ID = "categories-create-button";
const SEARCH_ID = "category-search";
const NOT_FOUND_MESSAGE = "This category no longer exists. The list has been refreshed.";

type Workflow =
  | { kind: "none" }
  | { kind: "create" }
  | { kind: "edit"; id: number }
  | { kind: "delete"; id: number };

/**
 * The home for category management. Changes go through CategoryContext (the shared
 * list every category dropdown uses), then the usage summary is reloaded. A successful
 * change is never reported as failed: if a refresh fails, the change stands and a
 * warning offers a retry.
 */
function CategoriesPage() {
  const { user } = useAuth();
  const { summary, status, error, reload } = useCategorySummary();
  const { createCategory, updateCategory, deleteCategory, reload: reloadCategories } = useCategories();
  const dateFormat = user?.preferences?.dateFormat ?? "MEDIUM";

  const [workflow, setWorkflow] = useState<Workflow>({ kind: "none" });
  const [pageError, setPageError] = useState("");
  const [statusMessage, setStatusMessage] = useState("");
  // Search, filter, and sort for the cards only (never the summary or spending table).
  const [discovery, setDiscovery] = useState<CategoryDiscovery>(DEFAULT_DISCOVERY);
  // The category just worked on stays visible until the toolbar next changes, so a rename
  // or a cancel never makes the card (and its focus target) vanish.
  const [recentId, setRecentId] = useState<number | null>(null);
  // Element to focus once the list has re-rendered, with a fallback if it is gone.
  const pendingFocus = useRef<{ id: string; fallback: string } | null>(null);

  const monthLabel = summary ? formatReportingMonth(summary.month, summary.year) : "";
  const monthName = monthLabel.split(" ")[0];
  const rows = summary?.categories ?? [];
  const workflowId = workflow.kind === "edit" || workflow.kind === "delete" ? workflow.id : null;
  const visible = discoverCategories(rows, discovery, [workflowId, recentId]);
  const keptVisible = visible.find((row) => !matchesSearch(row, discovery.query) || !matchesFilter(row, discovery.filter));
  const keptVisibleNote = keptVisible
    && `“${keptVisible.name}” is shown because you’re working on it, though it doesn’t match the current search or filter.`;

  useEffect(() => {
    const target = pendingFocus.current;
    if (!target || status === "loading") return;
    const element = document.getElementById(target.id) ?? document.getElementById(target.fallback);
    if (element) {
      element.focus();
      pendingFocus.current = null;
    }
  });

  function focusAfterRender(id: string, fallback = LIST_HEADING_ID): void {
    pendingFocus.current = { id, fallback };
  }

  function close(focusId: string, categoryId?: number): void {
    setWorkflow({ kind: "none" });
    if (categoryId !== undefined) setRecentId(categoryId);
    focusAfterRender(focusId);
  }

  function changeDiscovery(next: CategoryDiscovery): void {
    setDiscovery(next);
    setRecentId(null);
  }

  function clearDiscovery(): void {
    changeDiscovery(DEFAULT_DISCOVERY);
    focusAfterRender(SEARCH_ID); // The Clear button disappears, so keep focus nearby.
  }

  /** A category changed elsewhere: refresh both lists and explain. */
  function handleGone(message: string): void {
    setWorkflow({ kind: "none" });
    setPageError(message);
    focusAfterRender(LIST_HEADING_ID);
    void reloadCategories();
    void reload();
  }

  async function handleCreate(draft: CategoryDraft): Promise<void> {
    const created = await createCategory({ name: draft.name, budgetEnabled: true, iconKey: draft.iconKey });
    setWorkflow({ kind: "none" });
    setPageError("");
    setStatusMessage(`Created “${created.name}”.`);
    setRecentId(created.id);
    focusAfterRender(categoryCardIds(created.id).heading);
    await reload();
  }

  async function handleSave(category: CategorySummary, draft: CategoryDraft): Promise<void> {
    try {
      const updated = await updateCategory(category.id, {
        name: draft.name,
        budgetEnabled: category.budgetEnabled,
        iconKey: draft.iconKey,
      });
      setWorkflow({ kind: "none" });
      setPageError("");
      setStatusMessage(`Saved “${updated.name}”.`);
      setRecentId(category.id);
      focusAfterRender(categoryCardIds(category.id).edit);
      await reload();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === CATEGORY_NOT_FOUND) {
        handleGone(NOT_FOUND_MESSAGE);
      } else if (caught instanceof ApiError && caught.code === CATEGORY_BUILT_IN) {
        handleGone(caught.message);
      } else {
        throw caught; // Duplicate names, field messages, and other failures stay on the form.
      }
    }
  }

  async function handleDelete(category: CategorySummary): Promise<void> {
    // Decide where focus goes before the card disappears: the next card as currently shown
    // (searched, filtered, and sorted), else the previous one, else the list.
    const index = visible.findIndex((row) => row.id === category.id);
    const neighbour = visible[index + 1] ?? visible[index - 1];
    const ids = categoryCardIds(category.id);

    try {
      await deleteCategory(category.id);
      setWorkflow({ kind: "none" });
      setPageError("");
      setStatusMessage(`Deleted “${category.name}”.`);
      focusAfterRender(neighbour ? categoryCardIds(neighbour.id).heading : LIST_HEADING_ID);
      await reload();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === CATEGORY_IN_USE) {
        // The usage changed after the page loaded; the server is authoritative.
        setWorkflow({ kind: "none" });
        setRecentId(category.id);
        setPageError(
          `“${category.name}” is still used by transactions or budgets, so it can’t be deleted. `
          + "Change the category on those transactions and budgets, or delete them, then try again.",
        );
        focusAfterRender(ids.delete);
        void reload();
      } else if (caught instanceof ApiError && caught.code === CATEGORY_NOT_FOUND) {
        handleGone(NOT_FOUND_MESSAGE);
      } else if (caught instanceof ApiError && caught.code === CATEGORY_BUILT_IN) {
        handleGone(caught.message);
      } else {
        setWorkflow({ kind: "none" });
        setRecentId(category.id);
        setPageError("Unable to delete the category. Please try again.");
        focusAfterRender(ids.delete);
      }
    }
  }

  function workflowFor(category: CategorySummary) {
    const ids = categoryCardIds(category.id);
    if (workflow.kind === "edit" && workflow.id === category.id) {
      return (
        <CategoryForm
          label={`Edit ${category.name}`}
          initial={{ name: category.name, iconKey: resolveIconKey(category.iconKey) }}
          submitLabel="Save category"
          pendingLabel="Saving…"
          failureMessage="Unable to save the category. Please try again."
          onSubmit={(draft) => handleSave(category, draft)}
          onCancel={() => close(ids.edit, category.id)}
        />
      );
    }
    if (workflow.kind === "delete" && workflow.id === category.id) {
      return (
        <CategoryDeleteConfirm
          categoryName={category.name}
          onConfirm={() => handleDelete(category)}
          onCancel={() => close(ids.delete, category.id)}
        />
      );
    }
    return undefined;
  }

  return (
    <section className="categories-page" aria-labelledby="categories-heading">
      <div className="page-header">
        <h1 id="categories-heading">Categories</h1>
        <p>
          How each category is used
          {summary ? ` in ${monthLabel}` : " this month"}: spending, budgets, and activity.
        </p>
      </div>

      <StatusBanner message={statusMessage} onDismiss={() => setStatusMessage("")} />

      {pageError && (
        <div className="form-error categories-page__error" role="alert">
          <span>{pageError}</span>{" "}
          <button type="button" className="button button--secondary button--small" onClick={() => setPageError("")}>
            Dismiss
          </button>
        </div>
      )}

      {status === "error" && !summary && (
        <div className="form-error categories-page__error" role="alert">
          <span>{error}</span>{" "}
          <button type="button" className="button button--secondary button--small" onClick={() => void reload()}>
            Try again
          </button>
        </div>
      )}

      {status === "error" && summary && (
        <div className="form-warning categories-page__error" role="status">
          <span>Your change was saved, but category usage could not be refreshed.</span>{" "}
          <button type="button" className="button button--secondary button--small" onClick={() => void reload()}>
            Refresh usage
          </button>
        </div>
      )}

      <CategoryRefreshNotice />

      {!summary && status === "loading" && <p role="status">Loading categories…</p>}

      {summary && (
        <>
          {rows.length > 0 && (
            <>
              <CategorySummaryStrip rows={rows} monthLabel={monthLabel} />
              <CategorySpendingTable categories={rows} monthLabel={monthLabel} />
            </>
          )}

          <div className="categories-page__list-header">
            <h2 id={LIST_HEADING_ID} tabIndex={-1}>All categories</h2>
            {workflow.kind !== "create" && (
              <button
                id={CREATE_BUTTON_ID}
                type="button"
                className="button button--primary"
                onClick={() => {
                  setStatusMessage("");
                  setWorkflow({ kind: "create" });
                }}
              >
                Create category
              </button>
            )}
          </div>

          {workflow.kind === "create" && (
            <div className="category-card categories-page__create">
              <h3>New category</h3>
              <CategoryForm
                label="Create category"
                initial={EMPTY_CATEGORY_DRAFT}
                submitLabel="Create category"
                pendingLabel="Creating…"
                failureMessage="Unable to create the category. Please try again."
                onSubmit={handleCreate}
                onCancel={() => close(CREATE_BUTTON_ID)}
                focusNameOnOpen
              />
            </div>
          )}

          {rows.length > 0 && (
            <CategoryDiscoveryToolbar
              discovery={discovery}
              onChange={changeDiscovery}
              onClear={clearDiscovery}
              shown={visible.length}
              total={rows.length}
              keptVisibleNote={keptVisibleNote || undefined}
            />
          )}

          {rows.length === 0 ? (
            <p className="empty-state">You don’t have any categories yet.</p>
          ) : visible.length === 0 ? (
            <div className="categories-page__no-results">
              <p>No categories match your search and filter.</p>
              <button type="button" className="button button--secondary" onClick={clearDiscovery}>
                Show all categories
              </button>
            </div>
          ) : (
            <ul className="category-grid">
              {visible.map((category) => (
                <li key={category.id}>
                  <CategoryCard
                    category={category}
                    monthTotal={monthSpendingTotal(rows)}
                    monthName={monthName}
                    dateFormat={dateFormat}
                    onEdit={() => {
                      setStatusMessage("");
                      setWorkflow({ kind: "edit", id: category.id });
                    }}
                    onDelete={() => {
                      setStatusMessage("");
                      setWorkflow({ kind: "delete", id: category.id });
                    }}
                    workflow={workflowFor(category)}
                  />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}

export default CategoriesPage;
