vi.mock("../context/AuthContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../context/AuthContext")>()),
  useAuth: vi.fn(),
}));
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategorySummary: vi.fn(),
  getCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}));

import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { CategoryProvider } from "../context/CategoryProvider";
import { ApiError } from "../services/api";
import * as categoryService from "../services/categoryService";
import { getCategorySummary } from "../services/categoryService";
import { category, sampleCategories } from "../test/categoryFixtures";
import type { CategorySummaryList } from "../types/category";
import { accountContext, accountUser, deferred } from "../test/accountFixtures";
import {
  groceriesRow,
  petCareRow,
  salaryRow,
  summaryList,
  summaryRow,
  unusedRow,
} from "../test/categorySummaryFixtures";
import CategoriesPage from "./CategoriesPage";

const allRows = [groceriesRow, salaryRow, petCareRow, unusedRow];

function renderWithProviders() {
  render(<MemoryRouter><CategoryProvider><CategoriesPage /></CategoryProvider></MemoryRouter>);
}

async function renderPage() {
  const user = userEvent.setup();
  renderWithProviders();
  await screen.findByRole("heading", { name: "All categories" });
  return user;
}

// Full user-event flows are slow under coverage instrumentation and on a busy machine.
vi.setConfig({ testTimeout: 20_000 });

const card = (name: string) => screen.getByRole("article", { name });
/** The card's activity line as one string (the date is wrapped to keep it together). */
const activity = (name: string) => card(name).querySelector(".category-card__usage");
const cardNames = () => screen.queryAllByRole("article").map((article) => within(article).getByRole("heading").textContent);

