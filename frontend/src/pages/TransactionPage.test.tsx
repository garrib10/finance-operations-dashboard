import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TransactionPage from "./TransactionPage";
import * as categoryService from "../services/categoryService";
import * as transactionService from "../services/transactionService";
import type { CategoryResponse } from "../types/category";
import type {
  PagedTransactionResponse,
  TransactionResponse,
} from "../types/transaction";

vi.mock("../services/categoryService");
vi.mock("../services/transactionService");

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

describe("TransactionPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

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

    await user.selectOptions(screen.getByLabelText("Category"), "1");

    await user.type(screen.getByLabelText("Amount"), "75.50");

    await user.type(screen.getByLabelText("Description"), "Grocery Store");

    await user.type(screen.getByLabelText("Date"), "2026-09-10");

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

    await user.click(
      screen.getByRole("button", {
        name: "Edit",
      }),
    );

    expect(
      screen.getByRole("heading", {
        name: "Edit Transaction",
      }),
    ).toBeInTheDocument();

    expect(screen.getByDisplayValue("Food Lion")).toBeInTheDocument();

    expect(
      screen.getByRole("button", {
        name: "Update Transaction",
      }),
    ).toBeInTheDocument();
  });

  it("updates a transaction after editing", async () => {
    const user = userEvent.setup();

    render(<TransactionPage />);

    await screen.findByText("Food Lion");

    await user.click(
      screen.getByRole("button", {
        name: "Edit",
      }),
    );

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

  it("deletes a transaction after confirmation", async () => {
    const user = userEvent.setup();

    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<TransactionPage />);

    await screen.findByText("Food Lion");

    await user.click(
      screen.getByRole("button", {
        name: "Delete",
      }),
    );

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

    await user.click(
      screen.getByRole("button", {
        name: "Delete",
      }),
    );

    expect(transactionService.deleteTransaction).not.toHaveBeenCalled();
    expect(screen.getByText("Food Lion")).toBeInTheDocument();
  });

  it("shows an error when creating a transaction fails", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new Error("Request failed"),
    );

    render(<TransactionPage />);

    await screen.findByText("Food Lion");

    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.type(screen.getByLabelText("Amount"), "75.50");
    await user.type(screen.getByLabelText("Description"), "Grocery Store");
    await user.type(screen.getByLabelText("Date"), "2026-09-10");

    await user.click(
      screen.getByRole("button", {
        name: "Add Transaction",
      }),
    );

    expect(
      await screen.findByText("Unable to save transaction. Please try again."),
    ).toBeInTheDocument();
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
