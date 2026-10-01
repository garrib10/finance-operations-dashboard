import { useAuth } from "../context/AuthContext";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import type { SubmitEvent as ReactSubmitEvent } from "react";
import { ApiError } from "../services/api";
import { CATEGORY_DUPLICATE, CATEGORY_NOT_FOUND } from "../services/categoryService";
import {
  createTransaction,
  deleteTransaction,
  getTransactions,
  updateTransaction,
} from "../services/transactionService";
import { useCategories } from "../context/CategoryContext";
import { CategoryIcon, CategoryLabel } from "../components/CategoryIcon";
import { CategoryManager, type CategoryChange } from "../components/CategoryManager";
import { CategoryRefreshNotice } from "../components/CategoryRefreshNotice";
import { CategorySelect } from "../components/CategorySelect";
import type { CategoryResponse } from "../types/category";
import type {
  CreateTransactionRequest,
  PagedTransactionResponse,
  TransactionFilterRequest,
  TransactionResponse,
  TransactionSortField,
  TransactionType,
  SortDirection,
} from "../types/transaction";
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
import { formatCurrency, formatDate } from "../utils/formatters";

interface TransactionFormState {
  /** A category ID, CREATE_CATEGORY_VALUE, or "" when nothing is chosen. */
  categoryId: string;
  type: TransactionType;
  amount: string;
  description: string;
  transactionDate: string;
}

interface TransactionFilterState {
  search: string;
  type: "" | TransactionType;
  categoryId: string;
  startDate: string;
  endDate: string;
  minAmount: string;
  maxAmount: string;
  sortBy: TransactionSortField;
  sortDirection: SortDirection;
}

/** Server fields shown beside a control; anything else goes to the form summary. */
const TRANSACTION_FIELDS = [
  ...CATEGORY_SELECTION_FIELDS,
  "type",
  "amount",
  "description",
  "transactionDate",
] as const;

const NEW_CATEGORY_ERRORS = ["newCategory", "newCategory.name", "newCategory.iconKey"];

const initialFormState: TransactionFormState = {
  categoryId: "",
  type: "EXPENSE",
  amount: "",
  description: "",
  transactionDate: "",
};

const initialFilterState: TransactionFilterState = {
  search: "",
  type: "",
  categoryId: "",
  startDate: "",
  endDate: "",
  minAmount: "",
  maxAmount: "",
  sortBy: "transactionDate",
  sortDirection: "desc",
};

