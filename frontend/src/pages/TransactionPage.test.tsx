vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
import { useAuth } from "../context/AuthContext";
import { accountContext, accountUser, deferred } from "../test/accountFixtures";
import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as categoryService from "../services/categoryService";
import { ApiError } from "../services/api";
import * as transactionService from "../services/transactionService";
import type { CategoryResponse } from "../types/category";
import type {
  PagedTransactionResponse,
  TransactionResponse,
} from "../types/transaction";
import TransactionPage from "./TransactionPage";
import { toDateInputValue } from "../utils/formatters";
import { CategoryProvider } from "../context/CategoryProvider";

vi.mock("../services/categoryService");
vi.mock("../services/transactionService");

const scrollIntoViewMock = vi.fn();

const categories: CategoryResponse[] = [
  {
    id: 1,
    name: "Groceries",
    budgetEnabled: true,
    builtIn: true,
    iconKey: "shopping-cart",
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-01T10:00:00",
  },
];

const transaction: TransactionResponse = {
  id: 1,
  categoryId: 1,
  categoryName: "Groceries",
  categoryIconKey: "shopping-cart",
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
  await user.clear(screen.getByLabelText("Date")); // The form starts on today.
  await user.type(screen.getByLabelText("Date"), "2026-09-10");
}

// Full user-event form flows are slow when the machine is busy (dev server, browser).
vi.setConfig({ testTimeout: 20_000 });

