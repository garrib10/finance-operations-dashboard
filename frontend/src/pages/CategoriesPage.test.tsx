vi.mock("../context/AuthContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../context/AuthContext")>()),
  useAuth: vi.fn(),
}));
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategorySummary: vi.fn(),
}));

import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../context/AuthContext";
import { getCategorySummary } from "../services/categoryService";
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

async function renderPage() {
  render(<CategoriesPage />);
  await screen.findByRole("heading", { name: "All categories" });
}

const card = (name: string) => screen.getByRole("article", { name });
/** The card's activity line as one string (the date is wrapped to keep it together). */
const activity = (name: string) => card(name).querySelector(".category-card__usage");

describe("CategoriesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuth).mockReturnValue(accountContext());
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList(allRows));
  });

  it("has one page heading and shows loading until the summary arrives", async () => {
    render(<CategoriesPage />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading categories…");
    await screen.findByRole("heading", { name: "All categories" });
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
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

  it("has no management controls yet", async () => {
    await renderPage();

    for (const article of screen.getAllByRole("article")) {
      expect(within(article).queryByRole("button")).not.toBeInTheDocument();
      expect(within(article).queryByRole("link")).not.toBeInTheDocument();
    }
  });

  it("explains a load failure and retries", async () => {
    const user = userEvent.setup();
    vi.mocked(getCategorySummary)
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(summaryList(allRows));
    render(<CategoriesPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to load your categories. Please try again.");
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("heading", { name: "All categories" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an empty state when there are no categories", async () => {
    vi.mocked(getCategorySummary).mockResolvedValue(summaryList([]));
    render(<CategoriesPage />);

    expect(await screen.findByText("You don’t have any categories yet.")).toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });
});
