import { useEffect, useState } from "react";
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

  const [validationErrors, setValidationErrors] = useState<
    Record<string, string>
  >({});

  function buildFilters(page = 0): TransactionFilterRequest {
    return {
      page,
      size: 10,
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
      size: 10,
      sortBy: "transactionDate",
      sortDirection: "desc",
    },
  ): Promise<void> {
    const response = await getTransactions(transactionFilters);

    setTransactionData(response);
  }

  useEffect(() => {
    async function loadTransactionPage(): Promise<void> {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const [transactionsResponse, categoriesResponse] = await Promise.all([
          getTransactions({
            page: 0,
            size: 10,
            sortBy: "transactionDate",
            sortDirection: "desc",
          }),
          getCategories(),
        ]);

        setTransactionData(transactionsResponse);
        setCategories(categoriesResponse);
      } catch (error) {
        if (error instanceof ApiError) {
          setErrorMessage(error.message);
        } else {
          setErrorMessage("Unable to load transaction data. Please try again.");
        }
      } finally {
        setIsLoading(false);
      }
    }

    void loadTransactionPage();
  }, []);

  function resetForm(): void {
    setForm(initialFormState);
    setEditingTransactionId(null);
    setFormErrorMessage("");
    setValidationErrors({});
  }

  function handleEdit(transaction: TransactionResponse): void {
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

    setIsSubmitting(true);
    setFormErrorMessage("");
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

      resetForm();

      await loadTransactions(buildFilters(0));
    } catch (error) {
      if (error instanceof ApiError) {
        setFormErrorMessage(error.message);

        if (error.validationErrors) {
          setValidationErrors(error.validationErrors);
        }
      } else {
        setFormErrorMessage("Unable to save transaction. Please try again.");
      }
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
        size: 10,
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

      <section>
        <div>
          <h2>
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
              id="transaction-category"
              value={form.categoryId}
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
              <p className="field-error">{validationErrors.categoryId}</p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-type">Type</label>

            <select
              id="transaction-type"
              value={form.type}
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
              <p className="field-error">{validationErrors.type}</p>
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
              onChange={(event) =>
                setForm({
                  ...form,
                  amount: event.target.value,
                })
              }
              required
            />

            {validationErrors.amount && (
              <p className="field-error">{validationErrors.amount}</p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-description">Description</label>

            <input
              id="transaction-description"
              type="text"
              maxLength={255}
              value={form.description}
              onChange={(event) =>
                setForm({
                  ...form,
                  description: event.target.value,
                })
              }
              required
            />

            {validationErrors.description && (
              <p className="field-error">{validationErrors.description}</p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="transaction-date">Date</label>

            <input
              id="transaction-date"
              type="date"
              value={form.transactionDate}
              onChange={(event) =>
                setForm({
                  ...form,
                  transactionDate: event.target.value,
                })
              }
              required
            />

            {validationErrors.transactionDate && (
              <p className="field-error">{validationErrors.transactionDate}</p>
            )}
          </div>

          {formErrorMessage && <p className="form-error">{formErrorMessage}</p>}

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
                onClick={resetForm}
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
                  <tr key={transaction.id}>
                    <td>{formatDate(transaction.transactionDate)}</td>

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
