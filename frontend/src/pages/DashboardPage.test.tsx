import { describe, expect, it, vi } from "vitest";
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

describe("DashboardPage", () => {
  it("shows a loading state while dashboard data is loading", () => {
    mockedGetDashboard.mockReturnValue(new Promise(() => {}));

    render(<DashboardPage />);

    expect(screen.getByText("Loading dashboard...")).toBeInTheDocument();
  });

  it("renders dashboard data after a successful request", async () => {
    mockedGetDashboard.mockResolvedValue(dashboardResponse);

    render(<DashboardPage />);

    expect(await screen.findByText("$3,250.00")).toBeInTheDocument();

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

  it("shows an error when dashboard loading fails", async () => {
    mockedGetDashboard.mockRejectedValue(new Error("Request failed"));

    render(<DashboardPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load dashboard data. Please try again.",
    );
  });
});
