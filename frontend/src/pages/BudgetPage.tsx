import { useEffect, useEffectEvent, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
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

import { CATEGORY_DUPLICATE, CATEGORY_NOT_FOUND } from "../services/categoryService";
import { useCategories } from "../context/CategoryContext";
import { CategoryIcon, CategoryLabel } from "../components/CategoryIcon";
import { CalendarDays, CalendarRange, Tags, Wallet } from "lucide-react";
import { IconField } from "../components/IconField";
import { InlineNotice } from "../components/InlineNotice";
import { CategoryRefreshNotice } from "../components/CategoryRefreshNotice";
import { StatusBanner } from "../components/StatusBanner";
import { CategorySelect } from "../components/CategorySelect";
import {
  CATEGORY_SELECTION_FIELDS,
  CREATE_CATEGORY_VALUE,
  DUPLICATE_CATEGORY_MESSAGE,
  EMPTY_CATEGORY_DRAFT,
  buildCategorySelection,
  findEquivalentCategory,
  focusFirstInvalid,
  splitFieldErrors,
  validateCategoryName,
  withoutFieldError,
  type CategoryDraft,
} from "../utils/categoryForm";
import { CATEGORY_PARAM, categoryLinkKey, resolveCategoryLink } from "../utils/categoryDeepLink";
import { MONTH_PARAM, YEAR_PARAM, parsePeriodParams } from "../utils/reportingPeriod";
import type {
  BudgetAnalyticsResponse,
  BudgetResponse,
  CreateBudgetRequest,
  UpdateBudgetRequest,
} from "../types/budget";

import type { CategoryResponse } from "../types/category";
import { formatCurrency } from "../utils/formatters";
import { formatBudgetStatus } from "../utils/budgetStatus";

interface BudgetFormState {
  /** A category ID, CREATE_CATEGORY_VALUE, or "" when nothing is chosen. */
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

/** Server fields shown beside a control; anything else goes to the form summary. */
const BUDGET_FIELDS = [...CATEGORY_SELECTION_FIELDS, "monthlyLimit", "month", "year"] as const;

const NEW_CATEGORY_ERRORS = ["newCategory", "newCategory.name", "newCategory.iconKey"];

const monthOptions = Array.from({ length: 12 }, (_, index) => ({
  value: index + 1,
  label: new Intl.DateTimeFormat("en-US", {
    month: "long",
  }).format(new Date(2026, index, 1)),
}));

const currentYear = new Date().getFullYear();

const defaultYearOptions = Array.from(
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

function BudgetPage() {
  const [budgets, setBudgets] = useState<BudgetResponse[]>([]);


  const [analytics, setAnalytics] = useState<
    Record<number, BudgetAnalyticsResponse>
  >({});

  const { categories, status: categoryStatus, reload: reloadCategories } = useCategories();
  const [form, setForm] = useState<BudgetFormState>(getInitialBudgetForm);
  const [categoryDraft, setCategoryDraft] = useState<CategoryDraft>(EMPTY_CATEGORY_DRAFT);
  const [existingMatch, setExistingMatch] = useState<CategoryResponse | undefined>();
  const [editingBudgetId, setEditingBudgetId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [formErrorMessage, setFormErrorMessage] = useState("");
  const [refreshWarning, setRefreshWarning] = useState("");
  const [saveMessage, setSaveMessage] = useState("");
  const [validationErrors, setValidationErrors] = useState<
    Record<string, string>
  >({});
  const [failureAttempt, setFailureAttempt] = useState(0);

  const submittingRef = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const formErrorRef = useRef<HTMLParagraphElement>(null);
  const formHeadingRef = useRef<HTMLHeadingElement>(null);
  const editTriggerIdRef = useRef<number | null>(null);
  const pendingFocusTriggerIdRef = useRef<number | null>(null);

  // ?month=&year= (from a Categories budget link) opens that month; anything else opens
  // today's month as before. Checked with the Categories parser, so both pages agree on
  // what a valid month is (one of each, plain numbers, month 1-12, year 2000 or later).
  const [searchParams, setSearchParams] = useSearchParams();
  const periodParams = parsePeriodParams(searchParams);
  const linkedPeriod = periodParams.kind === "period" ? periodParams.period : null;
  const linkedPeriodKey = linkedPeriod ? `${linkedPeriod.year}-${linkedPeriod.month}` : null;
  const [viewMonth, setViewMonth] = useState(() => String(linkedPeriod?.month ?? new Date().getMonth() + 1));
  const [viewYear, setViewYear] = useState(() => String(linkedPeriod?.year ?? new Date().getFullYear()));
  const [viewCategoryId, setViewCategoryId] = useState("");

  // A different linked month (Back/Forward between links) moves the view there once.
  const [appliedPeriodKey, setAppliedPeriodKey] = useState(linkedPeriodKey);
  if (linkedPeriodKey !== appliedPeriodKey) {
    setAppliedPeriodKey(linkedPeriodKey);
    if (linkedPeriod) {
      setViewMonth(String(linkedPeriod.month));
      setViewYear(String(linkedPeriod.year));
    }
  }

  // The linked year may be outside the usual range, so it is always offered.
  const yearOptions = Array.from(
    new Set([...defaultYearOptions, ...budgets.map((budget) => budget.year), Number(viewYear)]),
  ).sort((firstYear, secondYear) => firstYear - secondYear);

  // An invalid month is dropped (the category link still applies, to today's month). A
  // linked month describes the page only until the user shows another month (or a save
  // moves there): the whole link is then used up, so its category is dropped too and the
  // URL is plain /budgets again. Replaced, never pushed, so Back still leaves the page.
  const periodLinkInvalid = periodParams.kind === "invalid";
  const periodLinkLeft = linkedPeriod !== null && linkedPeriodKey === appliedPeriodKey
    && (String(linkedPeriod.month) !== viewMonth || String(linkedPeriod.year) !== viewYear);

  useEffect(() => {
    if (!periodLinkInvalid && !periodLinkLeft) return;
    setSearchParams((params) => {
      const next = new URLSearchParams(params);
      next.delete(MONTH_PARAM);
      next.delete(YEAR_PARAM);
      if (periodLinkLeft) next.delete(CATEGORY_PARAM);
      return next;
    }, { replace: true });
  }, [periodLinkInvalid, periodLinkLeft, setSearchParams]);

  /**
   * A category deleted elsewhere can no longer be chosen or filtered on. Until the list
   * has loaded, a stored ID (an edit, say) is kept as is.
   */
  function availableCategoryId(id: string): string {
    if (!id || id === CREATE_CATEGORY_VALUE || categoryStatus !== "ready") return id;
    return categories.some((category) => String(category.id) === id) ? id : "";
  }

  const formCategoryId = availableCategoryId(form.categoryId);
  const activeViewCategoryId = availableCategoryId(viewCategoryId);

  const periodBudgets = budgets.filter(
    (budget) =>
      budget.month === Number(viewMonth) && budget.year === Number(viewYear),
  );

  // Client-side category filter over the loaded period (the API returns every budget).
  const filteredBudgets = activeViewCategoryId
    ? periodBudgets.filter((budget) => String(budget.categoryId) === activeViewCategoryId)
    : periodBudgets;

  const viewCategory = categories.find((category) => String(category.id) === viewCategoryId);

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

    if (budgetResponse.length === 0) {
      setBudgets([]);
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

    setBudgets(budgetResponse);
    setAnalytics(analyticsByBudgetId);
  }

  useEffect(() => {
    async function loadPageData(): Promise<void> {
      try {
        setIsLoading(true);
        setErrorMessage("");

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

  // After a failed submit, focus the first invalid control in form order, else the summary.
  useEffect(() => {
    if (!failureAttempt) return;

    if (!focusFirstInvalid(formRef.current)) {
      formErrorRef.current?.focus();
    }
  }, [failureAttempt]);

  // ?category={id} from the Categories page: edit that category's budget for the
  // displayed month (the linked ?month=&year= when given) if it has one, otherwise start
  // a new budget with it preselected. Resolved against the user's own categories; invalid
  // links are dropped silently.
  const categoryLink = resolveCategoryLink(searchParams.get(CATEGORY_PARAM), categoryStatus, categories);
  // The month is part of the link: Back/Forward to the same category in another month
  // opens that month's budget.
  const linkKey = categoryLinkKey(categoryLink);
  const handledKey = `${linkKey}@${linkedPeriodKey ?? "today"}`;
  // The link handled last, so reloads and re-renders never reopen the form.
  const handledLinkKey = useRef<string | null>(null);

  const applyCategoryLink = useEffectEvent(() => {
    const removeParam = () => setSearchParams((params) => {
      const next = new URLSearchParams(params);
      next.delete(CATEGORY_PARAM);
      return next;
    }, { replace: true });

    if (categoryLink.kind === "invalid") {
      removeParam();
      return;
    }
    if (categoryLink.kind !== "valid") return;

    const existing = budgets.find((budget) => String(budget.categoryId) === categoryLink.id
      && budget.month === Number(viewMonth) && budget.year === Number(viewYear));

    if (existing) {
      handleEditBudget(existing); // The existing edit flow scrolls to and focuses the form.
    } else if (categoryLink.category.budgetEnabled) {
      setEditingBudgetId(null);
      setForm((current) => ({ ...current, categoryId: categoryLink.id, month: viewMonth, year: viewYear }));
      formHeadingRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
      formHeadingRef.current?.focus({ preventScroll: true });
    } else {
      removeParam(); // This category does not take budgets, so there is nothing to start.
    }
  });

  useEffect(() => {
    if (isLoading || linkKey === "pending" || linkKey === "none" || handledLinkKey.current === handledKey) {
      if (linkKey === "none") handledLinkKey.current = null;
      return;
    }
    handledLinkKey.current = handledKey;
    applyCategoryLink();
  }, [isLoading, linkKey, handledKey]);

  useEffect(() => {
    if (editingBudgetId === null) {
      return;
    }

    formHeadingRef.current?.scrollIntoView?.({
      behavior: "smooth",
      block: "start",
    });

    formHeadingRef.current?.focus({
      preventScroll: true,
    });
  }, [editingBudgetId]);

  useEffect(() => {
    if (editingBudgetId !== null || pendingFocusTriggerIdRef.current === null) {
      return;
    }

    const triggerId = pendingFocusTriggerIdRef.current;

    document
      .querySelector<HTMLButtonElement>(`[data-budget-edit-id="${triggerId}"]`)
      ?.focus();

    pendingFocusTriggerIdRef.current = null;
  }, [editingBudgetId]);

  function resetForm(): void {
    setForm(getInitialBudgetForm());

    setCategoryDraft(EMPTY_CATEGORY_DRAFT);

    setExistingMatch(undefined);

    setEditingBudgetId(null);

    setFormErrorMessage("");

    setValidationErrors({});

    editTriggerIdRef.current = null;
  }

  function handleCancelEdit(): void {
    pendingFocusTriggerIdRef.current = editTriggerIdRef.current;

    resetForm();
  }

  function handleEditBudget(budget: BudgetResponse): void {
    editTriggerIdRef.current = budget.id;
    setSaveMessage("");

    setEditingBudgetId(budget.id);

    setForm({
      categoryId: String(budget.categoryId),
      monthlyLimit: String(budget.monthlyLimit),
      month: String(budget.month),
      year: String(budget.year),
    });

    setCategoryDraft(EMPTY_CATEGORY_DRAFT);

    setExistingMatch(undefined);

    setFormErrorMessage("");

    setValidationErrors({});
  }

  function updateField<K extends keyof BudgetFormState>(field: K, value: BudgetFormState[K]): void {
    setForm((current) => ({ ...current, [field]: value }));
    setValidationErrors((current) => withoutFieldError(current, field));
  }

  function handleCategoryChange(value: string): void {
    setForm((current) => ({ ...current, categoryId: value }));
    setExistingMatch(undefined);
    setValidationErrors((current) => value === CREATE_CATEGORY_VALUE
      ? withoutFieldError(current, "categoryId")
      : withoutFieldError(current, "categoryId", ...NEW_CATEGORY_ERRORS));
  }

  function failValidation(fieldErrors: Record<string, string>, summary: string): void {
    setValidationErrors(fieldErrors);
    setFormErrorMessage(summary);
    setFailureAttempt((attempt) => attempt + 1);
  }

  async function handleSubmit(
    event: ReactSubmitEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    // Synchronous guard: a second submit before React re-renders is ignored.
    if (submittingRef.current) return;

    const isEditing = editingBudgetId !== null;
    const creatingCategory = formCategoryId === CREATE_CATEGORY_VALUE;

    setFormErrorMessage("");

    setRefreshWarning("");

    setSaveMessage("");

    setValidationErrors({});

    setExistingMatch(undefined);

    const clientErrors: Record<string, string> = {};

    if (!formCategoryId) {
      clientErrors.categoryId = "Please select a category.";
    } else if (creatingCategory) {
      const nameError = validateCategoryName(categoryDraft.name);
      if (nameError) clientErrors["newCategory.name"] = nameError;
    }

    if (!form.monthlyLimit || Number(form.monthlyLimit) <= 0) {
      clientErrors.monthlyLimit = "Monthly limit must be greater than 0.";
    }

    if (Object.keys(clientErrors).length) {
      failValidation(clientErrors, "Please check the highlighted fields.");
      return;
    }

    const request: CreateBudgetRequest | UpdateBudgetRequest = {
      ...buildCategorySelection(formCategoryId, categoryDraft),
      monthlyLimit: Number(form.monthlyLimit),
      month: Number(form.month),
      year: Number(form.year),
    };

    submittingRef.current = true;

    setIsSubmitting(true);

    try {
      if (editingBudgetId !== null) {
        await updateBudget(editingBudgetId, request);
      } else {
        await createBudget(request);
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === CATEGORY_DUPLICATE && creatingCategory) {
        failValidation({ "newCategory.name": DUPLICATE_CATEGORY_MESSAGE },
          "That category already exists. Nothing was saved.");
        const latest = await reloadCategories();
        setExistingMatch(latest ? findEquivalentCategory(latest, categoryDraft.name) : undefined);
      } else if (error instanceof ApiError && error.code === CATEGORY_NOT_FOUND) {
        failValidation({ categoryId: "This category is no longer available. Choose another category." },
          "Please check the highlighted fields.");
        void reloadCategories();
      } else if (error instanceof ApiError && error.validationErrors) {
        const { fieldErrors, otherMessages } = splitFieldErrors(error.validationErrors, BUDGET_FIELDS);
        failValidation(fieldErrors, otherMessages.join(" ") || "Please check the highlighted fields.");
      } else if (error instanceof ApiError) {
        failValidation({}, error.message);
      } else {
        failValidation({},
          isEditing
            ? "Unable to update the budget. Please try again."
            : "Unable to create the budget. Please try again.",
        );
      }

      submittingRef.current = false;
      setIsSubmitting(false);
      return;
    }

    /*
     * Move the displayed budget period to the month/year
     * that was successfully created or updated.
     */
    setViewMonth(String(request.month));

    setViewYear(String(request.year));

    setViewCategoryId("");

    resetForm();

    setSaveMessage(isEditing ? "Budget updated." : "Budget created.");

    try {
      // A new category is now reusable everywhere; a failed refresh only warns.
      if (creatingCategory) await reloadCategories();
      await loadBudgetData();
    } catch {
      setRefreshWarning(
        "Budget saved, but the budget list could not be refreshed. Reload the page to see the latest data.",
      );
    } finally {
      submittingRef.current = false;
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
      setSaveMessage("");

      await deleteBudget(budget.id);

      if (editingBudgetId === budget.id) {
        resetForm();
      }

      setSaveMessage("Budget deleted.");

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

      {errorMessage && <InlineNotice variant="error">{errorMessage}</InlineNotice>}

      {/* A refresh warning also confirms the save, so it replaces the banner and stays. */}
      {refreshWarning && <InlineNotice variant="warning">{refreshWarning}</InlineNotice>}

      <StatusBanner message={refreshWarning ? "" : saveMessage} onDismiss={() => setSaveMessage("")} />

      <CategoryRefreshNotice />

      <section>
        <h2 ref={formHeadingRef} tabIndex={-1}>
          {editingBudgetId !== null ? "Edit Budget" : "Create Budget"}
        </h2>

        <form ref={formRef} className="budget-form" onSubmit={handleSubmit} noValidate>
          <CategorySelect
            id="budget-category"
            value={formCategoryId}
            onChange={handleCategoryChange}
            draft={categoryDraft}
            onNameChange={(name) => {
              setCategoryDraft((current) => ({ ...current, name }));
              setExistingMatch(undefined);
              setValidationErrors((current) => withoutFieldError(current, "newCategory.name", "newCategory"));
            }}
            onNameBlur={() => {
              const nameError = validateCategoryName(categoryDraft.name);
              if (nameError) setValidationErrors((current) => ({ ...current, "newCategory.name": nameError }));
            }}
            onIconChange={(iconKey) => {
              setCategoryDraft((current) => ({ ...current, iconKey }));
              setValidationErrors((current) => withoutFieldError(current, "newCategory.iconKey"));
            }}
            errors={{
              selection: validationErrors.categoryId ?? validationErrors.newCategory,
              name: validationErrors["newCategory.name"],
              iconKey: validationErrors["newCategory.iconKey"],
            }}
            disabled={isSubmitting}
            existingMatch={existingMatch}
            onUseExisting={(category) => {
              handleCategoryChange(String(category.id));
              setFormErrorMessage("");
            }}
          />

          <div className="form-field">
            <label htmlFor="budget-monthly-limit">Monthly Limit</label>

            <IconField icon={Wallet}>
              <input
                id="budget-monthly-limit"
                type="number"
                min="0.01"
                step="0.01"
                value={form.monthlyLimit}
                aria-invalid={validationErrors.monthlyLimit ? true : undefined}
                aria-describedby={validationErrors.monthlyLimit ? "budget-monthly-limit-error" : undefined}
                onChange={(event) => updateField("monthlyLimit", event.target.value)}
                placeholder="500.00"
              />
            </IconField>

            {validationErrors.monthlyLimit && (
              <p id="budget-monthly-limit-error" className="field-error">
                {validationErrors.monthlyLimit}
              </p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="budget-month">Month</label>

            <IconField icon={CalendarDays}>
              <select
                id="budget-month"
                value={form.month}
                aria-invalid={validationErrors.month ? true : undefined}
                aria-describedby={validationErrors.month ? "budget-month-error" : undefined}
                onChange={(event) => updateField("month", event.target.value)}
              >
                {monthOptions.map((month) => (
                  <option key={month.value} value={month.value}>
                    {month.label}
                  </option>
                ))}
              </select>
            </IconField>

            {validationErrors.month && (
              <p id="budget-month-error" className="field-error">{validationErrors.month}</p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="budget-year">Year</label>

            <IconField icon={CalendarRange}>
              <input
                id="budget-year"
                type="number"
                min="2000"
                value={form.year}
                aria-invalid={validationErrors.year ? true : undefined}
                aria-describedby={validationErrors.year ? "budget-year-error" : undefined}
                onChange={(event) => updateField("year", event.target.value)}
              />
            </IconField>

            {validationErrors.year && (
              <p id="budget-year-error" className="field-error">{validationErrors.year}</p>
            )}
          </div>

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
                onClick={handleCancelEdit}
                disabled={isSubmitting}
              >
                Cancel Edit
              </button>
            )}
          </div>
        </form>

        {formErrorMessage && (
          <p ref={formErrorRef} role="alert" className="form-error" tabIndex={-1}>
            {formErrorMessage}
          </p>
        )}
      </section>

      <section className="budget-filter-section">
        <div>
          <h2>Budget Period</h2>

          <p>View budgets for a specific month.</p>
        </div>

        <div
          className="budget-period-filter"
          data-testid="budget-period-filter"
        >
          {" "}
          <label className="form-field">
            <span>Month</span>

            <IconField icon={CalendarDays}>
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
            </IconField>
          </label>
          <label className="form-field">
            <span>Year</span>

            <IconField icon={CalendarRange}>
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
            </IconField>
          </label>
          <div className="form-field">
            <label htmlFor="budget-filter-category">Filter by category</label>

            <div className="category-select__control">
              {/* The chosen category's own icon; a generic one for "All categories". */}
              {viewCategory
                ? <CategoryIcon iconKey={viewCategory.iconKey} />
                : <Tags className="category-icon category-icon--placeholder" aria-hidden="true" focusable="false" size={18} />}

              <select
                id="budget-filter-category"
                value={viewCategory ? viewCategoryId : ""}
                onChange={(event) => setViewCategoryId(event.target.value)}
              >
                <option value="">All categories</option>

                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
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

        {periodBudgets.length === 0 ? (
          <p className="empty-state">
            No budgets found for{" "}
            {formatBudgetMonth(Number(viewMonth), Number(viewYear))}.
          </p>
        ) : filteredBudgets.length === 0 ? (
          <p className="empty-state">
            No {viewCategory?.name ?? "matching"} budget for{" "}
            {formatBudgetMonth(Number(viewMonth), Number(viewYear))}. Choose “All categories” to
            see the other budgets for this month.
          </p>
        ) : (
          <div className="budget-grid">
            {filteredBudgets.map((budget) => {
              const budgetAnalytics = analytics[budget.id];

              return (
                <article
                  key={budget.id}
                  className="budget-card"
                  data-testid={`budget-card-${budget.id}`}
                >
                  {" "}
                  <div className="budget-card__header">
                    <div>
                      <h3>
                        <CategoryLabel name={budget.categoryName} iconKey={budget.categoryIconKey} />
                      </h3>

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
                      data-budget-edit-id={budget.id}
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
