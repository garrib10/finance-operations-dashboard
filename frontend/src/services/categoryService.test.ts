import { describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { getCategories } from "./categoryService";

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
});
