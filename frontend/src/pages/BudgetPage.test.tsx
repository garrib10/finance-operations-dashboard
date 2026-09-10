import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import BudgetPage from "./BudgetPage";
import { ApiError } from "../services/api";
import {
  createBudget,
  deleteBudget,
  getBudgetAnalytics,
  getBudgets,
  updateBudget,
} from "../services/budgetService";
import { getCategories } from "../services/categoryService";

import type { BudgetAnalyticsResponse, BudgetResponse } from "../types/budget";
import type { CategoryResponse } from "../types/category";

vi.mock("../services/budgetService", () => ({
  createBudget: vi.fn(),
  deleteBudget: vi.fn(),
  getBudgetAnalytics: vi.fn(),
  getBudgets: vi.fn(),
  updateBudget: vi.fn(),
}));

vi.mock("../services/categoryService", () => ({
  getCategories: vi.fn(),
}));

/*
 * Recharts depends on browser layout measurements that jsdom does not provide.
 * These lightweight mocks keep BudgetPage tests focused on page behavior rather
 * than testing the charting library itself.
 */
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  BarChart: ({ children }: { children: ReactNode }) => (
    <div data-testid="bar-chart">{children}</div>
  ),
  CartesianGrid: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Bar: ({ name }: { name?: string }) => <div>{name}</div>,
}));

const mockGetBudgets = vi.mocked(getBudgets);
const mockGetBudgetAnalytics = vi.mocked(getBudgetAnalytics);
const mockCreateBudget = vi.mocked(createBudget);
const mockUpdateBudget = vi.mocked(updateBudget);
const mockDeleteBudget = vi.mocked(deleteBudget);
const mockGetCategories = vi.mocked(getCategories);

const today = new Date();
const currentMonth = today.getMonth() + 1;
const currentYear = today.getFullYear();

const groceriesBudget: BudgetResponse = {
  id: 1,
  categoryId: 1,
  categoryName: "Groceries",
  monthlyLimit: 500,
  month: currentMonth,
  year: currentYear,
  createdAt: `${currentYear}-01-01T12:00:00`,
  updatedAt: `${currentYear}-01-01T12:00:00`,
};

const diningBudget: BudgetResponse = {
  id: 2,
  categoryId: 2,
  categoryName: "Dining",
  monthlyLimit: 50,
  month: currentMonth,
  year: currentYear,
  createdAt: `${currentYear}-01-01T12:00:00`,
  updatedAt: `${currentYear}-01-01T12:00:00`,
};

const groceriesAnalytics: BudgetAnalyticsResponse = {
  budgetId: 1,
  categoryId: 1,
  categoryName: "Groceries",
  monthlyLimit: 500,
  amountSpent: 50,
  amountRemaining: 450,
  percentageUsed: 10,
  status: "ON_TRACK",
  month: currentMonth,
  year: currentYear,
};

const diningAnalytics: BudgetAnalyticsResponse = {
  budgetId: 2,
  categoryId: 2,
  categoryName: "Dining",
  monthlyLimit: 50,
  amountSpent: 15,
  amountRemaining: 35,
  percentageUsed: 30,
  status: "ON_TRACK",
  month: currentMonth,
  year: currentYear,
};

const categories = [
  {
    id: 1,
    name: "Groceries",
  },
  {
    id: 2,
    name: "Dining",
  },
] as unknown as CategoryResponse[];

function mockAnalyticsForLoadedBudgets(): void {
  mockGetBudgetAnalytics.mockImplementation(async (id: number) => {
    if (id === groceriesBudget.id) {
      return groceriesAnalytics;
    }

    return diningAnalytics;
  });
}

