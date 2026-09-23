import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import { getHealth } from "./healthService";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("healthService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads application health without authentication", async () => {
    const response = {
      status: "ok",
      application: "Finance Operations Dashboard",
    };

    mockApiRequest.mockResolvedValue(response);

    await expect(getHealth()).resolves.toEqual(response);
    expect(mockApiRequest).toHaveBeenCalledWith("/api/health", {
      authenticated: false,
    });
  });
});
