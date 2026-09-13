import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import DashboardPage from "./DashboardPage";
import * as DashboardService from "../services/dashboardService";
import type { DashboardResponse } from "../types/dashboard";

vi.mock("../services/dashboardService", () => ({
  getDashboard: vi.fn(),
}));

const mockedGetDashboard = vi.mocked(DashboardService.getDashboard);

const dashboardResponse: DashboardResponse = {
  currentBalance: 3250,
  totalIncome: 5000,
  totalExpenses: 1750,
  monthlyIncome: 3000,
  monthlyExpenses: 950,

  recentTransactions: [
    {
      id: 1,
      categoryId: 1,
      categoryName: "Groceries",
      type: "EXPENSE",
      amount: 75.5,
      description: "Grocery Store",
      transactionDate: "2026-09-09",
    },
  ],

  categorySpending: [
    {
      categoryId: 1,
      categoryName: "Groceries",
      amountSpent: 250,
    },
  ],

  budgetSummaries: [],
};

const budgetSummaries: DashboardResponse["budgetSummaries"] = [
  {
    budgetId: 1,
    categoryId: 1,
    categoryName: "Groceries",
    monthlyLimit: 500,
    amountSpent: 50,
    amountRemaining: 450,
    percentageUsed: 10,
    status: "ON_TRACK",
  },
  {
    budgetId: 2,
    categoryId: 2,
    categoryName: "Dining",
    monthlyLimit: 100,
    amountSpent: 75,
    amountRemaining: 25,
    percentageUsed: 75,
    status: "CAUTION",
  },
  {
    budgetId: 3,
    categoryId: 3,
    categoryName: "Entertainment",
    monthlyLimit: 100,
    amountSpent: 90,
    amountRemaining: 10,
    percentageUsed: 90,
    status: "WARNING",
  },
  {
    budgetId: 4,
    categoryId: 4,
    categoryName: "Shopping",
    monthlyLimit: 100,
    amountSpent: 125,
    amountRemaining: -25,
    percentageUsed: 125,
    status: "OVER_BUDGET",
  },
];

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a loading state while dashboard data is loading", () => {
    mockedGetDashboard.mockReturnValue(new Promise(() => {}));

    render(<DashboardPage />);

    expect(screen.getByText("Loading dashboard...")).toBeInTheDocument();
  });

  it("renders dashboard data after a successful request", async () => {
    mockedGetDashboard.mockResolvedValue(dashboardResponse);

    render(<DashboardPage />);

    expect(await screen.findByText("$3,250.00")).toBeInTheDocument();

    expect(screen.getByTestId("summary-current-balance")).toHaveTextContent(
      "$3,250.00",
    );

    expect(screen.getByTestId("summary-total-income")).toHaveTextContent(
      "$5,000.00",
    );

    expect(screen.getByTestId("summary-total-expenses")).toHaveTextContent(
      "$1,750.00",
    );

    expect(screen.getByTestId("summary-monthly-income")).toHaveTextContent(
      "$3,000.00",
    );

    expect(screen.getByTestId("summary-monthly-expenses")).toHaveTextContent(
      "$950.00",
    );

    expect(screen.getByText("Grocery Store")).toBeInTheDocument();

    expect(screen.getAllByText("Groceries").length).toBeGreaterThan(0);

    expect(screen.getByText("$250.00")).toBeInTheDocument();
  });

  it("renders dashboard empty states", async () => {
    mockedGetDashboard.mockResolvedValue({
      currentBalance: 0,
      totalIncome: 0,
      totalExpenses: 0,
      monthlyIncome: 0,
      monthlyExpenses: 0,
      recentTransactions: [],
      budgetSummaries: [],
      categorySpending: [],
    });

    render(<DashboardPage />);

    expect(await screen.findByText("No transactions yet.")).toBeInTheDocument();

    expect(
      screen.getByText("No category spending this month."),
    ).toBeInTheDocument();

    expect(
      screen.getByText("No budgets have been created for this month."),
    ).toBeInTheDocument();

    expect(screen.getAllByText("$0.00")).toHaveLength(5);
  });

  it("renders every budget status with a human-readable label", async () => {
    mockedGetDashboard.mockResolvedValue({
      ...dashboardResponse,
      budgetSummaries,
    });

    render(<DashboardPage />);

    expect(await screen.findByText("On Track")).toBeInTheDocument();
    expect(screen.getByText("Caution")).toBeInTheDocument();
    expect(screen.getByText("Warning")).toBeInTheDocument();
    expect(screen.getByText("Over Budget")).toBeInTheDocument();

    expect(screen.queryByText("ON_TRACK")).not.toBeInTheDocument();
    expect(screen.queryByText("OVER_BUDGET")).not.toBeInTheDocument();
  });

  it("exposes accessible budget progress values and caps over-budget progress", async () => {
    mockedGetDashboard.mockResolvedValue({
      ...dashboardResponse,
      budgetSummaries,
    });

    render(<DashboardPage />);

    const groceriesProgress = await screen.findByRole("progressbar", {
      name: "Groceries budget utilization",
    });

    expect(groceriesProgress).toHaveAttribute("aria-valuemin", "0");
    expect(groceriesProgress).toHaveAttribute("aria-valuemax", "100");
    expect(groceriesProgress).toHaveAttribute("aria-valuenow", "10");
    expect(groceriesProgress).toHaveAttribute("aria-valuetext", "10.0% used");

    const groceriesBar = groceriesProgress.querySelector(
      ".budget-progress__bar",
    );

    expect(groceriesBar).not.toBeNull();
    expect(groceriesBar).toHaveStyle({ width: "10%" });

    const shoppingProgress = screen.getByRole("progressbar", {
      name: "Shopping budget utilization",
    });

    expect(shoppingProgress).toHaveAttribute("aria-valuemin", "0");
    expect(shoppingProgress).toHaveAttribute("aria-valuemax", "100");
    expect(shoppingProgress).toHaveAttribute("aria-valuenow", "100");
    expect(shoppingProgress).toHaveAttribute("aria-valuetext", "125.0% used");

    const shoppingBar = shoppingProgress.querySelector(".budget-progress__bar");

    expect(shoppingBar).not.toBeNull();
    expect(shoppingBar).toHaveStyle({ width: "100%" });

    expect(screen.getAllByRole("progressbar")).toHaveLength(4);
  });

  it("shows an error when dashboard loading fails", async () => {
    mockedGetDashboard.mockRejectedValue(new Error("Request failed"));

    render(<DashboardPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load dashboard data. Please try again.",
    );
  });
});
