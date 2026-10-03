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
import { accountContext, accountUser } from "../test/accountFixtures";
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

  it("lists every category in the order the API returns (name, then ID)", async () => {
    await renderPage();

    expect(screen.getAllByRole("article").map((article) => within(article).getByRole("heading").textContent))
      .toEqual(["Groceries", "Income", "Pet Care", "Hobbies"]);
    expect(screen.getByText("4 categories, A–Z")).toBeInTheDocument();
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

    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to load your categories. Please try again.");
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("heading", { name: "All categories" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an empty state when there are no categories", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([]));
    renderWithProviders();

    expect(await screen.findByText("You don’t have any categories yet.")).toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
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
      expect(await screen.findByText("Deleted “Hobbies”.")).toHaveAttribute("role", "status");
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
      await user.click(within(alert).getByRole("button", { name: "Dismiss" }));
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("reports an unexpected delete failure and keeps the category", async () => {
      vi.mocked(categoryService.deleteCategory).mockRejectedValue(new Error("network"));
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Delete Hobbies" }));
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Unable to delete the category. Please try again.");
      expect(card("Hobbies")).toBeInTheDocument();
      await waitFor(() => expect(screen.getByRole("button", { name: "Delete Hobbies" })).toHaveFocus());
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
      expect(await screen.findByText("Saved “Pets”.")).toHaveAttribute("role", "status");
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

      expect(await screen.findByRole("alert")).toHaveTextContent("Built-in categories cannot be changed or deleted.");
      expect(screen.queryByRole("form")).not.toBeInTheDocument();
      await waitFor(() => expect(screen.getByRole("heading", { name: "All categories" })).toHaveFocus());
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
      expect(await screen.findByText("Created “Gifts”.")).toHaveAttribute("role", "status");
      await waitFor(() => expect(screen.getByRole("heading", { name: "Gifts" })).toHaveFocus());
      expect(screen.queryByRole("form")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Dismiss message" }));
      expect(screen.queryByText("Created “Gifts”.")).not.toBeInTheDocument();
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

      const warning = await screen.findByText("Your change was saved, but category usage could not be refreshed.");
      expect(screen.getByText("Saved “Pet Care”.")).toBeInTheDocument();
      expect(screen.queryByText(/Unable to save/)).not.toBeInTheDocument();
      expect(card("Pet Care")).toBeInTheDocument();

      await user.click(within(warning.closest("div")!).getByRole("button", { name: "Refresh usage" }));
      await waitFor(() => expect(screen.queryByText(/could not be refreshed/)).not.toBeInTheDocument());
    });

    it("keeps the change and warns when the shared category list cannot refresh", async () => {
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();
      vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));

      await user.click(screen.getByRole("button", { name: "Edit Pet Care" }));
      await user.click(screen.getByRole("button", { name: "Save category" }));

      expect(await screen.findByText(/Category options could not be refreshed. Your changes were saved./))
        .toBeInTheDocument();
      expect(screen.getByText("Saved “Pet Care”.")).toBeInTheDocument();
    });
  });
});
