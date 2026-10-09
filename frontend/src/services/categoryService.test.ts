import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import {
  CATEGORY_BUILT_IN,
  CATEGORY_DUPLICATE,
  CATEGORY_IN_USE,
  CATEGORY_NOT_FOUND,
  createCategory,
  deleteCategory,
  getCategories,
  getCategorySummary,
  updateCategory,
} from "./categoryService";
import type { CategoryResponse } from "../types/category";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("categoryService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the authenticated user's categories", async () => {
    mockApiRequest.mockResolvedValue([]);

    await expect(getCategories()).resolves.toEqual([]);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/categories");
  });

  it("loads the category usage summary for the current month without period parameters", async () => {
    const summary = { month: 10, year: 2026, serverCurrentMonth: 10, serverCurrentYear: 2026, categories: [] };
    mockApiRequest.mockResolvedValue(summary);

    await expect(getCategorySummary()).resolves.toEqual(summary);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/categories/summary");
    await getCategorySummary(null);
    expect(mockApiRequest).toHaveBeenLastCalledWith("/api/categories/summary");
  });

  it("sends both period parameters for an earlier month, with the abort signal", async () => {
    mockApiRequest.mockResolvedValue({});
    const controller = new AbortController();

    await getCategorySummary({ month: 8, year: 2026 }, controller.signal);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/categories/summary?month=8&year=2026",
      { signal: controller.signal });

    await getCategorySummary({ month: 1, year: 2000 });
    expect(mockApiRequest).toHaveBeenLastCalledWith("/api/categories/summary?month=1&year=2000");
  });

  it("returns built-in status and icon keys unchanged", async () => {
    const categories: CategoryResponse[] = [
      {
        id: 1,
        name: "Housing",
        budgetEnabled: true,
        builtIn: true,
        iconKey: "house",
        createdAt: "2026-09-01T10:00:00",
        updatedAt: "2026-09-01T10:00:00",
      },
      {
        id: 20,
        name: "Pet Care",
        budgetEnabled: false,
        builtIn: false,
        iconKey: "tag",
        createdAt: "2026-09-30T09:00:00",
        updatedAt: "2026-09-30T09:00:00",
      },
    ];
    mockApiRequest.mockResolvedValue(categories);

    await expect(getCategories()).resolves.toEqual(categories);
  });

  it("creates, updates, and deletes custom categories through the API wrapper", async () => {
    mockApiRequest.mockResolvedValue(undefined);

    await createCategory({ name: "Pet Care", budgetEnabled: true, iconKey: "paw-print" });
    await updateCategory(40, { name: "Pets", budgetEnabled: false });
    await deleteCategory(40);

    expect(mockApiRequest).toHaveBeenNthCalledWith(1, "/api/categories", {
      method: "POST",
      body: '{"name":"Pet Care","budgetEnabled":true,"iconKey":"paw-print"}',
    });
    expect(mockApiRequest).toHaveBeenNthCalledWith(2, "/api/categories/40", {
      method: "PUT",
      body: '{"name":"Pets","budgetEnabled":false}',
    });
    expect(mockApiRequest).toHaveBeenNthCalledWith(3, "/api/categories/40", { method: "DELETE" });
  });

  it("does not retry a failed mutation itself", async () => {
    mockApiRequest.mockRejectedValueOnce(new Error("network"));

    await expect(deleteCategory(40)).rejects.toThrow("network");
    expect(mockApiRequest).toHaveBeenCalledTimes(1);
  });

  it("exposes the stable category error codes", () => {
    expect([CATEGORY_DUPLICATE, CATEGORY_NOT_FOUND, CATEGORY_BUILT_IN, CATEGORY_IN_USE]).toEqual([
      "CATEGORY_DUPLICATE", "CATEGORY_NOT_FOUND", "CATEGORY_BUILT_IN", "CATEGORY_IN_USE",
    ]);
  });
});