describe("CategoriesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList(allRows));
    vi.mocked(categoryService.getCategories).mockResolvedValue(sampleCategories);
  });

  it("has one page heading and shows loading until the summary arrives", async () => {
    renderWithProviders();

    expect(screen.getByText("Loading categories…")).toHaveAttribute("role", "status");
    await screen.findByRole("heading", { name: "All categories" });
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.queryByText("Loading categories…")).not.toBeInTheDocument();
  });

  it("labels everything with the server's reporting month, not the browser's", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([unusedRow], 3, 2025));
    await renderPage();

    expect(screen.getByText(/How each category is used in March 2025/)).toBeInTheDocument();
    expect(within(card("Hobbies")).getByText("No budget for March")).toBeInTheDocument();
    expect(within(card("Hobbies")).getByText("Spent in March")).toBeInTheDocument();
  });

  it("summarises categories, the top category, budgets over limit, and spending", async () => {
    await renderPage();
    const strip = screen.getByText("Categories", { selector: "dt" }).closest("dl")!;

    expect(within(strip).getByText("4")).toBeInTheDocument();
    expect(within(strip).getByText("2 custom · 2 built-in")).toBeInTheDocument();
    expect(within(strip).getByText("Groceries")).toBeInTheDocument();
    expect(within(strip).getByText("$300.00 of $400.00")).toBeInTheDocument();
    expect(within(strip).getByText("of 2 budgets this month")).toBeInTheDocument();
    expect(within(strip).getByText("categories in October 2026")).toBeInTheDocument();
  });

  it("explains when nothing has been spent this month", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([salaryRow, unusedRow]));
    await renderPage();

    expect(screen.getByText("No spending yet in October 2026")).toBeInTheDocument();
    // With no spending there is no share to show on the cards.
    expect(screen.queryByText("Share of spending")).not.toBeInTheDocument();
  });

  it("shows a category's spending, share, budget progress, and activity", async () => {
    await renderPage();
    const groceries = card("Groceries");

    expect(within(groceries).getByText("Built-in")).toBeInTheDocument();
    expect(within(groceries).getByText("$300.00", { selector: "dd" })).toBeInTheDocument();
    expect(within(groceries).getByText("75.0%")).toBeInTheDocument();
    expect(within(groceries).getByText("$300.00 of $400.00")).toBeInTheDocument();
    expect(within(groceries).getByText("Warning")).toBeInTheDocument();
    expect(within(groceries).getByText("$100.00 left")).toBeInTheDocument();
    const progress = within(groceries).getByRole("progressbar", { name: "Groceries budget used" });
    expect(progress).toHaveAttribute("aria-valuenow", "75");
    expect(progress).toHaveAttribute("aria-valuetext", "75.0% used");
    expect(activity("Groceries")).toHaveTextContent("6 transactions · 2 budgets · Last used Oct 12, 2026");
    expect(within(groceries).getByText("Last used Oct 12, 2026")).toHaveClass("category-card__last-used");
  });

  it("caps the progress bar at 100% but reports the real overspend", async () => {
    await renderPage();
    const pets = card("Pet Care");

    expect(within(pets).getByText("Custom")).toBeInTheDocument();
    expect(within(pets).getByText("Over Budget")).toBeInTheDocument();
    expect(within(pets).getByText("$20.00 over budget")).toBeInTheDocument();
    const progress = within(pets).getByRole("progressbar");
    expect(progress).toHaveAttribute("aria-valuenow", "100");
    expect(progress).toHaveAttribute("aria-valuetext", "125.0% used");
  });

  it("offers no budget state for categories that do not take budgets", async () => {
    await renderPage();

    expect(within(card("Income")).queryByText(/No budget/)).not.toBeInTheDocument();
    expect(within(card("Income")).queryByRole("progressbar")).not.toBeInTheDocument();
    expect(within(card("Hobbies")).getByText("No budget for October")).toBeInTheDocument();
  });

  it("shows a share only for categories with spending this month", async () => {
    await renderPage();

    expect(within(card("Pet Care")).getByText("Share of spending")).toBeInTheDocument();
    expect(within(card("Pet Care")).getByText("25.0%")).toBeInTheDocument();
    expect(within(card("Hobbies")).queryByText("Share of spending")).not.toBeInTheDocument();
    expect(within(card("Hobbies")).getByText("$0.00", { selector: "dd" })).toBeInTheDocument();
  });

  it("still shows a budget that exists for a category that does not take budgets", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([summaryRow({
      id: 30, name: "Savings", budgetEnabled: false, builtIn: true, canDelete: false, budgetCount: 1,
      currentMonthBudget: {
        budgetId: 3, monthlyLimit: 50, amountSpent: 0, amountRemaining: 50, percentageUsed: 0, status: "ON_TRACK",
      },
    })]));
    await renderPage();

    expect(within(card("Savings")).getByRole("progressbar")).toHaveAttribute("aria-valuenow", "0");
    expect(within(card("Savings")).getByText("On Track")).toBeInTheDocument();
  });

  it("describes unused categories and singular counts", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([unusedRow, summaryRow({
      id: 31, name: "Gifts", transactionCount: 1, budgetCount: 1, lastTransactionDate: "2026-09-30",
    })]));
    await renderPage();

    expect(activity("Hobbies")).toHaveTextContent(/^Not used yet$/);
    expect(activity("Gifts")).toHaveTextContent("1 transaction · 1 budget · Last used Sep 30, 2026");
  });

  it("uses the account's date format", async () => {
    vi.mocked(useAuth).mockReturnValue(accountContext({
      ...accountUser, preferences: { ...accountUser.preferences, dateFormat: "ISO" },
    }));
    await renderPage();

    expect(within(card("Pet Care")).getByText(/Last used 2026-10-02/)).toBeInTheDocument();
  });

  it("lists every category by name by default, whatever order the API returns", async () => {
    await renderPage();

    expect(cardNames()).toEqual(["Groceries", "Hobbies", "Income", "Pet Care"]);
    expect(screen.getByText("Showing 4 of 4 categories")).toHaveAttribute("role", "status");
  });

  it("links every category to its transactions and budget-enabled ones to their budget", async () => {
    await renderPage();

    expect(within(card("Groceries")).getByRole("link", { name: "View transactions for Groceries" }))
      .toHaveAttribute("href", "/transactions?category=1");
    expect(within(card("Groceries")).getByRole("link", { name: "Edit budget for Groceries" }))
      .toHaveAttribute("href", "/budgets?category=1");
    expect(within(card("Hobbies")).getByRole("link", { name: "Set budget for Hobbies" }))
      .toHaveAttribute("href", "/budgets?category=9");
    expect(within(card("Income")).getByRole("link", { name: "View transactions for Income" })).toBeInTheDocument();
    expect(within(card("Income")).queryByRole("link", { name: /budget/ })).not.toBeInTheDocument();
  });

  it("offers to add the first transaction to a category that has none", async () => {
    await renderPage();

    expect(within(card("Hobbies")).getByRole("link", { name: "Add a transaction for Hobbies" }))
      .toHaveAttribute("href", "/transactions?addCategory=9");
    expect(within(card("Hobbies")).queryByRole("link", { name: /View transactions/ })).not.toBeInTheDocument();
    expect(within(card("Groceries")).queryByRole("link", { name: /Add a transaction/ })).not.toBeInTheDocument();
  });

  it("offers no budget action for a category that does not take budgets, even with a budget", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([summaryRow({
      id: 30, name: "Savings", budgetEnabled: false, builtIn: true, canDelete: false, budgetCount: 1,
      currentMonthBudget: {
        budgetId: 3, monthlyLimit: 50, amountSpent: 0, amountRemaining: 50, percentageUsed: 0, status: "ON_TRACK",
      },
    })]));
    await renderPage();

    expect(within(card("Savings")).getByRole("progressbar")).toBeInTheDocument();
    expect(within(card("Savings")).queryByRole("link", { name: /budget/ })).not.toBeInTheDocument();
  });

  it("offers no edit or delete for built-in categories", async () => {
    await renderPage();

    for (const name of ["Groceries", "Income"]) {
      expect(within(card(name)).queryByRole("button")).not.toBeInTheDocument();
    }
    expect(within(card("Pet Care")).getByRole("button", { name: "Edit Pet Care" })).toBeInTheDocument();
  });

  it("explains a load failure and retries", async () => {
    const user = userEvent.setup();
    vi.mocked(getCategorySummary)
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(summaryList(allRows));
    renderWithProviders();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Error: Unable to load categories. Please try again.");
    // Nothing from an earlier load is shown as current.
    expect(screen.queryByText("Categories", { selector: "dt" })).not.toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
    await user.click(within(alert).getByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("heading", { name: "All categories" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an empty state when there are no categories", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([]));
    renderWithProviders();

    expect(await screen.findByText("You don’t have any categories yet.")).toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });

  describe("spending distribution", () => {
    it("shows the whole month's spending for the server's month, before the cards", async () => {
      await renderPage();
      const table = screen.getByRole("table", { name: "Spending in October 2026" });

      expect(within(table).getAllByRole("rowheader").map((cell) => cell.textContent)).toEqual(["Groceries", "Pet Care"]);
      expect(within(table).getByText("75.0%")).toBeInTheDocument();
      expect(table.compareDocumentPosition(screen.getByRole("heading", { name: "All categories" })))
        .toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    });

    it("explains a month with no spending", async () => {
      vi.mocked(getCategorySummary).mockResolvedValue(summaryList([salaryRow, unusedRow], 9, 2026));
      await renderPage();

      expect(screen.getByText("No spending recorded for September 2026.")).toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });
  });

  describe("finding categories", () => {
    const search = () => screen.getByLabelText("Search categories");
    const strip = () => screen.getByText("Categories", { selector: "dt" }).closest("dl")!;

    it("searches the cards only; the summary and spending table still cover every category", async () => {
      const user = await renderPage();
      await user.type(search(), "  PET ");

      expect(cardNames()).toEqual(["Pet Care"]);
      expect(screen.getByText("Showing 1 of 4 categories")).toBeInTheDocument();
      expect(within(strip()).getByText("4")).toBeInTheDocument();
      expect(within(strip()).getByText("$300.00 of $400.00")).toBeInTheDocument();
      expect(within(screen.getByRole("table")).getByRole("rowheader", { name: "Groceries" })).toBeInTheDocument();
    });

    it.each([
      ["Custom", ["Hobbies", "Pet Care"]],
      ["Built-in", ["Groceries", "Income"]],
      ["Unused", ["Hobbies"]],
      ["No budget this month", ["Hobbies", "Income"]],
      ["All categories", ["Groceries", "Hobbies", "Income", "Pet Care"]],
    ])("filters to %s", async (option, expected) => {
      const user = await renderPage();
      await user.selectOptions(screen.getByLabelText("Filter categories"), screen.getByRole("option", { name: option }));

      expect(cardNames()).toEqual(expected);
      expect(screen.getByText(`Showing ${expected.length} of 4 categories`)).toBeInTheDocument();
    });

    it.each([
      ["This month’s spending", ["Groceries", "Pet Care", "Hobbies", "Income"]],
      ["Most used", ["Groceries", "Pet Care", "Income", "Hobbies"]],
      ["Name", ["Groceries", "Hobbies", "Income", "Pet Care"]],
    ])("sorts by %s", async (option, expected) => {
      const user = await renderPage();
      await user.selectOptions(screen.getByLabelText("Sort categories"), screen.getByRole("option", { name: option }));

      expect(cardNames()).toEqual(expected);
    });

    it("combines search and filter, and offers a way back from no results", async () => {
      const user = await renderPage();
      await user.selectOptions(screen.getByLabelText("Filter categories"), "custom");
      await user.type(search(), "groc");

      expect(cardNames()).toEqual([]);
      expect(screen.getByText("No categories match your search and filter.")).toBeInTheDocument();
      expect(screen.getByText("Showing 0 of 4 categories")).toBeInTheDocument();
      expect(screen.queryByText("You don’t have any categories yet.")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Show all categories" }));
      expect(cardNames()).toHaveLength(4);
      expect(search()).toHaveValue("");
      expect(screen.getByLabelText("Filter categories")).toHaveValue("all");
    });

    it("clears search, filter, and sort together and keeps focus in the toolbar", async () => {
      const user = await renderPage();
      expect(screen.queryByRole("button", { name: "Clear category filters" })).not.toBeInTheDocument();

      await user.type(search(), "o");
      await user.selectOptions(screen.getByLabelText("Sort categories"), "mostUsed");
      await user.click(screen.getByRole("button", { name: "Clear category filters" }));

      expect(search()).toHaveValue("");
      expect(screen.getByLabelText("Sort categories")).toHaveValue("name");
      expect(cardNames()).toEqual(["Groceries", "Hobbies", "Income", "Pet Care"]);
      expect(screen.queryByRole("button", { name: "Clear category filters" })).not.toBeInTheDocument();
      await waitFor(() => expect(search()).toHaveFocus());
    });

    it("keeps a category being edited visible, with an explanation, while the toolbar changes", async () => {
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.type(screen.getByLabelText("Category name"), "s");
      await user.type(search(), "groc");

      expect(screen.getByRole("form", { name: "Edit Pet Care" })).toBeInTheDocument();
      expect(screen.getByLabelText("Category name")).toHaveValue("Pet Cares"); // Nothing lost.
      expect(cardNames()).toEqual(["Groceries", "Pet Care"]);
      expect(screen.getByText(/“Pet Care” is shown because you’re working on it/)).toBeInTheDocument();

      // After Cancel it stays until the toolbar next changes, so focus has somewhere to go.
      await user.click(screen.getByRole("button", { name: "Cancel" }));
      await waitFor(() => expect(screen.getByRole("button", { name: "Edit Pet Care" })).toHaveFocus());
      await user.type(search(), "e");
      expect(cardNames()).toEqual(["Groceries"]);
      expect(screen.queryByText(/is shown because you’re working on it/)).not.toBeInTheDocument();
    });

    it("keeps a renamed category visible and focused when its new name no longer matches", async () => {
      const renamed = { ...petCareRow, name: "Animals" };
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([groceriesRow, salaryRow, renamed, unusedRow]));
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Animals" }));
      const user = await renderPage();
      await user.type(search(), "pet");
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.clear(screen.getByLabelText("Category name"));
      await user.type(screen.getByLabelText("Category name"), "Animals");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      await waitFor(() => expect(screen.getByRole("button", { name: "Edit Animals" })).toHaveFocus());
      expect(cardNames()).toEqual(["Animals"]);
    });

    it("shows a new category even when the current filter would hide it", async () => {
      const created = summaryRow({ id: 60, name: "Gifts", iconKey: "gift" });
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([created, ...allRows]));
      vi.mocked(categoryService.createCategory).mockResolvedValue(category({ id: 60, name: "Gifts", iconKey: "gift" }));
      const user = await renderPage();
      await user.selectOptions(screen.getByLabelText("Filter categories"), "builtIn");
      await user.click(screen.getByRole("button", { name: "Create category" }));
      await user.type(screen.getByLabelText("Category name"), "Gifts");
      await user.click(within(screen.getByRole("form", { name: "Create category" })).getByRole("button", { name: "Create category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "Gifts" })).toHaveFocus());
      expect(cardNames()).toEqual(["Gifts", "Groceries", "Income"]);
      // The refreshed data is filtered again on the next change.
      await user.selectOptions(screen.getByLabelText("Filter categories"), "builtIn");
      await user.selectOptions(screen.getByLabelText("Sort categories"), "mostUsed");
      expect(cardNames()).toEqual(["Groceries", "Income"]);
    });

    it("moves focus after a delete to the next card as currently filtered and sorted", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([groceriesRow, salaryRow, petCareRow]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();
      await user.selectOptions(screen.getByLabelText("Filter categories"), "custom");
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      // Unfiltered, "Income" would follow "Hobbies"; with the Custom filter it is "Pet Care".
      await waitFor(() => expect(screen.getByRole("heading", { name: "Pet Care" })).toHaveFocus());
      expect(cardNames()).toEqual(["Pet Care"]);
    });
  });

  describe("deleting", () => {
    it("keeps Delete focusable but inactive for a category in use, and explains why", async () => {
      const user = await renderPage();
      const remove = within(card("Pet Care")).getByRole("button", { name: "Delete Pet Care" });

      expect(remove).toHaveAttribute("aria-disabled", "true");
      expect(remove).toHaveAccessibleDescription(
        "Used by 3 transactions and 1 budget. Change or remove those first to delete this category.");
      remove.focus();
      expect(remove).toHaveFocus();

      await user.click(remove);
      await user.keyboard("{Enter}");
      await user.keyboard(" ");

      expect(categoryService.deleteCategory).not.toHaveBeenCalled();
      expect(screen.queryByRole("group", { name: /Delete “Pet Care”/ })).not.toBeInTheDocument();
    });

    it("confirms, deletes, announces, refreshes, and focuses the next card", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow, petCareRow]))
        .mockResolvedValueOnce(summaryList([petCareRow]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();

      await user.click(within(card("Hobbies")).getByRole("button", { name: "Delete Hobbies" }));
      const confirm = screen.getByRole("group", { name: "Delete “Hobbies”? This cannot be undone." });
      expect(within(confirm).getByRole("button", { name: "Delete category" })).toHaveFocus();
      await user.click(within(confirm).getByRole("button", { name: "Delete category" }));

      expect(categoryService.deleteCategory).toHaveBeenCalledExactlyOnceWith(9);
      expect(await screen.findByText("“Hobbies” was deleted successfully.")).toHaveAttribute("role", "status");
      await waitFor(() => expect(screen.queryByRole("article", { name: "Hobbies" })).not.toBeInTheDocument());
      await waitFor(() => expect(screen.getByRole("heading", { name: "Pet Care" })).toHaveFocus());
      expect(getCategorySummary).toHaveBeenCalledTimes(2);
    });

    it("focuses the previous card after deleting the last one in the list", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([petCareRow, unusedRow]))
        .mockResolvedValueOnce(summaryList([petCareRow]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "Pet Care" })).toHaveFocus());
    });

    it("focuses the list heading after deleting the only category", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow]))
        .mockResolvedValueOnce(summaryList([]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "All categories" })).toHaveFocus());
      expect(screen.getByText("You don’t have any categories yet.")).toBeInTheDocument();
    });

    it("keeps the category and returns focus when the delete is cancelled", async () => {
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Keep category" }));

      await waitFor(() => expect(screen.getByRole("button", { name: "Delete Hobbies" })).toHaveFocus());
      expect(categoryService.deleteCategory).not.toHaveBeenCalled();
    });

    it("keeps the category and explains when the server says it is now in use", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow]))
        .mockResolvedValueOnce(summaryList([{ ...unusedRow, transactionCount: 1, canDelete: false }]));
      vi.mocked(categoryService.deleteCategory).mockRejectedValue(new ApiError(
        "This category is used by transactions or budgets and cannot be deleted.", 409, undefined, "CATEGORY_IN_USE"));
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("“Hobbies” is still used by transactions or budgets, so it can’t be deleted.");
      expect(alert).toHaveTextContent("Change the category on those transactions and budgets, or delete them");
      expect(card("Hobbies")).toBeInTheDocument();
      // The refreshed summary now shows the real usage and blocks Delete.
      await waitFor(() => expect(screen.getByRole("button", { name: "Delete Hobbies" }))
        .toHaveAttribute("aria-disabled", "true"));
      expect(screen.getByText(/Used by 1 transaction\./)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Delete Hobbies" })).toHaveFocus();

      // It stays until dismissed.
      await user.click(within(alert).getByRole("button", { name: "Dismiss error" }));
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("reports an unexpected delete failure, keeps the category, and keeps the confirmation for a retry", async () => {
      vi.mocked(categoryService.deleteCategory)
        .mockRejectedValueOnce(new Error("network"))
        .mockResolvedValueOnce(undefined);
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Error: “Hobbies” was not deleted. Please try again.");
      expect(card("Hobbies")).toBeInTheDocument();
      expect(screen.queryByText(/was deleted successfully/)).not.toBeInTheDocument();
      const retry = within(card("Hobbies")).getByRole("button", { name: "Delete category" });
      await waitFor(() => expect(retry).toBeEnabled());
      expect(retry).toHaveFocus();

      // A successful retry replaces the error with the success message.
      await user.click(retry);
      expect(await screen.findByText("“Hobbies” was deleted successfully.")).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it.each([
      [new ApiError("Category not found", 404, undefined, "CATEGORY_NOT_FOUND"),
        "This category no longer exists. The list has been refreshed."],
      [new ApiError("Built-in categories cannot be changed or deleted.", 403, undefined, "CATEGORY_BUILT_IN"),
        "Built-in categories cannot be changed or deleted."],
    ])("explains and refreshes when the category changed elsewhere (%s)", async (failure, message) => {
      vi.mocked(categoryService.deleteCategory).mockRejectedValue(failure);
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(message);
      await waitFor(() => expect(getCategorySummary).toHaveBeenCalledTimes(2));
      expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
    });
  });

  describe("editing", () => {
    it("renames and re-icons a custom category, then refreshes and restores focus", async () => {
      const renamed = { ...petCareRow, name: "Pets", iconKey: "heart-pulse" };
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([groceriesRow, salaryRow, renamed, unusedRow]));
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pets", iconKey: "heart-pulse" }));
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      const form = screen.getByRole("form", { name: "Edit Pet Care" });
      const name = within(form).getByLabelText("Category name");
      expect(name).toHaveValue("Pet Care");
      expect(within(form).getByRole("radio", { name: "Paw print" })).toBeChecked();
      await user.clear(name);
      await user.type(name, "Pets");
      await user.click(within(form).getByRole("radio", { name: "Heart with pulse" }));
      await user.click(within(form).getByRole("button", { name: "Save category" }));

      expect(categoryService.updateCategory).toHaveBeenCalledExactlyOnceWith(7,
        { name: "Pets", budgetEnabled: true, iconKey: "heart-pulse" });
      expect(await screen.findByText("“Pets” was updated successfully.")).toHaveAttribute("role", "status");
      await waitFor(() => expect(screen.getByRole("button", { name: "Edit Pets" })).toHaveFocus());
      expect(getCategorySummary).toHaveBeenCalledTimes(2);
      expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
    });

    it("cancels without saving and returns focus to Edit", async () => {
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Cancel" }));

      await waitFor(() => expect(screen.getByRole("button", { name: "Edit Pet Care" })).toHaveFocus());
      expect(screen.queryByRole("form")).not.toBeInTheDocument();
      expect(categoryService.updateCategory).not.toHaveBeenCalled();
    });

    it("keeps the form open with the conflict for a duplicate name", async () => {
      vi.mocked(categoryService.updateCategory).mockRejectedValue(
        new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.clear(screen.getByLabelText("Category name"));
      await user.type(screen.getByLabelText("Category name"), "Groceries");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("That name is already used");
      expect(screen.getByRole("form", { name: "Edit Pet Care" })).toBeInTheDocument();
      expect(getCategorySummary).toHaveBeenCalledTimes(1);
    });

    it("closes the form and explains when the server says the category is built-in", async () => {
      vi.mocked(categoryService.updateCategory).mockRejectedValue(new ApiError(
        "Built-in categories cannot be changed or deleted.", 403, undefined, "CATEGORY_BUILT_IN"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("Built-in categories cannot be changed or deleted.");
      expect(screen.queryByRole("form")).not.toBeInTheDocument();
      // No field to fix: focus moves to the explanation.
      await waitFor(() => expect(alert).toHaveFocus());
    });

    it("closes the form and explains when the category no longer exists", async () => {
      vi.mocked(categoryService.updateCategory).mockRejectedValue(
        new ApiError("Category not found", 404, undefined, "CATEGORY_NOT_FOUND"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("This category no longer exists.");
      expect(screen.queryByRole("form")).not.toBeInTheDocument();
      await waitFor(() => expect(getCategorySummary).toHaveBeenCalledTimes(2));
    });
  });

  describe("creating", () => {
    const created = summaryRow({ id: 60, name: "Gifts", iconKey: "gift" });

    it("creates a category, announces it, refreshes, and focuses its card", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([created, ...allRows]));
      vi.mocked(categoryService.createCategory).mockResolvedValue(category({ id: 60, name: "Gifts", iconKey: "gift" }));
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Create category" }));
      const form = screen.getByRole("form", { name: "Create category" });
      expect(within(form).getByLabelText("Category name")).toHaveFocus();
      await user.type(within(form).getByLabelText("Category name"), "Gifts");
      await user.click(within(form).getByRole("radio", { name: "Gift" }));
      await user.click(within(form).getByRole("button", { name: "Create category" }));

      expect(categoryService.createCategory).toHaveBeenCalledExactlyOnceWith(
        { name: "Gifts", budgetEnabled: true, iconKey: "gift" });
      expect(await screen.findByText("“Gifts” was created successfully.")).toHaveAttribute("role", "status");
      await waitFor(() => expect(screen.getByRole("heading", { name: "Gifts" })).toHaveFocus());
      expect(screen.queryByRole("form")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Dismiss message" }));
      expect(screen.queryByText("“Gifts” was created successfully.")).not.toBeInTheDocument();
    });

    it("validates the name and shows a duplicate on the field", async () => {
      vi.mocked(categoryService.createCategory).mockRejectedValue(
        new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Create category" }));
      const form = screen.getByRole("form", { name: "Create category" });

      await user.click(within(form).getByRole("button", { name: "Create category" }));
      expect(within(form).getByLabelText("Category name")).toHaveAccessibleDescription("Category name is required");
      expect(categoryService.createCategory).not.toHaveBeenCalled();

      await user.type(within(form).getByLabelText("Category name"), "groceries");
      await user.click(within(form).getByRole("button", { name: "Create category" }));
      expect(await within(form).findByRole("alert")).toHaveTextContent("That name is already used");
    });

    it("reports an unexpected failure on the form", async () => {
      vi.mocked(categoryService.createCategory).mockRejectedValue(new Error("network"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Create category" }));
      await user.type(screen.getByLabelText("Category name"), "Gifts");
      await user.click(within(screen.getByRole("form")).getByRole("button", { name: "Create category" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Unable to create the category. Please try again.");
    });

    it("cancels and returns focus to Create category", async () => {
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Create category" }));
      await user.click(screen.getByRole("button", { name: "Cancel" }));

      await waitFor(() => expect(screen.getByRole("button", { name: "Create category" })).toHaveFocus());
      expect(categoryService.createCategory).not.toHaveBeenCalled();
    });
  });

  describe("status messages", () => {
    it("announces success only after the change and the refresh have both finished", async () => {
      const refresh = deferred<CategorySummaryList>();
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockReturnValueOnce(refresh.promise);
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));

      await waitFor(() => expect(getCategorySummary).toHaveBeenCalledTimes(2));
      expect(screen.queryByText(/was updated successfully/)).not.toBeInTheDocument();

      refresh.resolve(summaryList(allRows));
      expect(await screen.findByText("“Pet Care” was updated successfully.")).toBeInTheDocument();
      expect(screen.getAllByText(/was updated successfully/)).toHaveLength(1);
    });

    it.each([
      ["created", "“Gifts” was created, but the latest category summary could not be loaded."],
      ["deleted", "“Hobbies” was deleted, but the latest category summary could not be loaded."],
    ])("warns, without claiming failure, when a category was %s but the refresh failed", async (verb, message) => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockRejectedValueOnce(new Error("network"));
      vi.mocked(categoryService.createCategory).mockResolvedValue(category({ id: 60, name: "Gifts" }));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();

      if (verb === "created") {
        await user.click(screen.getByRole("button", { name: "Create category" }));
        await user.type(screen.getByLabelText("Category name"), "Gifts");
        await user.click(within(screen.getByRole("form", { name: "Create category" })).getByRole("button", { name: "Create category" }));
      } else {
        await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
        await user.click(screen.getByRole("button", { name: "Delete category" }));
      }

      expect(await screen.findByText(new RegExp(message)))
        .toHaveTextContent("Try again, or refresh the page to see the current data.");
      expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(screen.queryByText(/successfully/)).not.toBeInTheDocument();
      if (verb === "deleted") {
        // Gone on the server, so it is not shown from the stale summary either.
        expect(screen.queryByRole("article", { name: "Hobbies" })).not.toBeInTheDocument();
      }
    });

    it("keeps an edit open with its values and a single error when saving fails", async () => {
      vi.mocked(categoryService.updateCategory).mockRejectedValue(new Error("network"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.clear(screen.getByLabelText("Category name"));
      await user.type(screen.getByLabelText("Category name"), "Pets");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("Unable to save the category. Please try again.");
      expect(screen.getAllByRole("alert")).toHaveLength(1); // In the form only, not also on the page.
      expect(screen.getByRole("form", { name: "Edit Pet Care" })).toBeInTheDocument();
      expect(screen.getByLabelText("Category name")).toHaveValue("Pets");
      await waitFor(() => expect(alert).toHaveFocus());
    });

    it("keeps server field errors beside their inputs, not in a page banner", async () => {
      vi.mocked(categoryService.createCategory).mockRejectedValue(new ApiError("Validation failed", 400,
        { name: "Category name must be 50 characters or fewer", iconKey: "Choose one of the approved icons" }));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Create category" }));
      await user.type(screen.getByLabelText("Category name"), "Gifts");
      await user.click(within(screen.getByRole("form", { name: "Create category" })).getByRole("button", { name: "Create category" }));

      const name = screen.getByLabelText("Category name");
      await waitFor(() => expect(name).toHaveAttribute("aria-invalid", "true"));
      expect(name).toHaveAccessibleDescription("Category name must be 50 characters or fewer");
      expect(screen.getByRole("radiogroup", { name: "Icon" })).toHaveAccessibleDescription("Choose one of the approved icons");
      expect(name).toHaveValue("Gifts");
      await waitFor(() => expect(name).toHaveFocus());
      expect(document.getElementById("categories-page-notice")).not.toBeInTheDocument();
    });

    it("replaces an old error when a new operation starts, and filters never bring it back", async () => {
      vi.mocked(categoryService.deleteCategory).mockRejectedValue(new Error("network"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));
      expect(await screen.findByRole("alert")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Cancel" }));
      await user.selectOptions(screen.getByLabelText("Filter categories"), "custom");
      await user.selectOptions(screen.getByLabelText("Filter categories"), "all");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("keeps a dismissed success message dismissed until the next change", async () => {
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));
      await screen.findByText("“Pet Care” was updated successfully.");

      await user.click(screen.getByRole("button", { name: "Dismiss message" }));
      await user.type(screen.getByLabelText("Search categories"), "p");
      expect(screen.queryByText(/was updated successfully/)).not.toBeInTheDocument();
    });

    it("returns focus to the edited card even when the new name moves it", async () => {
      const renamed = { ...unusedRow, name: "Zoo trips" };
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([groceriesRow, salaryRow, petCareRow, renamed]));
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 9, name: "Zoo trips" }));
      const user = await renderPage();
      expect(cardNames()[1]).toBe("Hobbies");

      await user.click(screen.getByRole("button", { name: "Edit Hobbies" }));
      await user.clear(screen.getByLabelText("Category name"));
      await user.type(screen.getByLabelText("Category name"), "Zoo trips");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      await waitFor(() => expect(screen.getByRole("button", { name: "Edit Zoo trips" })).toHaveFocus());
      expect(cardNames().at(-1)).toBe("Zoo trips");
    });

    it("moves focus to the list heading when the last category is deleted", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow]))
        .mockResolvedValueOnce(summaryList([]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "All categories" })).toHaveFocus());
      expect(screen.getByText("You don’t have any categories yet.")).toBeInTheDocument();
      expect(await screen.findByText("“Hobbies” was deleted successfully.")).toBeInTheDocument();
    });
  });

  describe("refresh after a change", () => {
    it("keeps the change and offers a retry when the usage summary cannot refresh", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockRejectedValueOnce(new Error("network"))
        .mockResolvedValueOnce(summaryList(allRows));
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));

      const warning = await screen.findByText(/“Pet Care” was updated, but the latest category summary could not be loaded\./);
      expect(warning).toHaveTextContent("Warning: “Pet Care” was updated, but the latest category summary could not be loaded. Try again, or refresh the page to see the current data.");
      expect(warning.closest("[role='status']")).toBeInTheDocument();
      // Honest: neither a plain success nor a failure.
      expect(screen.queryByText(/was updated successfully/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Unable to save/)).not.toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(card("Pet Care")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Try again" }));
      await waitFor(() => expect(screen.queryByText(/could not be loaded/)).not.toBeInTheDocument());
    });

    it("keeps the change and warns when the shared category list cannot refresh", async () => {
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();
      vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));

      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));

      expect(await screen.findByText(/Category options could not be refreshed. Your changes were saved./))
        .toBeInTheDocument();
      expect(screen.getByText("“Pet Care” was updated successfully.")).toBeInTheDocument();
    });
  });
});