describe.each([10, 25, 50] as const)("TransactionPage with page size %s", (pageSize) => {
  afterEach(() => {
    for (const [request] of vi.mocked(transactionService.getTransactions).mock.calls) {
      expect(request?.size).toBe(pageSize);
    }
  });
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue(accountContext({ ...accountUser, preferences: { dateFormat: "MEDIUM", transactionPageSize: pageSize } }));
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

  it("keeps the page, form, and filters while transactions load, with loading in the history area", async () => {
    vi.mocked(transactionService.getTransactions).mockImplementation(
      () => new Promise(() => {}),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    const loading = await screen.findByText("Loading transactions…");
    expect(loading).toHaveAttribute("role", "status");
    expect(screen.getAllByRole("heading", { level: 1 }).map((heading) => heading.textContent)).toEqual(["Transactions"]);
    expect(screen.getByRole("heading", { name: "Filter Transactions" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Transaction History" })).toBeInTheDocument();
    expect(screen.getByLabelText("Search")).toBeEnabled();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText("No transactions found.")).not.toBeInTheDocument();
  });

  it("renders loaded transactions", async () => {
    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    expect(await screen.findByText("Food Lion")).toBeInTheDocument();

    const transactionRow = screen.getByTestId("transaction-row-1");

    expect(transactionRow).toBeInTheDocument();
    expect(transactionRow).toHaveTextContent("Food Lion");
    expect(transactionRow).toHaveTextContent("Groceries");
    expect(transactionRow).toHaveTextContent("$75.50");
    // The form option, the category filter option, and the row's category label.
    expect(screen.getAllByText("Groceries")).toHaveLength(3);
    expect(screen.getByText("$75.50")).toBeInTheDocument();
    expect(screen.getByText("Sep 10, 2026")).toBeInTheDocument();
  });

  it("renders income transactions", async () => {
    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([
        {
          ...transaction,
          id: 2,
          type: "INCOME",
          description: "Paycheck",
        },
      ]),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    const transactionRow = await screen.findByTestId("transaction-row-2");

    expect(transactionRow).toHaveTextContent("Paycheck");
    expect(transactionRow).toHaveTextContent("Income");
    expect(transactionRow.querySelector(".transaction-type-icon--income")).toBeInTheDocument();
    expect(transactionRow.querySelector(".icon-label .lucide-calendar-days")).toBeInTheDocument();
  });

  it("shows the Amount icon once the user starts typing, and none for Description or Date", async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
    await screen.findByTestId("transaction-row-1");
    const iconFor = (label: string) => screen.getByLabelText(label).parentElement?.querySelector("svg");

    expect(iconFor("Amount")).toBeNull();
    await user.type(screen.getByLabelText("Amount"), "5");
    await user.type(screen.getByLabelText("Description"), "C");
    expect(iconFor("Amount")).toHaveClass("lucide-dollar-sign");
    // Description and Date never have one: the label (or the calendar button) says enough.
    expect(iconFor("Description")).toBeNull();
    expect(iconFor("Date")).toBeNull();
  });

  it("shows filter icons that follow each filter's value", async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
    await screen.findByTestId("transaction-row-1");
    const iconFor = (label: string) => screen.getByLabelText(label).parentElement?.querySelector("svg");

    // Date inputs already have the browser's calendar button, so no second icon.
    expect(iconFor("Start Date")).toBeNull();
    expect(iconFor("End Date")).toBeNull();

    // Search and amounts wait for input, like the form's Amount and Description.
    expect(iconFor("Search")).toBeNull();
    expect(iconFor("Minimum Amount")).toBeNull();
    expect(iconFor("Maximum Amount")).toBeNull();
    await user.type(screen.getByLabelText("Search"), "f");
    await user.type(screen.getByLabelText("Minimum Amount"), "1");
    await user.type(screen.getByLabelText("Maximum Amount"), "9");
    expect(iconFor("Search")).toHaveClass("lucide-search");
    expect(iconFor("Minimum Amount")).toHaveClass("lucide-dollar-sign");
    expect(iconFor("Maximum Amount")).toHaveClass("lucide-dollar-sign");

    const typeFilter = screen.getByLabelText("Type", { selector: "#filter-type" });
    expect(typeFilter.parentElement?.querySelector("svg")).toHaveClass("category-icon--placeholder");
    await user.selectOptions(typeFilter, "INCOME");
    expect(typeFilter.parentElement?.querySelector("svg")).toHaveClass("transaction-type-icon--income");

    const sortBy = screen.getByLabelText("Sort By");
    expect(iconFor("Sort By")).toHaveClass("lucide-calendar-days");
    await user.selectOptions(sortBy, "amount");
    expect(iconFor("Sort By")).toHaveClass("lucide-dollar-sign");
    await user.selectOptions(sortBy, "createdAt");
    expect(iconFor("Sort By")).toHaveClass("lucide-calendar-plus");

    // Direction's arrow flips with the order.
    expect(iconFor("Direction")).toHaveClass("lucide-arrow-down-wide-narrow");
    await user.selectOptions(screen.getByLabelText("Direction"), "asc");
    expect(iconFor("Direction")).toHaveClass("lucide-arrow-up-narrow-wide");
  });

  it("shows an icon for the chosen transaction type", async () => {
    const user = userEvent.setup();
    const { container } = render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
    await screen.findByTestId("transaction-row-1");
    const typeIcon = () => container.querySelector(".icon-field .transaction-type-icon");

    expect(typeIcon()).toHaveClass("transaction-type-icon--expense");
    await user.selectOptions(screen.getByLabelText("Type", { selector: "#transaction-type" }), "INCOME");
    expect(typeIcon()).toHaveClass("transaction-type-icon--income");
  });

  it("renders the empty transaction state", async () => {
    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([], 0, 0),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    expect(
      await screen.findByText("No transactions found."),
    ).toBeInTheDocument();
  });

  it("creates a transaction", async () => {
    const user = userEvent.setup();

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    // A new transaction starts on today's date.
    expect(screen.getByLabelText("Date")).toHaveValue(toDateInputValue(new Date()));
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
    // After saving, the next one starts on today again.
    await waitFor(() => expect(screen.getByLabelText("Date")).toHaveValue(toDateInputValue(new Date())));
  });

  it("loads a transaction into edit mode", async () => {
    const user = userEvent.setup();

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    // The summary supplements the field messages instead of repeating them.
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Please check the highlighted fields.",
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

  it.each([
    ["type", "Type is invalid.", "Type", "#transaction-type"],
    ["amount", "Amount is invalid.", "Amount", undefined],
    ["description", "Description is invalid.", "Description", undefined],
    ["transactionDate", "Date is invalid.", "Date", undefined],
  ])(
    "focuses the %s field when it is the first validation error",
    async (field, message, label, selector) => {
      const user = userEvent.setup();

      vi.mocked(transactionService.createTransaction).mockRejectedValue(
        new ApiError("Validation failed.", 400, { [field]: message }),
      );

      render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

      await screen.findByText("Food Lion");
      await completeTransactionForm();

      if (field === "type") {
        await user.selectOptions(
          screen.getByLabelText("Type", { selector: "#transaction-type" }),
          "INCOME",
        );
      }

      await user.click(screen.getByRole("button", { name: "Add Transaction" }));

      const input = selector
        ? screen.getByLabelText(label, { selector })
        : screen.getByLabelText(label);

      expect(await screen.findByText(message)).toBeInTheDocument();
      expect(input).toHaveAttribute("aria-invalid", "true");
      expect(input).toHaveAccessibleDescription(message);

      await waitFor(() => {
        expect(input).toHaveFocus();
      });
    },
  );

  it("shows an API form error that has no field validation details", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new ApiError("Transaction could not be saved.", 422),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await completeTransactionForm();
    await user.click(screen.getByRole("button", { name: "Add Transaction" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Transaction could not be saved.",
    );
    // aria-invalid is only present when the control is invalid.
    expect(screen.getByLabelText("Category")).not.toHaveAttribute("aria-invalid");
  });

  it.each([
    [
      new ApiError("Transactions are unavailable.", 503),
      "Transactions are unavailable.",
    ],
    [
      new Error("Network failed"),
      "Unable to load transaction data. Please try again.",
    ],
  ])("reports initial transaction load failures", async (error, message) => {
    vi.mocked(transactionService.getTransactions).mockRejectedValue(error);

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    // Announced as an error, with a visible label rather than colour alone.
    expect(await screen.findByRole("alert")).toHaveTextContent(`Error: ${message}`);
  });

  it("preserves the create form and skips refresh when creation fails", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.createTransaction).mockRejectedValue(
      new Error("Request failed"),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await completeTransactionForm();

    await user.click(
      screen.getByRole("button", {
        name: "Add Transaction",
      }),
    );

    expect((await screen.findByText(/Transaction saved, but the transaction list could not be refreshed\./)).closest("[role='status']"))
      .toHaveTextContent(/^Warning: /);
    // The warning replaces the success banner rather than appearing beside it.
    expect(screen.queryByText(/^Transaction (added|updated)\.$/)).not.toBeInTheDocument();

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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

    expect((await screen.findByText(/Transaction saved, but the transaction list could not be refreshed\./)).closest("[role='status']"))
      .toHaveTextContent(/^Warning: /);
    // The warning replaces the success banner rather than appearing beside it.
    expect(screen.queryByText(/^Transaction (added|updated)\.$/)).not.toBeInTheDocument();

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

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(transactionService.deleteTransaction).toHaveBeenCalledWith(1);
    });

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenCalledTimes(2);
    });

    expect(await screen.findByText("Transaction deleted.")).toHaveAttribute("role", "status");
  });

  it("resets the form when deleting the transaction being edited", async () => {
    const user = userEvent.setup();

    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(transactionService.getTransactions)
      .mockResolvedValueOnce(createPagedResponse())
      .mockResolvedValueOnce(createPagedResponse([], 0, 0));

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(
      await screen.findByRole("heading", { name: "Add Transaction" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel" }),
    ).not.toBeInTheDocument();
  });

  it.each([
    [new ApiError("Delete is unavailable.", 503), "Delete is unavailable."],
    [
      new Error("Network failed"),
      "Unable to delete transaction. Please try again.",
    ],
  ])("reports transaction deletion failures", async (error, message) => {
    const user = userEvent.setup();

    vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.mocked(transactionService.deleteTransaction).mockRejectedValue(error);

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("does not delete a transaction when confirmation is cancelled", async () => {
    const user = userEvent.setup();

    vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(transactionService.deleteTransaction).not.toHaveBeenCalled();
    expect(screen.getByText("Food Lion")).toBeInTheDocument();
  });

  it("applies transaction filters", async () => {
    const user = userEvent.setup();

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.type(screen.getByLabelText("Search"), "Food");

    await user.selectOptions(
      screen.getByLabelText("Type", {
        selector: "#filter-type",
      }),
      "EXPENSE",
    );

    await user.type(screen.getByLabelText("Start Date"), "2026-09-01");
    await user.type(screen.getByLabelText("End Date"), "2026-09-30");
    await user.type(screen.getByLabelText("Minimum Amount"), "25");
    await user.type(screen.getByLabelText("Maximum Amount"), "100");
    await user.selectOptions(screen.getByLabelText("Sort By"), "amount");
    await user.selectOptions(screen.getByLabelText("Direction"), "asc");

    await user.click(
      screen.getByRole("button", {
        name: "Apply Filters",
      }),
    );

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          size: pageSize,
          search: "Food",
          type: "EXPENSE",
          startDate: "2026-09-01",
          endDate: "2026-09-30",
          minAmount: 25,
          maxAmount: 100,
          sortBy: "amount",
          sortDirection: "asc",
        }), expect.any(AbortSignal)
      );
    });
  });

  it.each([
    [
      new ApiError("Filtering is unavailable.", 503),
      "Filtering is unavailable.",
    ],
    [
      new Error("Network failed"),
      "Unable to filter transactions. Please try again.",
    ],
  ])("reports transaction filter failures", async (error, message) => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions)
      .mockResolvedValueOnce(createPagedResponse())
      .mockRejectedValueOnce(error);

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Apply Filters" }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("resets filters and reloads the first page with default sorting", async () => {
    const user = userEvent.setup();

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.type(screen.getByLabelText("Search"), "Food");
    await user.selectOptions(
      screen.getByLabelText("Type", { selector: "#filter-type" }),
      "EXPENSE",
    );
    await user.type(screen.getByLabelText("Start Date"), "2026-09-01");
    await user.type(screen.getByLabelText("End Date"), "2026-09-30");
    await user.type(screen.getByLabelText("Minimum Amount"), "25");
    await user.type(screen.getByLabelText("Maximum Amount"), "100");
    await user.selectOptions(screen.getByLabelText("Sort By"), "amount");
    await user.selectOptions(screen.getByLabelText("Direction"), "asc");

    await user.click(screen.getByRole("button", { name: "Reset" }));

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenLastCalledWith({
        page: 0,
        size: pageSize,
        sortBy: "transactionDate",
        sortDirection: "desc",
      }, expect.any(AbortSignal));
    });

    expect(screen.getByLabelText("Search")).toHaveValue("");
    expect(screen.getByLabelText("Sort By")).toHaveValue("transactionDate");
    expect(screen.getByLabelText("Direction")).toHaveValue("desc");
  });

  it.each([
    [new ApiError("Reset is unavailable.", 503), "Reset is unavailable."],
    [
      new Error("Network failed"),
      "Unable to load transactions. Please try again.",
    ],
  ])("reports reset-filter failures", async (error, message) => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions)
      .mockResolvedValueOnce(createPagedResponse())
      .mockRejectedValueOnce(error);

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Reset" }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("loads the next page while preserving filters", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([transaction], 0, 2),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

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
          size: pageSize,
          sortBy: "transactionDate",
          sortDirection: "desc",
        }), expect.any(AbortSignal)
      );
    });
  });

  it("loads the previous page", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([transaction], 1, 2),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Previous" }));

    await waitFor(() => {
      expect(transactionService.getTransactions).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 0 }), expect.any(AbortSignal)
      );
    });
  });

  it("ignores an invalid previous-page request", async () => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions).mockResolvedValue(
      createPagedResponse([transaction], -1, 2),
    );

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Previous" }));

    expect(transactionService.getTransactions).toHaveBeenCalledOnce();
  });

  it.each([
    [new ApiError("Page is unavailable.", 503), "Page is unavailable."],
    [
      new Error("Network failed"),
      "Unable to load the requested page. Please try again.",
    ],
  ])("reports pagination failures", async (error, message) => {
    const user = userEvent.setup();

    vi.mocked(transactionService.getTransactions)
      .mockResolvedValueOnce(createPagedResponse([transaction], 0, 2))
      .mockRejectedValueOnce(error);

    render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);

    await screen.findByText("Food Lion");
    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});

 it("uses the preferred page size and resets to page zero on preference changes", async () => {
   vi.mocked(useAuth).mockReturnValue(accountContext({ ...accountUser, preferences: { dateFormat: "ISO", transactionPageSize: 25 } }));
   vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
   vi.mocked(transactionService.getTransactions).mockResolvedValue(createPagedResponse());
   const { rerender } = render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
   expect(await screen.findByText("2026-09-10")).toBeInTheDocument();
   expect(transactionService.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 25 }), expect.any(AbortSignal));
   await userEvent.type(screen.getByLabelText("Search"), "Food");
   await userEvent.click(screen.getByRole("button", { name: "Apply Filters" }));
   expect(transactionService.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ size: 25, search: "Food" }), expect.any(AbortSignal));
   vi.mocked(useAuth).mockReturnValue(accountContext({ ...accountUser, preferences: { dateFormat: "ISO", transactionPageSize: 50 } }));
   rerender(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
   await waitFor(() => expect(transactionService.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50, search: "Food" }), expect.any(AbortSignal)));
   await userEvent.click(screen.getByRole("button", { name: "Reset" }));
   expect(transactionService.getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }), expect.any(AbortSignal));
 });

