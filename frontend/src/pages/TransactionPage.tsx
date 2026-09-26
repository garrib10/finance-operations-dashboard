import { useAuth } from "../context/AuthContext";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import type { SubmitEvent as ReactSubmitEvent } from "react";
import { ApiError } from "../services/api";
import { getCategories } from "../services/categoryService";
import {
  createTransaction,
  deleteTransaction,
  getTransactions,
  updateTransaction,
} from "../services/transactionService";
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
import { formatCurrency, formatDate } from "../utils/formatters";

interface TransactionFormState {
  categoryId: string;
  type: TransactionType;
  amount: string;
  description: string;
  transactionDate: string;
}

interface TransactionFilterState {
  search: string;
  type: "" | TransactionType;
  startDate: string;
  endDate: string;
  minAmount: string;
  maxAmount: string;
  sortBy: TransactionSortField;
  sortDirection: SortDirection;
}

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
  startDate: "",
  endDate: "",
  minAmount: "",
  maxAmount: "",
  sortBy: "transactionDate",
  sortDirection: "desc",
};

function TransactionPage() {
  const { user } = useAuth();
  const pageSize = user?.preferences?.transactionPageSize ?? 10;
  const requestSequence = useRef(0);
  const [transactionData, setTransactionData] =
    useState<PagedTransactionResponse | null>(null);

  const [categories, setCategories] = useState<CategoryResponse[]>([]);

  const [form, setForm] = useState<TransactionFormState>(initialFormState);

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

  const categoryInputRef = useRef<HTMLSelectElement>(null);
  const typeInputRef = useRef<HTMLSelectElement>(null);
  const amountInputRef = useRef<HTMLInputElement>(null);
  const descriptionInputRef = useRef<HTMLInputElement>(null);
  const dateInputRef = useRef<HTMLInputElement>(null);
  const formErrorRef = useRef<HTMLParagraphElement>(null);
  const formHeadingRef = useRef<HTMLHeadingElement>(null);
  const editTriggerIdRef = useRef<number | null>(null);
  const pendingFocusTriggerIdRef = useRef<number | null>(null);

  useEffect(() => {
    if (validationErrors.categoryId) {
      categoryInputRef.current?.focus();
      return;
    }

    if (validationErrors.type) {
      typeInputRef.current?.focus();
      return;
    }

    if (validationErrors.amount) {
      amountInputRef.current?.focus();
      return;
    }

    if (validationErrors.description) {
      descriptionInputRef.current?.focus();
      return;
    }

    if (validationErrors.transactionDate) {
      dateInputRef.current?.focus();
      return;
    }

    if (formErrorMessage) {
      formErrorRef.current?.focus();
    }
  }, [formErrorMessage, validationErrors]);

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

  function buildFilters(page = 0): TransactionFilterRequest {
    return {
      page,
      size: pageSize,
      sortBy: filters.sortBy,
      sortDirection: filters.sortDirection,
      ...(filters.search.trim() && {
        search: filters.search.trim(),
      }),
      ...(filters.type && {
        type: filters.type,
      }),
      ...(filters.startDate && {
        startDate: filters.startDate,
      }),
      ...(filters.endDate && {
        endDate: filters.endDate,
      }),
      ...(filters.minAmount && {
        minAmount: Number(filters.minAmount),
      }),
      ...(filters.maxAmount && {
        maxAmount: Number(filters.maxAmount),
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

        const [transactionsResponse, categoriesResponse] = await Promise.all([
          getTransactions({ ...filtersForSizeChange(), size: pageSize }),
          getCategories(),
        ]);

        if (!active || sequence !== requestSequence.current) return;
        setTransactionData(transactionsResponse);
        setCategories(categoriesResponse);
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

  function resetForm(): void {
    setForm(initialFormState);
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

    setFormErrorMessage("");
    setValidationErrors({});
  }

  async function handleSubmit(
    event: ReactSubmitEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();

    const isEditing = editingTransactionId !== null;

    setIsSubmitting(true);
    setFormErrorMessage("");
    setRefreshWarning("");
    setValidationErrors({});

    const request: CreateTransactionRequest = {
      categoryId: Number(form.categoryId),
      type: form.type,
      amount: Number(form.amount),
      description: form.description.trim(),
      transactionDate: form.transactionDate,
    };

    try {
      if (editingTransactionId !== null) {
        await updateTransaction(editingTransactionId, request);
      } else {
        await createTransaction(request);
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setFormErrorMessage(error.message);

        if (error.validationErrors) {
          setValidationErrors(error.validationErrors);
        }
      } else {
        setFormErrorMessage(
          isEditing
            ? "Unable to update the transaction. Please try again."
            : "Unable to create the transaction. Please try again.",
        );
      }

      setIsSubmitting(false);
      return;
    }

    resetForm();

    try {
      await loadTransactions(buildFilters(0));
    } catch {
      setRefreshWarning(
        "Transaction saved, but the transaction list could not be refreshed. Reload the page to see the latest data.",
      );
    } finally {
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

        <form onSubmit={handleSubmit} className="transaction-form">
          <div className="form-field">
            <label htmlFor="transaction-category">Category</label>

            <select
              ref={categoryInputRef}
              id="transaction-category"
              value={form.categoryId}
              aria-invalid={Boolean(validationErrors.categoryId)}
              aria-describedby={
                validationErrors.categoryId
                  ? "transaction-category-error"
                  : undefined
              }
              onChange={(event) =>
                setForm({
                  ...form,
                  categoryId: event.target.value,
                })
              }
              required
            >
              <option value="">Select a category</option>

              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>

            {validationErrors.categoryId && (
              <p id="transaction-category-error" className="field-error">
                {validationErrors.categoryId}
              </p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-type">Type</label>

            <select
              ref={typeInputRef}
              id="transaction-type"
              value={form.type}
              aria-invalid={Boolean(validationErrors.type)}
              aria-describedby={
                validationErrors.type ? "transaction-type-error" : undefined
              }
              onChange={(event) =>
                setForm({
                  ...form,
                  type: event.target.value as TransactionType,
                })
              }
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
              ref={amountInputRef}
              id="transaction-amount"
              type="number"
              min="0.01"
              step="0.01"
              value={form.amount}
              aria-invalid={Boolean(validationErrors.amount)}
              aria-describedby={
                validationErrors.amount ? "transaction-amount-error" : undefined
              }
              onChange={(event) =>
                setForm({
                  ...form,
                  amount: event.target.value,
                })
              }
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
              ref={descriptionInputRef}
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
              onChange={(event) =>
                setForm({
                  ...form,
                  description: event.target.value,
                })
              }
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
              ref={dateInputRef}
              id="transaction-date"
              type="date"
              value={form.transactionDate}
              aria-invalid={Boolean(validationErrors.transactionDate)}
              aria-describedby={
                validationErrors.transactionDate
                  ? "transaction-date-error"
                  : undefined
              }
              onChange={(event) =>
                setForm({
                  ...form,
                  transactionDate: event.target.value,
                })
              }
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

                    <td>{transaction.categoryName}</td>

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
