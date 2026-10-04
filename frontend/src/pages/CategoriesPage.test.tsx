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
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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
import { CATEGORIES_OTHERS_EXPANDED_KEY } from "../utils/categoriesSectionPreference";
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
type User = ReturnType<typeof userEvent.setup>;
/** The card's "More actions for {name}" disclosure trigger. */
const actionsTrigger = (name: string) => screen.getByRole("button", { name: `More actions for ${name}` });
/** Opens a card's actions and chooses Edit or Delete, as a user would. */
async function chooseAction(user: User, name: string, action: "Edit" | "Delete") {
  await user.click(actionsTrigger(name));
  await user.click(screen.getByRole("button", { name: `${action} ${name}` }));
}
const cardNames = () => screen.queryAllByRole("article").map((article) => within(article).getByRole("heading").textContent);

describe("CategoriesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Most tests are about workflows, not sections, so they start with "Other categories"
    // open (the user's saved choice); the "sections" tests start from the default instead.
    window.localStorage.setItem(CATEGORIES_OTHERS_EXPANDED_KEY, "true");
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
    // "No budget" replaces "With spending"; nothing here spends without a budget.
    expect(within(strip).queryByText("With spending")).not.toBeInTheDocument();
    expect(within(strip).getByText("No budget").nextElementSibling).toHaveTextContent(/^0$/);
    expect(within(strip).getByText("categories spending in October 2026 without a budget")).toBeInTheDocument();
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
    // This month's count (4), not the all-time 6, and no budget count.
    expect(activity("Groceries")).toHaveTextContent(/^4 transactions in October · Last used Oct 12, 2026$/);
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

  it("describes this month's activity: singular, plural, zero, and never used", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([unusedRow,
      summaryRow({ id: 31, name: "Gifts", transactionCount: 1, currentMonthTransactionCount: 1,
        lastTransactionDate: "2026-10-03" }),
      summaryRow({ id: 32, name: "Coffee", transactionCount: 9, currentMonthTransactionCount: 2,
        lastTransactionDate: "2026-10-03" }),
      // Busy in the past, quiet this month: zero now, with its real last-used date.
      summaryRow({ id: 33, name: "Holidays", transactionCount: 5, currentMonthTransactionCount: 0,
        budgetCount: 2, canDelete: false, lastTransactionDate: "2026-09-30" }),
    ]));
    await renderPage();

    expect(activity("Hobbies")).toHaveTextContent(/^Not used yet$/);
    expect(activity("Gifts")).toHaveTextContent(/^1 transaction in October · Last used Oct 3, 2026$/);
    expect(activity("Coffee")).toHaveTextContent(/^2 transactions in October · Last used Oct 3, 2026$/);
    expect(activity("Holidays")).toHaveTextContent(/^0 transactions in October · Last used Sep 30, 2026$/);
    expect(screen.queryByText(/budgets? ·|· \d+ budgets?/)).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/null|Invalid Date/);
  });

  it("names the server's reporting month in the activity line, not the browser's", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([groceriesRow], 3, 2025));
    await renderPage();

    expect(activity("Groceries")).toHaveTextContent(/^4 transactions in March · /);
  });

  it("keeps a quiet but historically used category undeletable, with its all-time reason", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([summaryRow({
      id: 33, name: "Holidays", transactionCount: 5, currentMonthTransactionCount: 0,
      budgetCount: 2, canDelete: false, lastTransactionDate: "2026-09-30",
    })]));
    const user = await renderPage();
    await user.click(actionsTrigger("Holidays"));

    const remove = screen.getByRole("button", { name: "Delete Holidays" });
    expect(remove).toHaveAttribute("aria-disabled", "true");
    expect(remove).toHaveAccessibleDescription(
      "Used by 5 transactions and 2 budgets. Change or remove those first to delete this category.");
  });

  it("uses the account's date format", async () => {
    vi.mocked(useAuth).mockReturnValue(accountContext({
      ...accountUser, preferences: { ...accountUser.preferences, dateFormat: "ISO" },
    }));
    await renderPage();

    expect(within(card("Pet Care")).getByText(/Last used 2026-10-02/)).toBeInTheDocument();
  });

  it("lists active categories first, each section by name by default, whatever order the API returns", async () => {
    await renderPage();

    expect(cardNames()).toEqual(["Groceries", "Pet Care", "Hobbies", "Income"]);
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

  it("offers no actions for built-in categories, and one actions button for custom ones", async () => {
    await renderPage();

    for (const name of ["Groceries", "Income"]) {
      // No disclosure at all (not a disabled one), but the links remain.
      expect(within(card(name)).queryByRole("button")).not.toBeInTheDocument();
      expect(within(card(name)).getByRole("link", { name: `View transactions for ${name}` })).toBeInTheDocument();
    }
    expect(within(card("Pet Care")).getAllByRole("button")).toEqual([actionsTrigger("Pet Care")]);
    // Edit and Delete live inside the closed disclosure, not on the card.
    expect(screen.queryByRole("button", { name: "Edit Pet Care" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete Pet Care" })).not.toBeInTheDocument();
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
      ["All categories", ["Groceries", "Pet Care", "Hobbies", "Income"]],
    ])("filters to %s", async (option, expected) => {
      const user = await renderPage();
      await user.selectOptions(screen.getByLabelText("Filter categories"), screen.getByRole("option", { name: option }));

      expect(cardNames()).toEqual(expected);
      expect(screen.getByText(`Showing ${expected.length} of 4 categories`)).toBeInTheDocument();
    });

    it.each([
      ["This month’s spending", ["Groceries", "Pet Care", "Hobbies", "Income"]],
      ["Most used", ["Groceries", "Pet Care", "Income", "Hobbies"]],
      ["Name", ["Groceries", "Pet Care", "Hobbies", "Income"]],
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
      expect(cardNames()).toEqual(["Groceries", "Pet Care", "Hobbies", "Income"]);
      expect(screen.queryByRole("button", { name: "Clear category filters" })).not.toBeInTheDocument();
      await waitFor(() => expect(search()).toHaveFocus());
    });

    it("keeps a category being edited visible, with an explanation, while the toolbar changes", async () => {
      const user = await renderPage();
      await chooseAction(user, "Pet Care", "Edit");
      await user.type(screen.getByLabelText("Category name"), "s");
      await user.type(search(), "groc");

      expect(screen.getByRole("form", { name: "Edit Pet Care" })).toBeInTheDocument();
      expect(screen.getByLabelText("Category name")).toHaveValue("Pet Cares"); // Nothing lost.
      expect(cardNames()).toEqual(["Groceries", "Pet Care"]);
      expect(screen.getByText(/“Pet Care” is shown because you’re working on it/)).toBeInTheDocument();

      // After Cancel it stays until the toolbar next changes, so focus has somewhere to go.
      await user.click(screen.getByRole("button", { name: "Cancel" }));
      await waitFor(() => expect(actionsTrigger("Pet Care")).toHaveFocus());
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
      await chooseAction(user, "Pet Care", "Edit");
      await user.clear(screen.getByLabelText("Category name"));
      await user.type(screen.getByLabelText("Category name"), "Animals");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      await waitFor(() => expect(actionsTrigger("Animals")).toHaveFocus());
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
      await chooseAction(user, "Hobbies", "Delete");
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      // Unfiltered, "Income" would follow "Hobbies"; with the Custom filter it is "Pet Care".
      await waitFor(() => expect(screen.getByRole("heading", { name: "Pet Care" })).toHaveFocus());
      expect(cardNames()).toEqual(["Pet Care"]);
    });
  });

  describe("spending without a budget", () => {
    // Books (custom) and Dining (built-in) spend with no budget this month; Tolls spends but
    // takes no budgets; Side Gigs only had income; the default rows have budgets or no spending.
    const books = summaryRow({ id: 20, name: "Books", iconKey: "graduation-cap", currentMonthSpent: 125.5,
      transactionCount: 4, currentMonthTransactionCount: 2, budgetCount: 1, canDelete: false,
      lastTransactionDate: "2026-10-05" });
    const dining = summaryRow({ id: 21, name: "Dining", iconKey: "utensils", builtIn: true, canDelete: false,
      currentMonthSpent: 1, transactionCount: 1, currentMonthTransactionCount: 1, lastTransactionDate: "2026-10-04" });
    const tolls = summaryRow({ id: 22, name: "Tolls", budgetEnabled: false, currentMonthSpent: 40, canDelete: false,
      transactionCount: 1, currentMonthTransactionCount: 1, lastTransactionDate: "2026-10-06" });
    const sideGigs = summaryRow({ id: 23, name: "Side Gigs", currentMonthSpent: 0, canDelete: false,
      transactionCount: 1, currentMonthTransactionCount: 1, lastTransactionDate: "2026-10-07" });
    const rows = [...allRows, books, dining, tolls, sideGigs];
    const noBudgetCount = () => screen.getByText("No budget", { selector: "dt" }).nextElementSibling;
    const warning = (name: string) => within(card(name)).queryByText(/spent in \w+ with no budget\./);

    beforeEach(() => {
      vi.mocked(getCategorySummary).mockResolvedValue(summaryList(rows));
    });

    it("warns on a qualifying card, with the card's only Set budget link inside the warning", async () => {
      await renderPage();

      const text = warning("Books")!;
      expect(text).toHaveTextContent("Warning: $125.50 spent in October with no budget. Set budget");
      const links = within(card("Books")).getAllByRole("link", { name: "Set budget for Books" });
      expect(links).toHaveLength(1);
      expect(text).toContainElement(links[0]);
      expect(links[0]).toHaveAttribute("href", "/budgets?category=20");
      expect(links[0].querySelector("svg")).toBeNull(); // Plain text link inside the warning.
      // The other links and the actions stay.
      expect(within(card("Books")).getByRole("link", { name: "View transactions for Books" })).toBeInTheDocument();
      expect(actionsTrigger("Books")).toBeInTheDocument();
      expect(warning("Dining")).toHaveTextContent("$1.00 spent in October with no budget.");
    });

    it("keeps the warnings static, so several cards are not announced", async () => {
      await renderPage();

      for (const name of ["Books", "Dining"]) {
        const notice = warning(name)!.closest<HTMLElement>(".inline-notice")!;
        expect(notice).not.toHaveAttribute("role");
        expect(notice).not.toHaveAttribute("aria-live");
        expect(within(notice).getByText("Warning:")).toBeVisible(); // Not colour alone.
        expect(notice.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
      }
      expect(within(card("Books")).queryByRole("alert")).not.toBeInTheDocument();
      expect(within(card("Books")).queryByRole("status")).not.toBeInTheDocument();
    });

    it("leaves other cards' budget actions as they were", async () => {
      await renderPage();

      // No spending: the ordinary Set budget link, no warning.
      expect(warning("Hobbies")).toBeNull();
      expect(within(card("Hobbies")).getByRole("link", { name: "Set budget for Hobbies" })).toBeInTheDocument();
      // A budget this month: Edit budget, no warning, no Set budget.
      expect(warning("Groceries")).toBeNull();
      expect(within(card("Groceries")).getByRole("link", { name: "Edit budget for Groceries" })).toBeInTheDocument();
      expect(within(card("Groceries")).queryByRole("link", { name: /Set budget/ })).not.toBeInTheDocument();
      // Takes no budgets: no warning and no budget action.
      expect(warning("Tolls")).toBeNull();
      expect(within(card("Tolls")).queryByRole("link", { name: /budget/ })).not.toBeInTheDocument();
      // Income only this month is not spending.
      expect(warning("Side Gigs")).toBeNull();
      expect(within(card("Side Gigs")).getByRole("link", { name: "Set budget for Side Gigs" })).toBeInTheDocument();
    });

    it("names the server's reporting month", async () => {
      vi.mocked(getCategorySummary).mockResolvedValue(summaryList([books], 3, 2025));
      await renderPage();

      expect(warning("Books")).toHaveTextContent("$125.50 spent in March with no budget.");
    });

    it("counts every qualifying category, whatever the search, filter, or sort shows", async () => {
      const user = await renderPage();
      expect(noBudgetCount()).toHaveTextContent(/^2$/);

      await user.selectOptions(screen.getByLabelText("Filter categories"), "builtIn");
      expect(screen.queryByRole("article", { name: "Books" })).not.toBeInTheDocument(); // Hidden but counted.
      expect(noBudgetCount()).toHaveTextContent(/^2$/);

      await user.selectOptions(screen.getByLabelText("Filter categories"), "all");
      await user.type(screen.getByLabelText("Search categories"), "groc");
      expect(noBudgetCount()).toHaveTextContent(/^2$/);

      await user.selectOptions(screen.getByLabelText("Sort categories"), "monthSpending");
      expect(noBudgetCount()).toHaveTextContent(/^2$/);
      // The spending table still lists every category with spending.
      expect(within(screen.getByRole("table")).getByRole("rowheader", { name: "Books" })).toBeInTheDocument();
    });

    it("follows a refreshed summary, and keeps the last data when a refresh fails", async () => {
      const budgeted = { ...books, name: "Books", currentMonthBudget: groceriesRow.currentMonthBudget };
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(rows))
        .mockResolvedValueOnce(summaryList([...allRows, budgeted, dining, tolls, sideGigs]))
        .mockRejectedValueOnce(new Error("network"));
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 20, name: "Books" }));
      const user = await renderPage();
      expect(noBudgetCount()).toHaveTextContent(/^2$/);

      // A successful change reloads the summary: Books now has a budget (set elsewhere).
      await chooseAction(user, "Books", "Edit");
      await user.click(screen.getByRole("button", { name: "Save category" }));
      await screen.findByText("“Books” was updated successfully.");
      expect(noBudgetCount()).toHaveTextContent(/^1$/);
      expect(warning("Books")).toBeNull();

      // A later change whose refresh fails keeps the last confirmed figures and says so.
      await chooseAction(user, "Books", "Edit");
      await user.click(screen.getByRole("button", { name: "Save category" }));
      expect(await screen.findByText(/latest category summary could not be loaded/)).toBeInTheDocument();
      expect(noBudgetCount()).toHaveTextContent(/^1$/);
      expect(warning("Dining")).toHaveTextContent("$1.00 spent in October with no budget.");
    });
  });

  describe("page-wide checks", () => {
    // Spotify spends with no budget, so a static warning is on screen too.
    const spotify = summaryRow({ id: 40, name: "Spotify", iconKey: "music", currentMonthSpent: 12,
      transactionCount: 1, currentMonthTransactionCount: 1, canDelete: false, lastTransactionDate: "2026-10-01" });

    beforeEach(() => {
      vi.mocked(getCategorySummary).mockResolvedValue(summaryList([...allRows, spotify]));
    });

    it("keeps the summary strip, spending table, and cards in agreement", async () => {
      await renderPage();
      const strip = screen.getByText("Categories", { selector: "dt" }).closest("dl")!;
      const table = screen.getByRole("table");
      const tableSpent = (name: string) =>
        within(within(table).getByRole("rowheader", { name }).closest("tr")!).getAllByRole("cell")[0].textContent;
      const cardSpent = (name: string) =>
        within(card(name)).getByText(/^Spent in /).nextElementSibling!.textContent;

      for (const name of ["Groceries", "Pet Care", "Spotify"]) {
        expect(cardSpent(name)).toBe(tableSpent(name));
      }
      // Top category, total, and the No budget count all describe the same five categories.
      expect(within(strip).getByText("$300.00 of $412.00")).toBeInTheDocument();
      expect(within(table).getAllByRole("rowheader")).toHaveLength(3);
      expect(within(strip).getByText("No budget").nextElementSibling).toHaveTextContent(/^1$/);
      expect(within(strip).getByText("3 custom · 2 built-in")).toBeInTheDocument();
      expect(screen.getAllByText(/spent in October with no budget\./)).toHaveLength(1);
    });

    it("has an accessible structure: headings, names, no menus, no positive tab order", async () => {
      const user = await renderPage();
      await user.click(actionsTrigger("Pet Care"));

      expect(screen.getAllByRole("heading", { level: 1 }).map((h) => h.textContent)).toEqual(["Categories"]);
      expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual([
        "Spending in October 2026", "All categories", "Active this month", "Other categories · 2",
      ]);
      // Disclosures, not ARIA menus; the open one is reported as expanded.
      expect(screen.queryByRole("menu")).not.toBeInTheDocument();
      expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
      expect(actionsTrigger("Pet Care")).toHaveAttribute("aria-expanded", "true");
      expect(document.getElementById(actionsTrigger("Pet Care").getAttribute("aria-controls")!)).toBeVisible();
      // Every control has a name, and nothing jumps the natural tab order.
      for (const control of [...screen.getAllByRole("button"), ...screen.getAllByRole("link")]) {
        expect(control).toHaveAccessibleName();
      }
      expect(document.querySelectorAll("[tabindex]:not([tabindex='-1']):not([tabindex='0'])")).toHaveLength(0);
      // The card warning is static; only page messages may be live regions.
      const warning = screen.getByText(/spent in October with no budget\./).closest<HTMLElement>(".inline-notice")!;
      expect(warning).not.toHaveAttribute("role");
      expect(warning.closest("[aria-live]")).toBeNull();
    });
  });

  describe("sections", () => {
    // Groceries and Pet Care are active (spending or a budget this month); Hobbies and
    // Income are not. These tests start from the default: "Other categories" closed.
    const KEY = CATEGORIES_OTHERS_EXPANDED_KEY;
    const activeSection = () => screen.getByRole("region", { name: "Active this month" });
    const otherSection = () => screen.getByRole("region", { name: /^Other categories · \d+$/ });
    const namesIn = (section: HTMLElement) =>
      within(section).queryAllByRole("article").map((article) => within(article).getByRole("heading").textContent);
    const toggle = () => screen.queryByRole("button", { name: /(Show|Hide) other categories/ });

    beforeEach(() => {
      window.localStorage.removeItem(KEY);
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it("shows Active this month first, then a closed Other categories, each category once", async () => {
      await renderPage();

      const regions = screen.getAllByRole("region").filter((region) => /categories-(active|other)-heading/.test(region.getAttribute("aria-labelledby") ?? ""));
      expect(regions).toEqual([activeSection(), otherSection()]);
      expect(namesIn(activeSection())).toEqual(["Groceries", "Pet Care"]);
      expect(within(otherSection()).getByRole("heading", { level: 2 })).toHaveTextContent("Other categories · 2");
      expect(namesIn(otherSection())).toEqual([]); // Closed: not rendered, so not focusable.
      expect(toggle()).toHaveAccessibleName("Show other categories");
      expect(toggle()).toHaveAttribute("aria-expanded", "false");
      expect(toggle()).toHaveAttribute("aria-controls", "categories-other-list");
      expect(document.getElementById("categories-other-list")).not.toBeVisible();
      expect(screen.getByText("Showing 2 of 4 categories")).toBeInTheDocument();
    });

    it("opens and closes with the toggle, saving only the user's choice", async () => {
      const user = await renderPage();
      await user.click(toggle()!);

      expect(toggle()).toHaveAccessibleName("Hide other categories");
      expect(toggle()).toHaveAttribute("aria-expanded", "true");
      expect(namesIn(otherSection())).toEqual(["Hobbies", "Income"]);
      expect(window.localStorage.getItem(KEY)).toBe("true");
      // No category appears twice anywhere on the page.
      expect(cardNames()).toEqual(["Groceries", "Pet Care", "Hobbies", "Income"]);

      await user.click(toggle()!);
      expect(namesIn(otherSection())).toEqual([]);
      expect(window.localStorage.getItem(KEY)).toBe("false");
    });

    it.each([["true", true], ["false", false], ["yes", false]])("restores a saved %j as %s", async (stored, open) => {
      window.localStorage.setItem(KEY, stored);
      await renderPage();

      expect(toggle()).toHaveAttribute("aria-expanded", String(open));
    });

    it("works without storage: unreadable means closed, and the toggle still works", async () => {
      vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
      vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("blocked"); });
      const user = await renderPage();

      expect(toggle()).toHaveAttribute("aria-expanded", "false");
      await user.click(toggle()!);
      expect(namesIn(otherSection())).toEqual(["Hobbies", "Income"]);
    });

    it("opens Other categories when nothing is active, without saving that", async () => {
      vi.mocked(getCategorySummary).mockResolvedValue(summaryList([salaryRow, unusedRow], 9, 2026));
      await renderPage();

      expect(within(activeSection()).getByText("Nothing has spending or a budget in September yet.")).toBeInTheDocument();
      expect(namesIn(otherSection())).toEqual(["Hobbies", "Income"]);
      expect(toggle()).toBeNull(); // Nothing to hide.
      expect(window.localStorage.getItem(KEY)).toBeNull();
    });

    it("keeps the account-level empty state when there are no categories at all", async () => {
      vi.mocked(getCategorySummary).mockResolvedValue(summaryList([]));
      renderWithProviders();

      expect(await screen.findByText("You don’t have any categories yet.")).toBeInTheDocument();
      expect(screen.queryByRole("region", { name: "Active this month" })).not.toBeInTheDocument();
    });

    it("opens while creating and shows the new category there, focused, without saving", async () => {
      const created = summaryRow({ id: 60, name: "Gifts", iconKey: "gift" });
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([created, ...allRows]));
      vi.mocked(categoryService.createCategory).mockResolvedValue(category({ id: 60, name: "Gifts", iconKey: "gift" }));
      const user = await renderPage();

      await user.click(screen.getByRole("button", { name: "Create category" }));
      expect(namesIn(otherSection())).toEqual(["Hobbies", "Income"]);
      expect(toggle()).toBeNull(); // Required open while the form is up.

      await user.type(screen.getByLabelText("Category name"), "Gifts");
      await user.click(within(screen.getByRole("form", { name: "Create category" })).getByRole("button", { name: "Create category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "Gifts" })).toHaveFocus());
      expect(namesIn(otherSection())).toEqual(["Gifts", "Hobbies", "Income"]);
      expect(namesIn(activeSection())).not.toContain("Gifts"); // Recent is not active.
      expect(window.localStorage.getItem(KEY)).toBeNull();

      // The user's own Hide still works afterwards.
      await user.click(toggle()!);
      expect(namesIn(otherSection())).toEqual([]);
    });

    it("keeps an Other card's edit form and delete confirmation on screen", async () => {
      const user = await renderPage();
      await user.click(toggle()!); // Open it to reach an Other card.

      await chooseAction(user, "Hobbies", "Edit");
      expect(within(otherSection()).getByRole("form", { name: "Edit Hobbies" })).toBeInTheDocument();
      // While the form is up the section is required open, so there is no Hide to press.
      expect(toggle()).toBeNull();
      await user.click(screen.getByRole("button", { name: "Cancel" }));
      await waitFor(() => expect(actionsTrigger("Hobbies")).toHaveFocus());

      await chooseAction(user, "Hobbies", "Delete");
      expect(within(otherSection()).getByRole("group", { name: /Delete “Hobbies”/ })).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Keep category" }));
      await waitFor(() => expect(actionsTrigger("Hobbies")).toHaveFocus());
    });

    it("opens Other categories when a saved card moves there, so focus can follow it", async () => {
      // Pet Care's budget and spending are gone by the time the summary reloads.
      const quiet = { ...petCareRow, currentMonthSpent: 0, currentMonthBudget: null };
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([groceriesRow, salaryRow, quiet, unusedRow]));
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();

      await chooseAction(user, "Pet Care", "Edit");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      await waitFor(() => expect(actionsTrigger("Pet Care")).toHaveFocus());
      expect(namesIn(otherSection())).toContain("Pet Care");
      expect(namesIn(activeSection())).toEqual(["Groceries"]);
      expect(window.localStorage.getItem(KEY)).toBeNull();
    });

    it("focuses the next Other card after a delete, opening the section for it", async () => {
      const created = summaryRow({ id: 60, name: "Gifts", iconKey: "gift" });
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList(allRows))
        .mockResolvedValueOnce(summaryList([created, ...allRows]))
        .mockResolvedValueOnce(summaryList(allRows));
      vi.mocked(categoryService.createCategory).mockResolvedValue(category({ id: 60, name: "Gifts", iconKey: "gift" }));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();
      await user.click(screen.getByRole("button", { name: "Create category" }));
      await user.type(screen.getByLabelText("Category name"), "Gifts");
      await user.click(within(screen.getByRole("form", { name: "Create category" })).getByRole("button", { name: "Create category" }));
      await waitFor(() => expect(screen.getByRole("heading", { name: "Gifts" })).toHaveFocus());

      await chooseAction(user, "Gifts", "Delete");
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "Hobbies" })).toHaveFocus());
      expect(namesIn(otherSection())).toEqual(["Hobbies", "Income"]);
    });

    it("keeps the sections when only the sort changes, sorting inside each", async () => {
      const user = await renderPage();
      await user.click(toggle()!);
      await user.selectOptions(screen.getByLabelText("Sort categories"), "mostUsed");

      expect(namesIn(activeSection())).toEqual(["Groceries", "Pet Care"]); // 6, then 3 transactions.
      expect(namesIn(otherSection())).toEqual(["Income", "Hobbies"]); // 1, then 0.
    });

    it.each([
      ["a search", async (user: User) => user.type(screen.getByLabelText("Search categories"), "o")],
      ["a filter", async (user: User) => user.selectOptions(screen.getByLabelText("Filter categories"), "custom")],
    ])("shows one flat list for %s, with every match visible, then returns to the sections", async (_kind, change) => {
      const user = await renderPage(); // Other categories closed.
      await change(user);

      expect(screen.queryByRole("region", { name: "Active this month" })).not.toBeInTheDocument();
      expect(screen.queryByRole("region", { name: /Other categories/ })).not.toBeInTheDocument();
      expect(new Set(cardNames()).size).toBe(cardNames().length);
      expect(cardNames()).toEqual(expect.arrayContaining(["Hobbies"])); // A closed-section match.
      expect(window.localStorage.getItem(KEY)).toBeNull();

      await user.click(screen.getByRole("button", { name: "Clear category filters" }));
      expect(activeSection()).toBeInTheDocument();
      expect(namesIn(otherSection())).toEqual([]); // Back to the saved (closed) choice.
    });

    it("keeps an edit in progress when a search switches to the flat list", async () => {
      const user = await renderPage();
      await user.click(toggle()!);
      await chooseAction(user, "Hobbies", "Edit");
      await user.type(screen.getByLabelText("Category name"), " club");
      await user.type(screen.getByLabelText("Search categories"), "zzz");

      expect(screen.getByRole("form", { name: "Edit Hobbies" })).toBeInTheDocument();
      expect(screen.getByLabelText("Category name")).toHaveValue("Hobbies club");
    });

    it("never changes the account-level figures when Other categories is closed", async () => {
      const user = await renderPage();
      const strip = screen.getByText("Categories", { selector: "dt" }).closest("dl")!;
      const before = strip.textContent;
      const table = within(screen.getByRole("table")).getAllByRole("rowheader").map((cell) => cell.textContent);

      await user.click(toggle()!);
      await user.click(toggle()!);
      expect(strip.textContent).toBe(before);
      expect(within(screen.getByRole("table")).getAllByRole("rowheader").map((cell) => cell.textContent)).toEqual(table);
      expect(within(strip).getByText("4")).toBeInTheDocument(); // All four categories.
    });
  });

  describe("card actions", () => {
    it("keeps only one card's actions open at a time", async () => {
      const user = await renderPage();
      await user.click(actionsTrigger("Pet Care"));
      await user.click(actionsTrigger("Hobbies"));

      expect(actionsTrigger("Hobbies")).toHaveAttribute("aria-expanded", "true");
      expect(actionsTrigger("Pet Care")).toHaveAttribute("aria-expanded", "false");
    });

    it("closes the actions and opens the edit form in the card when Edit is chosen", async () => {
      const user = await renderPage();
      await chooseAction(user, "Pet Care", "Edit");

      const form = within(card("Pet Care")).getByRole("form", { name: "Edit Pet Care" });
      expect(within(form).getByLabelText("Category name")).toHaveValue("Pet Care");
      // The trigger is hidden while the card shows its own workflow controls.
      expect(screen.queryByRole("button", { name: "More actions for Pet Care" })).not.toBeInTheDocument();
    });

    it("closes open actions when another workflow starts", async () => {
      const user = await renderPage();
      await user.click(actionsTrigger("Hobbies"));
      await user.click(screen.getByRole("button", { name: "Create category" }));

      expect(actionsTrigger("Hobbies")).toHaveAttribute("aria-expanded", "false");
    });

    it.each([
      ["search", async (user: User) => user.type(screen.getByLabelText("Search categories"), "e")],
      ["filter", async (user: User) => user.selectOptions(screen.getByLabelText("Filter categories"), "custom")],
      ["sort", async (user: User) => user.selectOptions(screen.getByLabelText("Sort categories"), "mostUsed")],
    ])("closes open actions when the %s changes", async (_control, change) => {
      const user = await renderPage();
      await user.click(actionsTrigger("Pet Care"));
      await change(user);

      expect(actionsTrigger("Pet Care")).toHaveAttribute("aria-expanded", "false");
    });

    it("shows the transaction and budget actions as real links with decorative icons", async () => {
      await renderPage();

      const links = [
        [card("Groceries"), "View transactions for Groceries", "View transactions", "/transactions?category=1"],
        [card("Hobbies"), "Add a transaction for Hobbies", "Add transaction", "/transactions?addCategory=9"],
        [card("Groceries"), "Edit budget for Groceries", "Edit budget", "/budgets?category=1"],
        [card("Hobbies"), "Set budget for Hobbies", "Set budget", "/budgets?category=9"],
      ] as const;
      for (const [scope, name, text, href] of links) {
        const link = within(scope).getByRole("link", { name });
        expect(link).toHaveAttribute("href", href);
        expect(link).toHaveTextContent(text);
        expect(link.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
        expect(link.querySelector("button")).toBeNull();
        expect(link.closest("button")).toBeNull();
      }
    });
  });

  describe("deleting", () => {
    it("keeps Delete focusable but inactive for a category in use, and explains why", async () => {
      const user = await renderPage();
      await user.click(actionsTrigger("Pet Care"));
      const remove = within(card("Pet Care")).getByRole("button", { name: "Delete Pet Care" });

      expect(remove).toHaveAttribute("aria-disabled", "true");
      expect(remove).toHaveAccessibleDescription(
        "Used by 3 transactions and 1 budget. Change or remove those first to delete this category.");
      remove.focus();
      expect(remove).toHaveFocus();

      // The reason is visible text in the panel, not a tooltip.
      expect(screen.getByText(/Used by 3 transactions and 1 budget\./)).toBeVisible();

      await user.click(remove);
      await user.keyboard("{Enter}");
      await user.keyboard(" ");

      expect(remove).toHaveFocus(); // Nothing happened, and the panel stayed open.
      expect(categoryService.deleteCategory).not.toHaveBeenCalled();
      expect(screen.queryByRole("group", { name: /Delete “Pet Care”/ })).not.toBeInTheDocument();
    });

    it("confirms, deletes, announces, refreshes, and focuses the next card", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow, petCareRow]))
        .mockResolvedValueOnce(summaryList([petCareRow]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();

      await chooseAction(user, "Hobbies", "Delete");
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

      await chooseAction(user, "Hobbies", "Delete");
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "Pet Care" })).toHaveFocus());
    });

    it("focuses the list heading after deleting the only category", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow]))
        .mockResolvedValueOnce(summaryList([]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();

      await chooseAction(user, "Hobbies", "Delete");
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "All categories" })).toHaveFocus());
      expect(screen.getByText("You don’t have any categories yet.")).toBeInTheDocument();
    });

    it("keeps the category and returns focus when the delete is cancelled", async () => {
      const user = await renderPage();
      await chooseAction(user, "Hobbies", "Delete");
      await user.click(screen.getByRole("button", { name: "Keep category" }));

      await waitFor(() => expect(actionsTrigger("Hobbies")).toHaveFocus());
      expect(categoryService.deleteCategory).not.toHaveBeenCalled();
    });

    it("keeps the category and explains when the server says it is now in use", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow]))
        .mockResolvedValueOnce(summaryList([{ ...unusedRow, transactionCount: 1, canDelete: false }]));
      vi.mocked(categoryService.deleteCategory).mockRejectedValue(new ApiError(
        "This category is used by transactions or budgets and cannot be deleted.", 409, undefined, "CATEGORY_IN_USE"));
      const user = await renderPage();

      await chooseAction(user, "Hobbies", "Delete");
      await user.click(screen.getByRole("button", { name: "Delete category" }));

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("“Hobbies” is still used by transactions or budgets, so it can’t be deleted.");
      expect(alert).toHaveTextContent("Change the category on those transactions and budgets, or delete them");
      expect(card("Hobbies")).toBeInTheDocument();
      // The refreshed summary now shows the real usage and blocks Delete.
      await waitFor(() => expect(actionsTrigger("Hobbies")).toHaveFocus());
      await user.click(actionsTrigger("Hobbies"));
      expect(screen.getByRole("button", { name: "Delete Hobbies" })).toHaveAttribute("aria-disabled", "true");
      expect(screen.getByText(/Used by 1 transaction\./)).toBeInTheDocument();

      // It stays until dismissed.
      await user.click(within(alert).getByRole("button", { name: "Dismiss error" }));
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("reports an unexpected delete failure, keeps the category, and keeps the confirmation for a retry", async () => {
      vi.mocked(categoryService.deleteCategory)
        .mockRejectedValueOnce(new Error("network"))
        .mockResolvedValueOnce(undefined);
      const user = await renderPage();
      await chooseAction(user, "Hobbies", "Delete");
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
      await chooseAction(user, "Hobbies", "Delete");
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

      await chooseAction(user, "Pet Care", "Edit");
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
      await waitFor(() => expect(actionsTrigger("Pets")).toHaveFocus());
      expect(getCategorySummary).toHaveBeenCalledTimes(2);
      expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
    });

    it("cancels without saving and returns focus to Edit", async () => {
      const user = await renderPage();
      await chooseAction(user, "Pet Care", "Edit");
      await user.click(screen.getByRole("button", { name: "Cancel" }));

      await waitFor(() => expect(actionsTrigger("Pet Care")).toHaveFocus());
      expect(screen.queryByRole("form")).not.toBeInTheDocument();
      expect(categoryService.updateCategory).not.toHaveBeenCalled();
    });

    it("keeps the form open with the conflict for a duplicate name", async () => {
      vi.mocked(categoryService.updateCategory).mockRejectedValue(
        new ApiError("Category already exists", 409, undefined, "CATEGORY_DUPLICATE"));
      const user = await renderPage();
      await chooseAction(user, "Pet Care", "Edit");
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
      await chooseAction(user, "Pet Care", "Edit");
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
      await chooseAction(user, "Pet Care", "Edit");
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
      await chooseAction(user, "Pet Care", "Edit");
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
        await chooseAction(user, "Hobbies", "Delete");
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
      await chooseAction(user, "Pet Care", "Edit");
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
      await chooseAction(user, "Hobbies", "Delete");
      await user.click(screen.getByRole("button", { name: "Delete category" }));
      expect(await screen.findByRole("alert")).toBeInTheDocument();

      await chooseAction(user, "Pet Care", "Edit");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Cancel" }));
      await user.selectOptions(screen.getByLabelText("Filter categories"), "custom");
      await user.selectOptions(screen.getByLabelText("Filter categories"), "all");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("keeps a dismissed success message dismissed until the next change", async () => {
      vi.mocked(categoryService.updateCategory).mockResolvedValue(category({ id: 7, name: "Pet Care" }));
      const user = await renderPage();
      await chooseAction(user, "Pet Care", "Edit");
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
      expect(cardNames()[2]).toBe("Hobbies"); // First of the other categories.

      await chooseAction(user, "Hobbies", "Edit");
      await user.clear(screen.getByLabelText("Category name"));
      await user.type(screen.getByLabelText("Category name"), "Zoo trips");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      await waitFor(() => expect(actionsTrigger("Zoo trips")).toHaveFocus());
      expect(cardNames().at(-1)).toBe("Zoo trips");
    });

    it("moves focus to the list heading when the last category is deleted", async () => {
      vi.mocked(getCategorySummary)
        .mockResolvedValueOnce(summaryList([unusedRow]))
        .mockResolvedValueOnce(summaryList([]));
      vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
      const user = await renderPage();
      await chooseAction(user, "Hobbies", "Delete");
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

      await chooseAction(user, "Pet Care", "Edit");
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

      await chooseAction(user, "Pet Care", "Edit");
      await user.click(screen.getByRole("button", { name: "Save category" }));

      expect(await screen.findByText(/Category options could not be refreshed. Your changes were saved./))
        .toBeInTheDocument();
      expect(screen.getByText("“Pet Care” was updated successfully.")).toBeInTheDocument();
    });
  });
});
