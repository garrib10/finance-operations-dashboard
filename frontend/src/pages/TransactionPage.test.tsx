import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as categoryService from "../services/categoryService";
import { ApiError } from "../services/api";
import * as transactionService from "../services/transactionService";
import type { CategoryResponse } from "../types/category";
import type {
  PagedTransactionResponse,
  TransactionResponse,
} from "../types/transaction";
import TransactionPage from "./TransactionPage";

vi.mock("../services/categoryService");
vi.mock("../services/transactionService");

const scrollIntoViewMock = vi.fn();

const categories: CategoryResponse[] = [
  {
    id: 1,
    name: "Groceries",
    budgetEnabled: true,
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-01T10:00:00",
  },
];

const transaction: TransactionResponse = {
  id: 1,
  categoryId: 1,
  categoryName: "Groceries",
  type: "EXPENSE",
  amount: 75.5,
  description: "Food Lion",
  transactionDate: "2026-09-10",
  createdAt: "2026-09-10T12:00:00",
  updatedAt: "2026-09-10T12:00:00",
};

function createPagedResponse(
  transactions: TransactionResponse[] = [transaction],
  page = 0,
  totalPages = 1,
): PagedTransactionResponse {
  return {
    transactions,
    page,
    size: 10,
    totalElements: transactions.length,
    totalPages,
  };
}

async function completeTransactionForm(
  description = "Grocery Store",
): Promise<void> {
  const user = userEvent.setup();

  await user.selectOptions(screen.getByLabelText("Category"), "1");
  await user.type(screen.getByLabelText("Amount"), "75.50");
  await user.type(screen.getByLabelText("Description"), description);
  await user.type(screen.getByLabelText("Date"), "2026-09-10");
}

