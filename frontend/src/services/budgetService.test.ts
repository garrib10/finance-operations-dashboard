import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import {
  createBudget,
  deleteBudget,
  getBudget,
  getBudgetAnalytics,
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
});
