import { describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { getCategories } from "./categoryService";
import type { CategoryResponse } from "../types/category";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("categoryService", () => {
  it("loads the authenticated user's categories", async () => {
    mockApiRequest.mockResolvedValue([]);

    await expect(getCategories()).resolves.toEqual([]);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/categories");
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
});