describe("BudgetPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    Object.defineProperty(window, "scrollTo", {
      configurable: true,
      value: vi.fn(),
    });

    mockGetCategories.mockResolvedValue(categories);
    mockCreateBudget.mockResolvedValue(groceriesBudget);
    mockUpdateBudget.mockResolvedValue(groceriesBudget);
    mockDeleteBudget.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the loading state while budget data is being fetched", () => {
    mockGetBudgets.mockReturnValue(new Promise(() => {}));
    mockGetCategories.mockReturnValue(new Promise(() => {}));

    render(<BudgetPage />);

    expect(screen.getByText("Loading budgets...")).toBeInTheDocument();
  });

  it("renders budgets, analytics, statuses, and charts for the selected period", async () => {
    mockGetBudgets.mockResolvedValue([groceriesBudget, diningBudget]);
    mockAnalyticsForLoadedBudgets();

    render(<BudgetPage />);

    expect(
      await screen.findByRole("heading", { name: "Monthly Budgets" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "Groceries" }),
    ).toBeInTheDocument();

    expect(screen.getByRole("heading", { name: "Dining" })).toBeInTheDocument();

    expect(screen.getAllByText("On Track")).toHaveLength(2);

    expect(screen.getByText("$500.00")).toBeInTheDocument();
    expect(screen.getByText("$450.00")).toBeInTheDocument();
    expect(screen.getByText("10.0%")).toBeInTheDocument();
    expect(screen.getByText("30.0%")).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "Budget vs. Spending" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "Budget Utilization" }),
    ).toBeInTheDocument();

    expect(screen.getAllByTestId("bar-chart")).toHaveLength(2);
  });

  it("filters budgets by month and shows the empty state for a period with no budgets", async () => {
    const user = userEvent.setup();

    mockGetBudgets.mockResolvedValue([groceriesBudget, diningBudget]);
    mockAnalyticsForLoadedBudgets();

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Groceries" });

    const filterMonthSelect = screen.getAllByLabelText("Month")[1];
    const emptyMonth = currentMonth === 1 ? 2 : 1;

    await user.selectOptions(filterMonthSelect, String(emptyMonth));

    expect(screen.getByText(/No budgets found for/i)).toBeInTheDocument();

    expect(
      screen.queryByRole("heading", { name: "Groceries" }),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole("heading", { name: "Budget vs. Spending" }),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole("heading", { name: "Budget Utilization" }),
    ).not.toBeInTheDocument();
  });

  it("creates a budget and reloads the budget data", async () => {
    const user = userEvent.setup();

    mockGetBudgets
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([groceriesBudget]);

    mockGetBudgetAnalytics.mockResolvedValue(groceriesAnalytics);

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Create Budget" });

    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.type(screen.getByLabelText("Monthly Limit"), "500");

    await user.click(screen.getByRole("button", { name: "Create Budget" }));

    await waitFor(() => {
      expect(mockCreateBudget).toHaveBeenCalledWith({
        categoryId: 1,
        monthlyLimit: 500,
        month: currentMonth,
        year: currentYear,
      });
    });

    await waitFor(() => {
      expect(mockGetBudgets).toHaveBeenCalledTimes(2);
    });
  });

  it("loads a budget into edit mode and updates it", async () => {
    const user = userEvent.setup();

    mockGetBudgets.mockResolvedValue([groceriesBudget]);
    mockGetBudgetAnalytics.mockResolvedValue(groceriesAnalytics);

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Groceries" });

    await user.click(screen.getByRole("button", { name: "Edit" }));

    expect(
      screen.getByRole("heading", { name: "Edit Budget" }),
    ).toBeInTheDocument();

    expect(screen.getByLabelText("Category")).toHaveValue("1");
    expect(screen.getByLabelText("Monthly Limit")).toHaveValue(500);

    const monthlyLimitInput = screen.getByLabelText("Monthly Limit");

    await user.clear(monthlyLimitInput);
    await user.type(monthlyLimitInput, "600");

    await user.click(screen.getByRole("button", { name: "Update Budget" }));

    await waitFor(() => {
      expect(mockUpdateBudget).toHaveBeenCalledWith(1, {
        categoryId: 1,
        monthlyLimit: 600,
        month: currentMonth,
        year: currentYear,
      });
    });
  });

  it("cancels edit mode and resets the form", async () => {
    const user = userEvent.setup();

    mockGetBudgets.mockResolvedValue([groceriesBudget]);
    mockGetBudgetAnalytics.mockResolvedValue(groceriesAnalytics);

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Groceries" });

    await user.click(screen.getByRole("button", { name: "Edit" }));

    expect(
      screen.getByRole("heading", { name: "Edit Budget" }),
    ).toBeInTheDocument();

    expect(screen.getByLabelText("Category")).toHaveValue("1");
    expect(screen.getByLabelText("Monthly Limit")).toHaveValue(500);

    await user.click(screen.getByRole("button", { name: "Cancel Edit" }));

    expect(
      screen.getByRole("heading", { name: "Create Budget" }),
    ).toBeInTheDocument();

    expect(screen.getByLabelText("Category")).toHaveValue("");
    expect(screen.getByLabelText("Monthly Limit")).toHaveValue(null);

    expect(
      screen.queryByRole("button", { name: "Cancel Edit" }),
    ).not.toBeInTheDocument();
  });

  it("deletes a budget after confirmation", async () => {
    const user = userEvent.setup();

    mockGetBudgets
      .mockResolvedValueOnce([groceriesBudget])
      .mockResolvedValueOnce([]);

    mockGetBudgetAnalytics.mockResolvedValue(groceriesAnalytics);

    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Groceries" });

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(confirmSpy).toHaveBeenCalledWith("Delete the Groceries budget?");

    await waitFor(() => {
      expect(mockDeleteBudget).toHaveBeenCalledWith(1);
    });

    await waitFor(() => {
      expect(mockGetBudgets).toHaveBeenCalledTimes(2);
    });
  });

  it("does not delete a budget when confirmation is cancelled", async () => {
    const user = userEvent.setup();

    mockGetBudgets.mockResolvedValue([groceriesBudget]);
    mockGetBudgetAnalytics.mockResolvedValue(groceriesAnalytics);

    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Groceries" });

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(confirmSpy).toHaveBeenCalledWith("Delete the Groceries budget?");
    expect(mockDeleteBudget).not.toHaveBeenCalled();

    expect(
      screen.getByRole("heading", { name: "Groceries" }),
    ).toBeInTheDocument();
  });

  it("shows a duplicate-budget business-rule error returned by the API", async () => {
    const user = userEvent.setup();

    mockGetBudgets.mockResolvedValue([]);

    mockCreateBudget.mockRejectedValue(
      new ApiError("Budget already exists for this category and month", 409),
    );

    render(<BudgetPage />);

    await screen.findByRole("heading", { name: "Create Budget" });

    await user.selectOptions(screen.getByLabelText("Category"), "1");
    await user.type(screen.getByLabelText("Monthly Limit"), "500");

    await user.click(screen.getByRole("button", { name: "Create Budget" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Budget already exists for this category and month",
    );
  });

  it("shows an API error when the budget page cannot load", async () => {
    mockGetBudgets.mockRejectedValue(
      new ApiError("Unable to load budgets.", 500),
    );

    render(<BudgetPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load budgets.",
    );
  });
});
