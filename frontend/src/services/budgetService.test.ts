import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import {
  createBudget,
  deleteBudget,
  getBudget,
  getBudgetAnalytics,
  getMonthBudgetAnalytics,
  getBudgets,
  updateBudget,
} from "./budgetService";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("budgetService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApiRequest.mockResolvedValue(undefined);
  });

  it("loads all budgets", async () => {
    await getBudgets();

    expect(mockApiRequest).toHaveBeenCalledWith("/api/budgets");
  });

  it("loads one budget", async () => {
    await getBudget(12);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/budgets/12");
  });

  it("loads analytics for one budget", async () => {
    await getBudgetAnalytics(12);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/budgets/12/analytics");
  });

  it("creates a budget", async () => {
    const request = {
      categoryId: 3,
      monthlyLimit: 500,
      month: 9,
      year: 2026,
    };

    await createBudget(request);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/budgets", {
      method: "POST",
      body: JSON.stringify(request),
    });
  });

  it("updates a budget", async () => {
    const request = {
      categoryId: 3,
      monthlyLimit: 650,
      month: 9,
      year: 2026,
    };

    await updateBudget(12, request);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/budgets/12", {
      method: "PUT",
      body: JSON.stringify(request),
    });
  });

  it("deletes a budget", async () => {
    await deleteBudget(12);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/budgets/12", {
      method: "DELETE",
    });
  });

  it("sends a new category instead of a category ID", async () => {
    await createBudget({
      newCategory: { name: "Gym", iconKey: "dumbbell" },
      monthlyLimit: 40,
      month: 9,
      year: 2026,
    });
    await updateBudget(12, {
      newCategory: { name: "Date Night" },
      monthlyLimit: 60,
      month: 10,
      year: 2026,
    });

    expect(mockApiRequest).toHaveBeenNthCalledWith(1, "/api/budgets", {
      method: "POST",
      body: '{"newCategory":{"name":"Gym","iconKey":"dumbbell"},"monthlyLimit":40,"month":9,"year":2026}',
    });
    expect(mockApiRequest).toHaveBeenNthCalledWith(2, "/api/budgets/12", {
      method: "PUT",
      body: '{"newCategory":{"name":"Date Night"},"monthlyLimit":60,"month":10,"year":2026}',
    });
  });

  it("loads one month's budgets with analytics in one request, always with both values", async () => {
    await getMonthBudgetAnalytics({ month: 3, year: 2024 });

    expect(mockApiRequest).toHaveBeenCalledExactlyOnceWith("/api/budgets/analytics?month=3&year=2024");
  });

  it("passes the abort signal through for the month request", async () => {
    const controller = new AbortController();

    await getMonthBudgetAnalytics({ month: 12, year: 2030 }, controller.signal);

    expect(mockApiRequest).toHaveBeenCalledExactlyOnceWith("/api/budgets/analytics?month=12&year=2030", {
      signal: controller.signal,
    });
  });
});
