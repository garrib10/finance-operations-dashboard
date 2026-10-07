import { ChevronDown, ChevronUp } from "lucide-react";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { CategoryCard } from "../components/CategoryCard";
import { CategoryDeleteConfirm } from "../components/CategoryDeleteConfirm";
import { CategoryDiscoveryToolbar } from "../components/CategoryDiscoveryToolbar";
import { CategoryForm } from "../components/CategoryForm";
import { CategoryPeriodControls, PERIOD_MONTH_ID } from "../components/CategoryPeriodControls";
import { CategoryRefreshNotice } from "../components/CategoryRefreshNotice";
import { resolveIconKey } from "../components/categoryIconRegistry";
import { CategorySpendingTable } from "../components/CategorySpendingTable";
import { CategorySummaryStrip } from "../components/CategorySummaryStrip";
import { InlineNotice } from "../components/InlineNotice";
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
  normalizeSearch,
  type CategoryDiscovery,
} from "../utils/categoryDiscovery";
import {
  readCategoriesActiveExpanded,
  readCategoriesOthersExpanded,
  saveCategoriesActiveExpanded,
  saveCategoriesOthersExpanded,
} from "../utils/categoriesSectionPreference";
import { formatReportingMonth, monthSpendingTotal, partitionByActivity } from "../utils/categorySummary";
import { categoryCardIds } from "../utils/categoryUsage";
import {
  isAvailablePeriod,
  isSamePeriod,
  parsePeriodParams,
  withPeriod,
  type ReportingPeriod,
} from "../utils/reportingPeriod";

const LIST_HEADING_ID = "categories-list-heading";
const CREATE_BUTTON_ID = "categories-create-button";
const SEARCH_ID = "category-search";
const ACTIVE_HEADING_ID = "categories-active-heading";
const OTHER_HEADING_ID = "categories-other-heading";
const OTHER_REGION_ID = "categories-other-list";
const ACTIVE_REGION_ID = "categories-active-list";
const NOTICE_ID = "categories-page-notice";
const NOT_FOUND_MESSAGE = "This category no longer exists. The list has been refreshed.";

/**
 * The page's one status message. Success floats in StatusBanner and closes itself; the
 * others stay in the page as an InlineNotice until dismissed or replaced. A `retry`
 * notice offers "Try again" to reload the usage summary.
 */
interface PageStatus {
  type: "success" | "info" | "warning" | "error";
  message: string;
  retry?: boolean;
}

/** A change the server accepted, but whose fresh summary could not be loaded. */
function refreshWarning(name: string, verb: "created" | "updated" | "deleted"): PageStatus {
  return {
    type: "warning",
    message: `“${name}” was ${verb}, but the latest category summary could not be loaded. `
      + "Try again, or refresh the page to see the current data.",
    retry: true,
  };
}

type Workflow =
  | { kind: "none" }
  | { kind: "create" }
  | { kind: "edit"; id: number }
  | { kind: "delete"; id: number };

/**
 * The home for category management. Changes go through CategoryContext (the shared
 * list every category dropdown uses), then the usage summary is reloaded. Success is
 * announced only once fresh data has arrived; a change the server accepted is never
 * reported as failed: if the refresh fails, a warning says so and offers a retry.
 */