it("falls back to ten when account preferences are unavailable", async () => {
 vi.clearAllMocks();
 vi.mocked(useAuth).mockReturnValue({ ...accountContext(), user: null });
 vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
 vi.mocked(transactionService.getTransactions).mockResolvedValue(createPagedResponse());
 render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
 await screen.findByText("Food Lion");
 expect(transactionService.getTransactions).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ size: 10 }), expect.any(AbortSignal));
});

it("ignores an older page-size response and avoids refetches for unrelated identity changes", async () => {
 vi.clearAllMocks();
 const old = deferred<PagedTransactionResponse>();
 vi.mocked(useAuth).mockReturnValue(accountContext());
 vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
 vi.mocked(transactionService.getTransactions).mockReturnValueOnce(old.promise).mockResolvedValue(createPagedResponse([{ ...transaction, description: "New result" }]));
 const { rerender } = render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
 await waitFor(() => expect(transactionService.getTransactions).toHaveBeenCalledTimes(1));
 const updated = { ...accountUser, preferences: { dateFormat: "ISO" as const, transactionPageSize: 50 as const } };
 vi.mocked(useAuth).mockReturnValue(accountContext(updated));
 rerender(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
 await screen.findByText("New result");
 old.resolve(createPagedResponse([{ ...transaction, description: "Old result" }]));
 await waitFor(() => expect(screen.queryByText("Old result")).not.toBeInTheDocument());
 vi.mocked(useAuth).mockReturnValue(accountContext({ ...updated, displayName: "Changed name" }));
 rerender(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
 expect(transactionService.getTransactions).toHaveBeenCalledTimes(2);
});


it("keeps newer filter results when an older filter request completes later", async () => {
  vi.clearAllMocks();
  vi.mocked(useAuth).mockReturnValue(accountContext());
  vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
  const older = deferred<PagedTransactionResponse>();
  vi.mocked(transactionService.getTransactions)
    .mockResolvedValueOnce(createPagedResponse())
    .mockReturnValueOnce(older.promise)
    .mockResolvedValueOnce(createPagedResponse([{ ...transaction, description: "Latest filtered result" }]));
  render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
  await screen.findByText("Food Lion");
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Search"), "Old");
  await user.click(screen.getByRole("button", { name: "Apply Filters" }));
  await user.clear(screen.getByLabelText("Search"));
  await user.type(screen.getByLabelText("Search"), "Latest");
  await user.click(screen.getByRole("button", { name: "Apply Filters" }));
  await screen.findByText("Latest filtered result");
  await act(async () => older.resolve(createPagedResponse([{ ...transaction, description: "Obsolete filtered result" }])));
  expect(screen.getByText("Latest filtered result")).toBeInTheDocument();
  expect(screen.queryByText("Obsolete filtered result")).not.toBeInTheDocument();
});

it("ignores an obsolete page-size load failure after the new size succeeds", async () => {
  vi.clearAllMocks();
  const older = deferred<PagedTransactionResponse>();
  vi.mocked(useAuth).mockReturnValue(accountContext());
  vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
  vi.mocked(transactionService.getTransactions)
    .mockReturnValueOnce(older.promise)
    .mockResolvedValueOnce(createPagedResponse([{ ...transaction, description: "Current page" }]));
  const { rerender } = render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
  await waitFor(() => expect(transactionService.getTransactions).toHaveBeenCalledTimes(1));
  vi.mocked(useAuth).mockReturnValue(accountContext({ ...accountUser, preferences: { dateFormat: "MEDIUM", transactionPageSize: 25 } }));
  rerender(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
  await screen.findByText("Current page");
  await act(async () => older.reject(new ApiError("Obsolete request failed", 503)));
  expect(screen.getByText("Current page")).toBeInTheDocument();
  expect(screen.queryByText("Obsolete request failed")).not.toBeInTheDocument();
  expect(screen.queryByText("Loading transactions…")).not.toBeInTheDocument();
});

describe("TransactionPage loading and stale requests", () => {
  const getTransactions = () => vi.mocked(transactionService.getTransactions);
  const renderPage = () => render(<MemoryRouter><CategoryProvider><TransactionPage /></CategoryProvider></MemoryRouter>);
  const row = (description: string) => ({ ...transaction, id: description.length, description });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    vi.mocked(categoryService.getCategories).mockResolvedValue(categories);
    getTransactions().mockResolvedValue(createPagedResponse());
  });

  async function search(user: ReturnType<typeof userEvent.setup>, text: string) {
    await user.clear(screen.getByLabelText("Search"));
    await user.type(screen.getByLabelText("Search"), text);
    await user.click(screen.getByRole("button", { name: "Apply Filters" }));
  }

  it("never shows the old rows under new filters, and only the newest request ends loading", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Food Lion");
    const first = deferred<PagedTransactionResponse>();
    const second = deferred<PagedTransactionResponse>();
    getTransactions().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);

    await search(user, "first");
    expect(screen.getByText("Loading transactions…")).toHaveAttribute("role", "status");
    expect(screen.queryByText("Food Lion")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Search")).toBeEnabled(); // Filters stay usable.

    await search(user, "second");
    expect(getTransactions().mock.calls.at(-2)![1]!.aborted).toBe(true);
    // The older request finishing changes nothing: still loading, no old rows.
    await act(async () => first.resolve(createPagedResponse([row("Old filter row")])));
    expect(screen.getByText("Loading transactions…")).toBeInTheDocument();
    expect(screen.queryByText("Old filter row")).not.toBeInTheDocument();

    await act(async () => second.resolve(createPagedResponse([row("New filter row")])));
    expect(await screen.findByText("New filter row")).toBeInTheDocument();
    expect(screen.queryByText("Loading transactions…")).not.toBeInTheDocument();
  });

  it("ignores an older request's failure once a newer one has loaded", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Food Lion");
    const older = deferred<PagedTransactionResponse>();
    getTransactions().mockReturnValueOnce(older.promise).mockResolvedValueOnce(createPagedResponse([row("Newest")]));

    await search(user, "older");
    await search(user, "newest");
    expect(await screen.findByText("Newest")).toBeInTheDocument();

    await act(async () => older.reject(new ApiError("Obsolete failure", 503)));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Newest")).toBeInTheDocument();
  });

  it("tells a failed load apart from an empty list, and Try again repeats the same request", async () => {
    const user = userEvent.setup();
    getTransactions().mockRejectedValueOnce(new Error("offline"));
    renderPage();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Unable to load transaction data. Please try again.");
    expect(screen.getByText("Transactions could not be loaded.")).toBeInTheDocument();
    expect(screen.queryByText("No transactions found.")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Filter Transactions" })).toBeInTheDocument();
    const failedRequest = getTransactions().mock.calls[0][0];

    const retry = screen.getByRole("button", { name: "Try again" });
    expect(retry).toHaveAttribute("type", "button");
    await user.click(retry);

    expect(await screen.findByText("Food Lion")).toBeInTheDocument();
    expect(getTransactions()).toHaveBeenLastCalledWith(failedRequest, expect.any(AbortSignal));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("retries a failed filter with the same filters", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Food Lion");
    getTransactions().mockRejectedValueOnce(new Error("offline"));

    await search(user, "Coffee");
    await user.click(await screen.findByRole("button", { name: "Try again" }));

    expect(getTransactions()).toHaveBeenLastCalledWith(expect.objectContaining({ search: "Coffee", page: 0 }), expect.any(AbortSignal));
    expect(await screen.findByText("Food Lion")).toBeInTheDocument();
  });

  it("says when filters match nothing, unlike an account with no transactions", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Food Lion");
    getTransactions().mockResolvedValue(createPagedResponse([], 0, 0));

    await search(user, "nothing like this");
    expect(await screen.findByText("No transactions match these filters.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Reset" }));
    expect(await screen.findByText("No transactions found.")).toBeInTheDocument();
  });

  it("aborts the pending request when the page is left", async () => {
    getTransactions().mockReturnValueOnce(new Promise(() => {}));
    const view = renderPage();
    await waitFor(() => expect(getTransactions()).toHaveBeenCalledOnce());
    const signal = getTransactions().mock.calls[0][1]!;

    view.unmount();

    expect(signal.aborted).toBe(true);
  });
});