function TransactionPage() {
  const { user } = useAuth();
  const { categories, status: categoryStatus, reload: reloadCategories } = useCategories();
  const pageSize = user?.preferences?.transactionPageSize ?? 10;
  const requestSequence = useRef(0);
  const [transactionData, setTransactionData] =
    useState<PagedTransactionResponse | null>(null);

  const [form, setForm] = useState<TransactionFormState>(initialFormState);

  const [categoryDraft, setCategoryDraft] = useState<CategoryDraft>(EMPTY_CATEGORY_DRAFT);

  const [existingMatch, setExistingMatch] = useState<CategoryResponse | undefined>();

  const [filters, setFilters] =
    useState<TransactionFilterState>(initialFilterState);

  const [editingTransactionId, setEditingTransactionId] = useState<
    number | null
  >(null);

  const [isLoading, setIsLoading] = useState(true);

  const [isSubmitting, setIsSubmitting] = useState(false);

  const [errorMessage, setErrorMessage] = useState("");

  const [formErrorMessage, setFormErrorMessage] = useState("");

  const [refreshWarning, setRefreshWarning] = useState("");

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

  // After a failed submit, focus the first invalid control in form order, else the summary.
  useEffect(() => {
    if (!failureAttempt) return;

    if (!focusFirstInvalid(formRef.current)) {
      formErrorRef.current?.focus();
    }
  }, [failureAttempt]);

  useEffect(() => {
    if (editingTransactionId === null) {
      return;
    }

    formHeadingRef.current?.scrollIntoView?.({
      behavior: "smooth",
      block: "start",
    });

    formHeadingRef.current?.focus({
      preventScroll: true,
    });
  }, [editingTransactionId]);

  useEffect(() => {
    if (
      editingTransactionId !== null ||
      pendingFocusTriggerIdRef.current === null
    ) {
      return;
    }

    const triggerId = pendingFocusTriggerIdRef.current;

    document
      .querySelector<HTMLButtonElement>(
        `[data-transaction-edit-id="${triggerId}"]`,
      )
      ?.focus();

    pendingFocusTriggerIdRef.current = null;
  }, [editingTransactionId]);

  function buildFilters(page = 0, source: TransactionFilterState = filters): TransactionFilterRequest {
    return {
      page,
      size: pageSize,
      sortBy: source.sortBy,
      sortDirection: source.sortDirection,
      ...(source.search.trim() && {
        search: source.search.trim(),
      }),
      ...(source.type && {
        type: source.type,
      }),
      ...(availableCategoryId(source.categoryId) && {
        categoryId: Number(source.categoryId),
      }),
      ...(source.startDate && {
        startDate: source.startDate,
      }),
      ...(source.endDate && {
        endDate: source.endDate,
      }),
      ...(source.minAmount && {
        minAmount: Number(source.minAmount),
      }),
      ...(source.maxAmount && {
        maxAmount: Number(source.maxAmount),
      }),
    };
  }

  async function loadTransactions(
    transactionFilters: TransactionFilterRequest = {
      page: 0,
      size: pageSize,
      sortBy: "transactionDate",
      sortDirection: "desc",
    },
  ): Promise<void> {
    const sequence = ++requestSequence.current;
    const response = await getTransactions(transactionFilters);

    if (sequence === requestSequence.current) setTransactionData(response);
  }

  const filtersForSizeChange = useEffectEvent(() => buildFilters(0));

  useEffect(() => {
    let active = true;
    const sequence = ++requestSequence.current;
    async function loadTransactionPage(): Promise<void> {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const transactionsResponse = await getTransactions({
          ...filtersForSizeChange(),
          size: pageSize,
        });

        if (!active || sequence !== requestSequence.current) return;
        setTransactionData(transactionsResponse);
      } catch (error) {
        if (!active || sequence !== requestSequence.current) return;
        if (error instanceof ApiError) {
          setErrorMessage(error.message);
        } else {
          setErrorMessage("Unable to load transaction data. Please try again.");
        }
      } finally {
        if (active && sequence === requestSequence.current) setIsLoading(false);
      }
    }

    void loadTransactionPage();
    return () => { active = false; };
  }, [pageSize]);

  /**
   * A category deleted elsewhere can no longer be chosen or filtered on. Until the list
   * has loaded, a stored ID (an edit, say) is kept as is.
   */
  function availableCategoryId(id: string): string {
    if (!id || id === CREATE_CATEGORY_VALUE || categoryStatus !== "ready") return id;
    return categories.some((category) => String(category.id) === id) ? id : "";
  }

  const formCategoryId = availableCategoryId(form.categoryId);

  function resetForm(): void {
    setForm(initialFormState);
    setCategoryDraft(EMPTY_CATEGORY_DRAFT);
    setExistingMatch(undefined);
    setEditingTransactionId(null);
    setFormErrorMessage("");
    setValidationErrors({});
    editTriggerIdRef.current = null;
  }

  function handleCancelEdit(): void {
    pendingFocusTriggerIdRef.current = editTriggerIdRef.current;

    resetForm();
  }

  function handleEdit(transaction: TransactionResponse): void {
    editTriggerIdRef.current = transaction.id;

    setEditingTransactionId(transaction.id);

    setForm({
      categoryId: transaction.categoryId.toString(),
      type: transaction.type,
      amount: transaction.amount.toString(),
      description: transaction.description,
      transactionDate: transaction.transactionDate,
    });

    setCategoryDraft(EMPTY_CATEGORY_DRAFT);
    setExistingMatch(undefined);
    setFormErrorMessage("");
    setValidationErrors({});
  }

  function updateField<K extends keyof TransactionFormState>(field: K, value: TransactionFormState[K]): void {
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

    const isEditing = editingTransactionId !== null;
    const creatingCategory = formCategoryId === CREATE_CATEGORY_VALUE;

    setFormErrorMessage("");
    setRefreshWarning("");
    setValidationErrors({});
    setExistingMatch(undefined);

    if (!formCategoryId) {
      failValidation({ categoryId: "Choose an existing category or create a new one" },
        "Please check the highlighted fields.");
      return;
    }

    if (creatingCategory) {
      const nameError = validateCategoryName(categoryDraft.name);
      if (nameError) {
        failValidation({ "newCategory.name": nameError }, "Please check the highlighted fields.");
        return;
      }
    }

    const request: CreateTransactionRequest = {
      ...buildCategorySelection(formCategoryId, categoryDraft),
      type: form.type,
      amount: Number(form.amount),
      description: form.description.trim(),
      transactionDate: form.transactionDate,
    };

    submittingRef.current = true;
    setIsSubmitting(true);

    try {
      if (editingTransactionId !== null) {
        await updateTransaction(editingTransactionId, request);
      } else {
        await createTransaction(request);
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
        const { fieldErrors, otherMessages } = splitFieldErrors(error.validationErrors, TRANSACTION_FIELDS);
        failValidation(fieldErrors, otherMessages.join(" ") || "Please check the highlighted fields.");
      } else if (error instanceof ApiError) {
        failValidation({}, error.message);
      } else {
        failValidation({},
          isEditing
            ? "Unable to update the transaction. Please try again."
            : "Unable to create the transaction. Please try again.",
        );
      }

      submittingRef.current = false;
      setIsSubmitting(false);
      return;
    }

    resetForm();

    try {
      // A new category is now reusable everywhere; a failed refresh only warns.
      if (creatingCategory) await reloadCategories();
      await loadTransactions(buildFilters(0));
    } catch {
      setRefreshWarning(
        "Transaction saved, but the transaction list could not be refreshed. Reload the page to see the latest data.",
      );
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  }

  async function handleDelete(transaction: TransactionResponse): Promise<void> {
    const shouldDelete = window.confirm(`Delete "${transaction.description}"?`);

    if (!shouldDelete) {
      return;
    }

    try {
      setErrorMessage("");

      await deleteTransaction(transaction.id);

      if (editingTransactionId === transaction.id) {
        resetForm();
      }

      await loadTransactions(buildFilters(0));
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to delete transaction. Please try again.");
      }
    }
  }

  async function handleFilterSubmit(
    event: ReactSubmitEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    try {
      setIsLoading(true);
      setErrorMessage("");

      await loadTransactions(buildFilters(0));
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to filter transactions. Please try again.");
      }
    } finally {
      setIsLoading(false);
    }
  }

  async function handleResetFilters(): Promise<void> {
    setFilters(initialFilterState);

    try {
      setIsLoading(true);
      setErrorMessage("");

      await loadTransactions({
        page: 0,
        size: pageSize,
        sortBy: "transactionDate",
        sortDirection: "desc",
      });
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to load transactions. Please try again.");
      }
    } finally {
      setIsLoading(false);
    }
  }

  async function handlePageChange(page: number): Promise<void> {
    if (!transactionData || page < 0 || page >= transactionData.totalPages) {
      return;
    }

    try {
      setIsLoading(true);
      setErrorMessage("");

      await loadTransactions(buildFilters(page));
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.message);
      } else {
        setErrorMessage("Unable to load the requested page. Please try again.");
      }
    } finally {
      setIsLoading(false);
    }
  }

  /** Renames show up in the list; a deleted category leaves the form and filter. */
  async function handleCategoryManaged(change: CategoryChange): Promise<void> {
    let nextFilters = filters;

    if (change.type === "deleted") {
      const id = String(change.categoryId);
      if (form.categoryId === id) setForm((current) => ({ ...current, categoryId: "" }));
      if (filters.categoryId === id) {
        nextFilters = { ...filters, categoryId: "" };
        setFilters(nextFilters);
      }
    }

    try {
      await loadTransactions(buildFilters(transactionData?.page ?? 0, nextFilters));
    } catch {
      setRefreshWarning(
        "The category was updated, but the transaction list could not be refreshed. Reload the page to see the latest data.",
      );
    }
  }

  const selectedFilterCategory = categories.find(
    (category) => String(category.id) === filters.categoryId,
  );

  if (isLoading && transactionData === null) {
    return (
      <section>
        <h1>Transactions</h1>
        <p>Loading transactions...</p>
      </section>
    );
  }

  return (
    <section>
      <div>
        <h1>Transactions</h1>
        <p>Manage your income and expenses.</p>
      </div>

      {errorMessage && <p className="form-error">{errorMessage}</p>}

      {refreshWarning && (
        <p className="form-error" role="status">
          {refreshWarning}
        </p>
      )}

      <CategoryRefreshNotice />

      <section>
        <div>
          <h2 ref={formHeadingRef} tabIndex={-1}>
            {editingTransactionId !== null
              ? "Edit Transaction"
              : "Add Transaction"}
          </h2>

          <p>
            {editingTransactionId !== null
              ? "Update the selected transaction."
              : "Record a new income or expense transaction."}
          </p>
        </div>

        <form ref={formRef} onSubmit={handleSubmit} className="transaction-form">
          <CategorySelect
            id="transaction-category"
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
            <label htmlFor="transaction-type">Type</label>

            <select
              id="transaction-type"
              value={form.type}
              aria-invalid={Boolean(validationErrors.type)}
              aria-describedby={
                validationErrors.type ? "transaction-type-error" : undefined
              }
              onChange={(event) => updateField("type", event.target.value as TransactionType)}
            >
              <option value="EXPENSE">Expense</option>

              <option value="INCOME">Income</option>
            </select>

            {validationErrors.type && (
              <p id="transaction-type-error" className="field-error">
                {validationErrors.type}
              </p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-amount">Amount</label>

            <input
              id="transaction-amount"
              type="number"
              min="0.01"
              step="0.01"
              value={form.amount}
              aria-invalid={Boolean(validationErrors.amount)}
              aria-describedby={
                validationErrors.amount ? "transaction-amount-error" : undefined
              }
              onChange={(event) => updateField("amount", event.target.value)}
              required
            />

            {validationErrors.amount && (
              <p id="transaction-amount-error" className="field-error">
                {validationErrors.amount}
              </p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-description">Description</label>

            <input
              id="transaction-description"
              type="text"
              maxLength={255}
              value={form.description}
              aria-invalid={Boolean(validationErrors.description)}
              aria-describedby={
                validationErrors.description
                  ? "transaction-description-error"
                  : undefined
              }
              onChange={(event) => updateField("description", event.target.value)}
              required
            />

            {validationErrors.description && (
              <p id="transaction-description-error" className="field-error">
                {validationErrors.description}
              </p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-date">Date</label>

            <input
              id="transaction-date"
              type="date"
              value={form.transactionDate}
              aria-invalid={Boolean(validationErrors.transactionDate)}
              aria-describedby={
                validationErrors.transactionDate
                  ? "transaction-date-error"
                  : undefined
              }
              onChange={(event) => updateField("transactionDate", event.target.value)}
              required
            />

            {validationErrors.transactionDate && (
              <p id="transaction-date-error" className="field-error">
                {validationErrors.transactionDate}
              </p>
            )}
          </div>

          {formErrorMessage && (
            <p
              ref={formErrorRef}
              id="transaction-form-error"
              className="form-error"
              role="alert"
              tabIndex={-1}
            >
              {formErrorMessage}
            </p>
          )}

          <div>
            <button
              type="submit"
              className="button button--primary"
              disabled={isSubmitting}
            >
              {isSubmitting
                ? "Saving..."
                : editingTransactionId !== null
                  ? "Update Transaction"
                  : "Add Transaction"}
            </button>

            {editingTransactionId !== null && (
              <button
                type="button"
                className="button button--secondary"
                onClick={handleCancelEdit}
              >
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>

      <CategoryManager onChange={(change) => void handleCategoryManaged(change)} />

      <section>
        <div>
          <h2>Filter Transactions</h2>
          <p>Search, filter, and sort your financial activity.</p>
        </div>

        <form onSubmit={handleFilterSubmit} className="transaction-filters">
          <div className="form-field">
            <label htmlFor="transaction-search">Search</label>

            <input
              id="transaction-search"
              type="search"
              placeholder="Search description"
              value={filters.search}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  search: event.target.value,
                })
              }
            />
          </div>

          <div className="form-field">
            <label htmlFor="filter-type">Type</label>

            <select
              id="filter-type"
              value={filters.type}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  type: event.target.value as "" | TransactionType,
                })
              }
            >
              <option value="">All</option>
              <option value="INCOME">Income</option>
              <option value="EXPENSE">Expense</option>
            </select>
          </div>

          <div className="form-field">
            <label htmlFor="filter-category">Filter by category</label>

            <div className="category-select__control">
              {selectedFilterCategory && <CategoryIcon iconKey={selectedFilterCategory.iconKey} />}

              <select
                id="filter-category"
                value={selectedFilterCategory ? filters.categoryId : ""}
                onChange={(event) =>
                  setFilters({
                    ...filters,
                    categoryId: event.target.value,
                  })
                }
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

          <div className="form-field">
            <label htmlFor="filter-start-date">Start Date</label>

            <input
              id="filter-start-date"
              type="date"
              value={filters.startDate}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  startDate: event.target.value,
                })
              }
            />
          </div>

          <div className="form-field">
            <label htmlFor="filter-end-date">End Date</label>

            <input
              id="filter-end-date"
              type="date"
              value={filters.endDate}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  endDate: event.target.value,
                })
              }
            />
          </div>

          <div className="form-field">
            <label htmlFor="filter-min-amount">Minimum Amount</label>

            <input
              id="filter-min-amount"
              type="number"
              min="0"
              step="0.01"
              value={filters.minAmount}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  minAmount: event.target.value,
                })
              }
            />
          </div>

          <div className="form-field">
            <label htmlFor="filter-max-amount">Maximum Amount</label>

            <input
              id="filter-max-amount"
              type="number"
              min="0"
              step="0.01"
              value={filters.maxAmount}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  maxAmount: event.target.value,
                })
              }
            />
          </div>

          <div className="form-field">
            <label htmlFor="filter-sort-by">Sort By</label>

            <select
              id="filter-sort-by"
              value={filters.sortBy}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  sortBy: event.target.value as TransactionSortField,
                })
              }
            >
              <option value="transactionDate">Transaction Date</option>

              <option value="amount">Amount</option>

              <option value="createdAt">Created Date</option>
            </select>
          </div>

          <div className="form-field">
            <label htmlFor="filter-sort-direction">Direction</label>

            <select
              id="filter-sort-direction"
              value={filters.sortDirection}
              onChange={(event) =>
                setFilters({
                  ...filters,
                  sortDirection: event.target.value as SortDirection,
                })
              }
            >
              <option value="desc">Descending</option>

              <option value="asc">Ascending</option>
            </select>
          </div>

          <div>
            <button type="submit" className="button button--primary">
              Apply Filters
            </button>

            <button
              type="button"
              className="button button--secondary"
              onClick={() => {
                void handleResetFilters();
              }}
            >
              Reset
            </button>
          </div>
        </form>
      </section>

      <section>
        <div>
          <h2>Transaction History</h2>

          <p>
            {transactionData
              ? `${transactionData.totalElements} total transactions`
              : "0 total transactions"}
          </p>
        </div>

        {transactionData?.transactions.length === 0 ? (
          <p className="empty-state">No transactions found.</p>
        ) : (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Category</th>
                  <th>Type</th>
                  <th>Amount</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {transactionData?.transactions.map((transaction) => (
                  <tr
                    key={transaction.id}
                    data-testid={`transaction-row-${transaction.id}`}
                  >
                    <td>{formatDate(transaction.transactionDate, user?.preferences?.dateFormat)}</td>

                    <td>{transaction.description}</td>

                    <td>
                      <CategoryLabel name={transaction.categoryName} iconKey={transaction.categoryIconKey} />
                    </td>

                    <td>
                      {transaction.type === "INCOME" ? "Income" : "Expense"}
                    </td>

                    <td>{formatCurrency(transaction.amount)}</td>

                    <td>
                      <button
                        type="button"
                        className="button button--secondary"
                        data-transaction-edit-id={transaction.id}
                        onClick={() => handleEdit(transaction)}
                      >
                        Edit
                      </button>

                      <button
                        type="button"
                        className="button button--secondary"
                        onClick={() => {
                          void handleDelete(transaction);
                        }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {transactionData && transactionData.totalPages > 0 && (
          <div className="pagination">
            <button
              type="button"
              className="button button--secondary"
              disabled={transactionData.page === 0 || isLoading}
              onClick={() => {
                void handlePageChange(transactionData.page - 1);
              }}
            >
              Previous
            </button>

            <span className="pagination__status">
              Page {transactionData.page + 1} of {transactionData.totalPages}
            </span>

            <button
              type="button"
              className="button button--secondary"
              disabled={
                transactionData.page >= transactionData.totalPages - 1 ||
                isLoading
              }
              onClick={() => {
                void handlePageChange(transactionData.page + 1);
              }}
            >
              Next
            </button>
          </div>
        )}
      </section>
    </section>
  );
}

export default TransactionPage;