function CategoriesPage() {
  const { user } = useAuth();
  // ?month=&year= is the source of truth for an earlier month; no parameters means the
  // server's current month. The hook only requests a period once it is known to be valid.
  const [searchParams, setSearchParams] = useSearchParams();
  const periodParams = parsePeriodParams(searchParams);
  const urlPeriod = periodParams.kind === "period" ? periodParams.period : null;
  const { summary, current, status, error, reload } = useCategorySummary(urlPeriod);
  const { createCategory, updateCategory, deleteCategory, reload: reloadCategories } = useCategories();
  const dateFormat = user?.preferences?.dateFormat ?? "MEDIUM";

  const [workflow, setWorkflow] = useState<Workflow>({ kind: "none" });
  // The one card whose "More actions" disclosure is open, by category ID.
  const [openActionsId, setOpenActionsId] = useState<number | null>(null);
  const [pageStatus, setPageStatus] = useState<PageStatus | null>(null);
  // Search, filter, and sort for the cards only (never the summary or spending table).
  const [discovery, setDiscovery] = useState<CategoryDiscovery>(DEFAULT_DISCOVERY);
  // The category just worked on stays visible until the toolbar next changes, so a rename
  // or a cancel never makes the card (and its focus target) vanish.
  const [recentId, setRecentId] = useState<number | null>(null);
  // The edit in progress, kept here so it survives the card being rebuilt when the page
  // switches between sections and the flat search results.
  const [editDraft, setEditDraft] = useState<CategoryDraft | null>(null);
  // The user's own Show/Hide choice for "Other categories", saved per device.
  const [othersSaved, setOthersSaved] = useState(readCategoriesOthersExpanded);
  // The same for "Active this month", which is open unless the user hid it.
  const [activeSaved, setActiveSaved] = useState(readCategoriesActiveExpanded);
  // A card that must stay on screen for focus (just created, saved, cancelled, or the next
  // card after a delete). It opens "Other categories" without touching the saved choice,
  // and the user's own Show/Hide press clears it.
  const [revealId, setRevealId] = useState<number | null>(null);
  // The month (year-month) whose open-by-default "nothing active" state the user hid.
  const [emptyMonthHidden, setEmptyMonthHidden] = useState<string | null>(null);
  // Deleted on the server: hidden at once, even if the summary refresh then fails.
  const [removedIds, setRemovedIds] = useState<readonly number[]>([]);
  // Element to focus once the list has re-rendered, with a fallback if it is gone.
  const pendingFocus = useRef<{ id: string; fallback: string } | null>(null);

  // The month the page should show: a valid URL month, else the server's current month.
  const targetPeriod: ReportingPeriod | null =
    urlPeriod && (!current || isAvailablePeriod(urlPeriod, current)) ? urlPeriod : current;
  // Data is shown only when it describes that month, so a switch never shows one month's
  // figures under another month's label (the previous data waits out of sight).
  const shown = summary && targetPeriod && isSamePeriod({ month: summary.month, year: summary.year }, targetPeriod)
    ? summary
    : null;
  const historical = Boolean(shown && current && !isSamePeriod({ month: shown.month, year: shown.year }, current));
  const monthLabel = shown ? formatReportingMonth(shown.month, shown.year) : "";
  const monthName = monthLabel.split(" ")[0];
  const rows = (shown?.categories ?? []).filter((row) => !removedIds.includes(row.id));
  const workflowId = workflow.kind === "edit" || workflow.kind === "delete" ? workflow.id : null;
  const visible = discoverCategories(rows, discovery, [workflowId, recentId]);
  const keptVisible = visible.find((row) => !matchesSearch(row, discovery.query) || !matchesFilter(row, discovery.filter));
  const keptVisibleNote = keptVisible
    && `“${keptVisible.name}” is shown because you’re working on it, though it doesn’t match the current search or filter.`;

  // Browsing (no search, all categories): Active this month, then Other categories, each in
  // the chosen sort order and each category once. Searching or filtering shows one flat list.
  const browsing = normalizeSearch(discovery.query) === "" && discovery.filter === "all";
  const { activeCategories, otherCategories } = partitionByActivity(visible);
  const inOthers = (id: number | null) => id !== null && otherCategories.some((row) => row.id === id);
  const inActive = (id: number | null) => id !== null && activeCategories.some((row) => row.id === id);
  // Active this month: the saved choice, kept open while one of its cards has a form or
  // confirmation open (the toggle is then hidden) or holds the card focus is moving to.
  const activeForced = inActive(workflowId);
  const activeOpen = activeForced || activeSaved || inActive(revealId);
  // Reasons that must keep the section open (the toggle is hidden while they apply)...
  const othersForced = workflow.kind === "create" || inOthers(workflowId);
  // ...a month with nothing active, which opens it by default (the page would otherwise be
  // empty) but, like everywhere else, can still be hidden: that choice lasts for the month...
  const shownKey = shown ? `${shown.year}-${shown.month}` : "";
  const othersOpenForEmpty = activeCategories.length === 0 && emptyMonthHidden !== shownKey;
  // ...and the effective state: forced, empty, the saved choice, or a card kept for focus.
  const othersOpen = othersForced || othersOpenForEmpty || othersSaved || inOthers(revealId);
  const shownCount = browsing
    ? (activeOpen ? activeCategories.length : 0) + (othersOpen ? otherCategories.length : 0)
    : visible.length;
  // The order cards are rendered in, for choosing the next card after a delete.
  const renderedOrder = browsing ? [...activeCategories, ...otherCategories] : visible;

  useEffect(() => {
    const target = pendingFocus.current;
    if (!target || status === "loading") return;
    const element = document.getElementById(target.id) ?? document.getElementById(target.fallback);
    element?.focus();
    // Keep trying on the next render if the target could not take focus yet (a button
    // that is still disabled while its request finishes, say).
    if (element && document.activeElement === element) pendingFocus.current = null;
  });

  function focusAfterRender(id: string, fallback = LIST_HEADING_ID): void {
    pendingFocus.current = { id, fallback };
  }

  /**
   * Corrects the URL without adding history: malformed or partial period parameters, a
   * month outside January 2000 to the current month, and an explicit current month (the
   * same as no parameters) are all replaced by the plain URL. Other parameters are kept.
   */
  const normalizePeriodUrl = useEffectEvent(() => {
    const unavailable = urlPeriod && current && (!isAvailablePeriod(urlPeriod, current) || isSamePeriod(urlPeriod, current));
    if (periodParams.kind === "invalid" || unavailable) {
      setSearchParams((params) => withPeriod(params, null), { replace: true });
    }
  });

  useEffect(() => {
    normalizePeriodUrl();
  }, [searchParams, current?.month, current?.year]);

  /** A month or year the user chose: a new history entry, so Back returns to the last one. */
  function changePeriod(next: ReportingPeriod): void {
    if (!current) return;
    setOpenActionsId(null);
    setSearchParams((params) => withPeriod(params, isSamePeriod(next, current) ? null : next));
  }

  function resetPeriod(): void {
    setOpenActionsId(null);
    setSearchParams((params) => withPeriod(params, null));
    focusAfterRender(PERIOD_MONTH_ID); // The Back button disappears; keep focus nearby.
  }

  /** Keeps a card on screen (and "Other categories" open if it is there) for focus. */
  function keepOnScreen(categoryId: number): void {
    setRecentId(categoryId);
    setRevealId(categoryId);
  }

  function close(focusId: string, categoryId?: number): void {
    setWorkflow({ kind: "none" });
    if (categoryId !== undefined) keepOnScreen(categoryId);
    focusAfterRender(focusId);
  }

  function changeDiscovery(next: CategoryDiscovery): void {
    setDiscovery(next);
    setRecentId(null);
    setRevealId(null);
    setOpenActionsId(null);
  }

  /** The user's explicit Show/Hide for Active this month: the only thing that saves it. */
  function toggleActive(): void {
    const next = !activeOpen;
    setActiveSaved(next);
    saveCategoriesActiveExpanded(next);
    setRevealId(null);
    setOpenActionsId(null);
  }

  /** The user's explicit Show/Hide: the only thing that saves the preference. */
  function toggleOthers(): void {
    const next = !othersOpen;
    setOthersSaved(next);
    saveCategoriesOthersExpanded(next);
    // Hiding a month with nothing active also overrides its open-by-default for that month.
    setEmptyMonthHidden(next ? null : shownKey);
    setRevealId(null);
    setOpenActionsId(null);
  }

  function clearDiscovery(): void {
    changeDiscovery(DEFAULT_DISCOVERY);
    focusAfterRender(SEARCH_ID); // The Clear button disappears, so keep focus nearby.
  }

  /** Starting a new operation replaces whatever an earlier one reported. */
  function startWorkflow(next: Workflow): void {
    setPageStatus(null);
    setEditDraft(null);
    setOpenActionsId(null);
    setWorkflow(next);
  }

  /**
   * After the server accepted a change: reload the usage summary, then report success
   * only if fresh data arrived; otherwise warn honestly that the change did happen.
   */
  async function reportAfterRefresh(name: string, verb: "created" | "updated" | "deleted"): Promise<void> {
    const refreshed = await reload();
    setPageStatus(refreshed
      ? { type: "success", message: `“${name}” was ${verb} successfully.` }
      : refreshWarning(name, verb));
  }

  async function retryRefresh(): Promise<void> {
    const refreshed = await reload();
    if (refreshed) setPageStatus(null);
  }

  /** A category changed elsewhere: refresh both lists and explain. */
  function handleGone(message: string): void {
    setWorkflow({ kind: "none" });
    setPageStatus({ type: "error", message });
    focusAfterRender(NOTICE_ID);
    void reloadCategories();
    void reload();
  }

  async function handleCreate(draft: CategoryDraft): Promise<void> {
    const created = await createCategory({ name: draft.name, budgetEnabled: true, iconKey: draft.iconKey });
    setWorkflow({ kind: "none" });
    keepOnScreen(created.id); // Usually an Other category: no spending or budget yet.
    focusAfterRender(categoryCardIds(created.id).heading);
    await reportAfterRefresh(created.name, "created");
  }

  async function handleSave(category: CategorySummary, draft: CategoryDraft): Promise<void> {
    try {
      const updated = await updateCategory(category.id, {
        name: draft.name,
        budgetEnabled: category.budgetEnabled,
        iconKey: draft.iconKey,
      });
      setWorkflow({ kind: "none" });
      keepOnScreen(category.id);
      focusAfterRender(categoryCardIds(category.id).actions);
      await reportAfterRefresh(updated.name, "updated");
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
    // Decide where focus goes before the card disappears: the next card in the rendered
    // order (Active, then Other; or the searched/filtered list), else the previous one,
    // else the list heading.
    const index = renderedOrder.findIndex((row) => row.id === category.id);
    const neighbour = renderedOrder[index + 1] ?? renderedOrder[index - 1];
    const ids = categoryCardIds(category.id);

    const name = category.name; // Captured before the card (and its data) go away.

    try {
      await deleteCategory(category.id);
      setWorkflow({ kind: "none" });
      setRemovedIds((removed) => [...removed, category.id]);
      if (neighbour) setRevealId(neighbour.id); // Opens "Other categories" if it is there.
      focusAfterRender(neighbour ? categoryCardIds(neighbour.id).heading : LIST_HEADING_ID);
      await reportAfterRefresh(name, "deleted");
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === CATEGORY_IN_USE) {
        // The usage changed after the page loaded; the server is authoritative. Nothing
        // was removed, and a retry cannot succeed, so the confirmation closes.
        setWorkflow({ kind: "none" });
        keepOnScreen(category.id);
        setPageStatus({
          type: "error",
          message: `“${name}” is still used by transactions or budgets, so it can’t be deleted. `
            + "Change the category on those transactions and budgets, or delete them, then try again.",
        });
        focusAfterRender(ids.actions);
        void reload();
      } else if (caught instanceof ApiError && caught.code === CATEGORY_NOT_FOUND) {
        handleGone(NOT_FOUND_MESSAGE);
      } else if (caught instanceof ApiError && caught.code === CATEGORY_BUILT_IN) {
        handleGone(caught.message);
      } else {
        // Nothing was deleted: keep the confirmation open (it refocuses its button) so the
        // user can try again.
        setPageStatus({ type: "error", message: `“${name}” was not deleted. Please try again.` });
      }
    }
  }

  function workflowFor(category: CategorySummary) {
    const ids = categoryCardIds(category.id);
    if (workflow.kind === "edit" && workflow.id === category.id) {
      return (
        <CategoryForm
          label={`Edit ${category.name}`}
          initial={editDraft ?? { name: category.name, iconKey: resolveIconKey(category.iconKey) }}
          onDraftChange={setEditDraft}
          submitLabel="Save category"
          pendingLabel="Saving…"
          failureMessage="Unable to save the category. Please try again."
          onSubmit={(draft) => handleSave(category, draft)}
          onCancel={() => close(ids.actions, category.id)}
        />
      );
    }
    if (workflow.kind === "delete" && workflow.id === category.id) {
      return (
        <CategoryDeleteConfirm
          categoryName={category.name}
          onConfirm={() => handleDelete(category)}
          onCancel={() => close(ids.actions, category.id)}
        />
      );
    }
    return undefined;
  }

  function renderGrid(categories: CategorySummary[]) {
    return (
      <ul className="category-grid">
        {categories.map((category) => (
          <li key={category.id}>
            <CategoryCard
              category={category}
              monthTotal={monthSpendingTotal(rows)}
              monthName={monthName}
              dateFormat={dateFormat}
              onEdit={() => startWorkflow({ kind: "edit", id: category.id })}
              onDelete={() => startWorkflow({ kind: "delete", id: category.id })}
              workflow={workflowFor(category)}
              actionsOpen={openActionsId === category.id}
              onActionsOpenChange={(open) => setOpenActionsId((current) => {
                if (open) return category.id;
                return current === category.id ? null : current;
              })}
            />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section className="categories-page" aria-labelledby="categories-heading">
      <div className="page-header">
        <h1 id="categories-heading">Categories</h1>
        <p>
          How each category is used
          {shown ? ` in ${monthLabel}` : " this month"}: spending, budgets, and activity.
        </p>
      </div>

      {current && targetPeriod && (
        <CategoryPeriodControls
          selected={targetPeriod}
          current={current}
          onChange={changePeriod}
          onReset={resetPeriod}
          disabledReason={workflow.kind === "none" ? undefined
            : "Finish or cancel the open form or confirmation to change the month."}
        />
      )}

      {/* One short announcement when a month has finished loading; never per card. */}
      <p className="visually-hidden" role="status">{shown && status !== "loading" ? `Showing ${monthLabel}` : ""}</p>

      {/* One status area: a floating success confirmation, or one persistent notice. */}
      <StatusBanner
        message={pageStatus?.type === "success" ? pageStatus.message : ""}
        onDismiss={() => setPageStatus(null)}
      />

      {status === "error" && !shown && (
        // The first load failed: there is no data, so nothing stale is shown.
        <InlineNotice variant="error" action={{ label: "Try again", onClick: () => void reload() }}>
          {error}
        </InlineNotice>
      )}

      {pageStatus && pageStatus.type !== "success" && (
        <InlineNotice
          id={NOTICE_ID}
          variant={pageStatus.type}
          action={pageStatus.retry ? { label: "Try again", onClick: () => void retryRefresh() } : undefined}
          onDismiss={() => setPageStatus(null)}
        >
          {pageStatus.message}
        </InlineNotice>
      )}

      {/* The shared category-list warning; the page's own notice takes priority. */}
      {(!pageStatus || pageStatus.type === "success") && <CategoryRefreshNotice />}

      {!summary && status === "loading" && <p role="status">Loading categories…</p>}
      {summary && !shown && status === "loading" && targetPeriod && (
        <p role="status">Loading {formatReportingMonth(targetPeriod.month, targetPeriod.year)}…</p>
      )}

      {summary && (
        <>
          {shown && rows.length > 0 && (
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
                onClick={() => startWorkflow({ kind: "create" })}
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

          {shown && rows.length > 0 && (
            <CategoryDiscoveryToolbar
              discovery={discovery}
              onChange={changeDiscovery}
              onClear={clearDiscovery}
              shown={shownCount}
              total={rows.length}
              keptVisibleNote={keptVisibleNote || undefined}
            />
          )}

          {/* The list header and create form above stay while another month loads, so an
              open form is never lost; the cards wait for that month's data. */}
          {!shown ? null : rows.length === 0 ? (
            <p className="empty-state">You don’t have any categories yet.</p>
          ) : visible.length === 0 ? (
            <div className="categories-page__no-results">
              <p>No categories match your search and filter.</p>
              <button type="button" className="button button--secondary" onClick={clearDiscovery}>
                Show all categories
              </button>
            </div>
          ) : browsing ? (
            <>
              <section className="categories-section" aria-labelledby={ACTIVE_HEADING_ID}>
                <div className="categories-section__header">
                  <h2 id={ACTIVE_HEADING_ID}>{historical ? `Active in ${monthLabel}` : "Active this month"}</h2>
                  {/* Hidden while one of its cards has a form or confirmation open. */}
                  {activeCategories.length > 0 && !activeForced && (
                    <button
                      type="button"
                      className="button button--secondary button--small categories-section__toggle"
                      aria-expanded={activeOpen}
                      aria-controls={ACTIVE_REGION_ID}
                      onClick={toggleActive}
                    >
                      {activeOpen ? "Hide active categories" : "Show active categories"}
                      {activeOpen
                        ? <ChevronUp aria-hidden="true" focusable="false" size={16} />
                        : <ChevronDown aria-hidden="true" focusable="false" size={16} />}
                    </button>
                  )}
                </div>
                {activeCategories.length === 0 ? (
                  <p className="empty-state">
                    {historical
                      ? `Nothing had spending or a budget in ${monthLabel}.`
                      : `Nothing has spending or a budget in ${monthName} yet.`}
                  </p>
                ) : (
                  // Closed: nothing inside is rendered, so no hidden card can take focus.
                  <div id={ACTIVE_REGION_ID} hidden={!activeOpen}>
                    {activeOpen && renderGrid(activeCategories)}
                  </div>
                )}
              </section>

              <section className="categories-section" aria-labelledby={OTHER_HEADING_ID}>
                <div className="categories-section__header">
                  <h2 id={OTHER_HEADING_ID}>Other categories · {otherCategories.length}</h2>
                  {/* Hidden while something requires the section open, so it never lies. */}
                  {otherCategories.length > 0 && !othersForced && (
                    <button
                      type="button"
                      className="button button--secondary button--small categories-section__toggle"
                      aria-expanded={othersOpen}
                      aria-controls={OTHER_REGION_ID}
                      onClick={toggleOthers}
                    >
                      {othersOpen ? "Hide other categories" : "Show other categories"}
                      {othersOpen
                        ? <ChevronUp aria-hidden="true" focusable="false" size={16} />
                        : <ChevronDown aria-hidden="true" focusable="false" size={16} />}
                    </button>
                  )}
                </div>
                {otherCategories.length === 0 ? (
                  <p className="empty-state">
                    {historical
                      ? `Every category had spending or a budget in ${monthLabel}.`
                      : "Every category has spending or a budget this month."}
                  </p>
                ) : (
                  // Closed: nothing inside is rendered, so no hidden card can take focus.
                  <div id={OTHER_REGION_ID} hidden={!othersOpen}>
                    {othersOpen && renderGrid(otherCategories)}
                  </div>
                )}
              </section>
            </>
          ) : (
            renderGrid(visible)
          )}
        </>
      )}
    </section>
  );
}

export default CategoriesPage;
