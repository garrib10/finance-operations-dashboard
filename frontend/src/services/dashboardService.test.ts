import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { getDashboard } from "./dashboardService";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("dashboardService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the authenticated dashboard summary", async () => {
    const response = {
      currentBalance: 1200,
      totalIncome: 2000,
      totalExpenses: 800,
      monthlyIncome: 1500,
      monthlyExpenses: 500,
      recentTransactions: [],
      budgetSummaries: [],
      categorySpending: [],
    };

    mockApiRequest.mockResolvedValue(response);

    await expect(getDashboard()).resolves.toEqual(response);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/dashboard");
  });
});