describe("TransactionPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
      configurable: true,
      value: scrollIntoViewMock,
    });

    vi.mocked(categoryService.getCategories).mockResolvedValue(categories);

    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse(),
    );

    vi.mocked(transactionService.createTransaction).mockResolvedValue(
      transaction,
    );

    vi.mocked(transactionService.updateTransaction).mockResolvedValue(
      transaction,
    );

    vi.mocked(transactionService.deleteTransaction).mockResolvedValue(
      undefined,
    );
  });

  it("shows the loading state", () => {
    vi.mocked(transactionService.getTransactions).mockImplementation(
      () => new Promise(() => {}),
    );

    vi.mocked(categoryService.getCategories).mockImplementation(
      () => new Promise(() => {}),
    );

    render(<TransactionPage />);

    expect(screen.getByText("Loading transactions...")).toBeInTheDocument();
  });

  it("renders loaded transactions", async () => {
    render(<TransactionPage />);

    expect(await screen.findByText("Food Lion")).toBeInTheDocument();

    const transactionRow = screen.getByTestId("transaction-row-1");

    expect(transactionRow).toBeInTheDocument();
    expect(transactionRow).toHaveTextContent("Food Lion");
    expect(transactionRow).toHaveTextContent("Groceries");
    expect(transactionRow).toHaveTextContent("$75.50");
    expect(screen.getAllByText("Groceries")).toHaveLength(2);
    expect(screen.getByText("$75.50")).toBeInTheDocument();
    expect(screen.getByText("Sep 10, 2026")).toBeInTheDocument();
  });

  it("renders the empty transaction state", async () => {
    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([], 0, 0),
    );

    render(<TransactionPage />);

    expect(
      await screen.findByText("No transactions found."),
    ).toBeInTheDocument();
  });

  it("creates a transaction", async () => {
    const user = userEvent.setup();

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await completeTransactionForm();

    await user.click(
      screen.getByRole("button", {
        name: "Add Transaction",
      }),
    );

    await waitFor(() => {
      expect(transactionService.createTransaction).toHaveBeenCalledWith({
        categoryId: 1,
        type: "EXPENSE",
        amount: 75.5,
        description: "Grocery Store",
        transactionDate: "2026-09-10",
      });
    });
  });

  it("loads a transaction into edit mode", async () => {
    const user = userEvent.setup();

    render(<TransactionPage />);

    await screen.findByText("Food Lion");

    const editButton = screen.getByRole("button", { name: "Edit" });

    expect(editButton).toHaveAttribute("data-transaction-edit-id", "1");

    await user.click(editButton);

    const formHeading = screen.getByRole("heading", {
      name: "Edit Transaction",
    });

    expect(formHeading).toBeInTheDocument();
    expect(formHeading).toHaveAttribute("tabindex", "-1");

    await waitFor(() => {
      expect(formHeading).toHaveFocus();
    });

    expect(scrollIntoViewMock).toHaveBeenCalledWith({
      behavior: "smooth",
      block: "start",
    });

    expect(screen.getByDisplayValue("Food Lion")).toBeInTheDocument();

    expect(
      screen.getByRole("button", {
        name: "Update Transaction",
      }),
    ).toBeInTheDocument();
  });

  it("returns focus to the originating Edit button after cancelling", async () => {
    const user = userEvent.setup();

    render(<TransactionPage />);

    await screen.findByText("Food Lion");

    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(
      screen.getByRole("heading", { name: "Add Transaction" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Edit" })).toHaveFocus();
    });
  });

  it("updates a transaction after editing", async () => {
    const user = userEvent.setup();

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Edit" }));

    const descriptionInput = screen.getByLabelText("Description");

    await user.clear(descriptionInput);
    await user.type(descriptionInput, "Updated Grocery Store");

    await user.click(
      screen.getByRole("button", {
        name: "Update Transaction",
      }),
    );

    await waitFor(() => {
      expect(transactionService.updateTransaction).toHaveBeenCalledWith(1, {
        categoryId: 1,
        type: "EXPENSE",
        amount: 75.5,
        description: "Updated Grocery Store",
        transactionDate: "2026-09-10",
      });
    });
  });

  it("associates validation errors with fields and focuses the first invalid field", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new ApiError("Validation failed.", 400, {
        categoryId: "Category is required.",
        amount: "Amount must be greater than zero.",
        description: "Description is required.",
        transactionDate: "Transaction date is required.",
      }),
    );

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await completeTransactionForm();

    await user.click(
      screen.getByRole("button", {
        name: "Add Transaction",
      }),
    );

    const categoryInput = screen.getByLabelText("Category");
    const typeInput = screen.getByLabelText("Type", {
      selector: "#transaction-type",
    });
    const amountInput = screen.getByLabelText("Amount");
    const descriptionInput = screen.getByLabelText("Description");
    const dateInput = screen.getByLabelText("Date");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Validation failed.",
    );

    expect(categoryInput).toHaveAttribute("aria-invalid", "true");
    expect(categoryInput).toHaveAttribute(
      "aria-describedby",
      "transaction-category-error",
    );
    expect(categoryInput).toHaveAccessibleDescription("Category is required.");

    expect(amountInput).toHaveAttribute("aria-invalid", "true");
    expect(amountInput).toHaveAccessibleDescription(
      "Amount must be greater than zero.",
    );

    expect(descriptionInput).toHaveAttribute("aria-invalid", "true");
    expect(descriptionInput).toHaveAccessibleDescription(
      "Description is required.",
    );

    expect(dateInput).toHaveAttribute("aria-invalid", "true");
    expect(dateInput).toHaveAccessibleDescription(
      "Transaction date is required.",
    );

    expect(typeInput).not.toHaveAttribute("aria-invalid", "true");

    await waitFor(() => {
      expect(categoryInput).toHaveFocus();
    });
  });

  it("preserves the create form and skips refresh when creation fails", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new Error("Request failed"),
    );

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await completeTransactionForm();

    await user.click(
      screen.getByRole("button", {
        name: "Add Transaction",
      }),
    );

    const formError = await screen.findByRole("alert");

    expect(formError).toHaveTextContent(
      "Unable to create the transaction. Please try again.",
    );
    expect(formError).toHaveAttribute("id", "transaction-form-error");

    await waitFor(() => {
      expect(formError).toHaveFocus();
    });

    expect(transactionService.getTransactions).toHaveBeenCalledOnce();
    expect(screen.getByLabelText("Category")).toHaveValue("1");
    expect(screen.getByLabelText("Amount")).toHaveValue(75.5);
    expect(screen.getByLabelText("Description")).toHaveValue("Grocery Store");
  });

  it("preserves edit mode and skips refresh when updating fails", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.updateTransaction).mockRejectedValue(
      new Error("Request failed"),
    );

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Edit" }));

    const descriptionInput = screen.getByLabelText("Description");
    await user.clear(descriptionInput);
    await user.type(descriptionInput, "Updated Grocery Store");

    await user.click(
      screen.getByRole("button", {
        name: "Update Transaction",
      }),
    );

    expect(
      await screen.findByText(
        "Unable to update the transaction. Please try again.",
      ),
    ).toBeInTheDocument();

    expect(transactionService.getTransactions).toHaveBeenCalledOnce();
    expect(
      screen.getByRole("heading", { name: "Edit Transaction" }),
    ).toBeInTheDocument();
    expect(descriptionInput).toHaveValue("Updated Grocery Store");
  });

  it("reports a refresh warning after a successful create", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions)
      .mockResolvedValueOnce(createPagedResponse())
      .mockRejectedValueOnce(new Error("Refresh failed"));

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await completeTransactionForm();

    await user.click(
      screen.getByRole("button", {
        name: "Add Transaction",
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Transaction saved, but the transaction list could not be refreshed.",
    );

    expect(transactionService.createTransaction).toHaveBeenCalledOnce();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Food Lion")).toBeInTheDocument();
    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(screen.getByLabelText("Amount")).toHaveValue(null);
    expect(screen.getByLabelText("Description")).toHaveValue("");
    expect(
      screen.queryByText(/Unable to create the transaction/i),
    ).not.toBeInTheDocument();
  });

  it("exits edit mode and reports a refresh warning after a successful update", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions)
      .mockResolvedValueOnce(createPagedResponse())
      .mockRejectedValueOnce(new Error("Refresh failed"));

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Edit" }));

    const descriptionInput = screen.getByLabelText("Description");
    await user.clear(descriptionInput);
    await user.type(descriptionInput, "Updated Grocery Store");

    await user.click(
      screen.getByRole("button", {
        name: "Update Transaction",
      }),
    );

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Transaction saved, but the transaction list could not be refreshed.",
    );

    expect(transactionService.updateTransaction).toHaveBeenCalledOnce();
    expect(
      screen.getByRole("heading", { name: "Add Transaction" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Food Lion")).toBeInTheDocument();
  });

  it("deletes a transaction after confirmation", async () => {
    const user = userEvent.setup();

    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(transactionService.deleteTransaction).toHaveBeenCalledWith(1);
    });

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenCalledTimes(2);
    });
  });

  it("does not delete a transaction when confirmation is cancelled", async () => {
    const user = userEvent.setup();

    vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(transactionService.deleteTransaction).not.toHaveBeenCalled();
    expect(screen.getByText("Food Lion")).toBeInTheDocument();
  });

  it("applies transaction filters", async () => {
    const user = userEvent.setup();

    render(<TransactionPage />);

    await screen.findByText("Food Lion");
    await user.type(screen.getByLabelText("Search"), "Food");

    await user.selectOptions(
      screen.getByLabelText("Type", {
        selector: "#filter-type",
      }),
      "EXPENSE",
    );

    await user.click(
      screen.getByRole("button", {
        name: "Apply Filters",
      }),
    );

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          size: 10,
          search: "Food",
          type: "EXPENSE",
          sortBy: "transactionDate",
          sortDirection: "desc",
        }),
      );
    });
  });

  it("loads the next page while preserving filters", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([transaction], 0, 2),
    );

    render(<TransactionPage />);

    await screen.findByText("Food Lion");

    await user.click(
      screen.getByRole("button", {
        name: "Next",
      }),
    );

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 1,
          size: 10,
          sortBy: "transactionDate",
          sortDirection: "desc",
        }),
      );
    });
  });
});
