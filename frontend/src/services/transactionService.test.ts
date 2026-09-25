import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";
import {
  createTransaction,
  deleteTransaction,
  getTransaction,
  getTransactions,
  updateTransaction,
} from "./transactionService";

vi.mock("./api", () => ({
  apiRequest: vi.fn(),
}));

const mockApiRequest = vi.mocked(apiRequest);

describe("transactionService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApiRequest.mockResolvedValue(undefined);
  });

  it("loads transactions without a query string when filters are omitted", async () => {
    await getTransactions();

    expect(mockApiRequest).toHaveBeenCalledWith("/api/transactions");
  });

  it("serializes and encodes every supported transaction filter", async () => {
    await getTransactions({
      type: "EXPENSE",
      search: "Food & dining",
      startDate: "2026-09-01",
      endDate: "2026-09-30",
      minAmount: 10.5,
      maxAmount: 500,
      sortBy: "amount",
      sortDirection: "asc",
      page: 2,
      size: 25,
    });

    expect(mockApiRequest).toHaveBeenCalledWith(
      "/api/transactions?type=EXPENSE&search=Food+%26+dining&startDate=2026-09-01&endDate=2026-09-30&minAmount=10.5&maxAmount=500&sortBy=amount&sortDirection=asc&page=2&size=25",
    );
  });

  it("preserves zero-valued numeric filters", async () => {
    await getTransactions({
      minAmount: 0,
      maxAmount: 0,
      page: 0,
      size: 0,
    });

    expect(mockApiRequest).toHaveBeenCalledWith(
      "/api/transactions?minAmount=0&maxAmount=0&page=0&size=0",
    );
  });

  it("loads one transaction", async () => {
    await getTransaction(9);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/transactions/9");
  });

  it("creates a transaction", async () => {
    const request = {
      categoryId: 2,
      type: "EXPENSE" as const,
      amount: 42.5,
      description: "Groceries",
      transactionDate: "2026-09-22",
    };

    await createTransaction(request);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/transactions", {
      method: "POST",
      body: JSON.stringify(request),
    });
  });

  it("updates a transaction", async () => {
    const request = {
      categoryId: 2,
      type: "EXPENSE" as const,
      amount: 50,
      description: "Updated groceries",
      transactionDate: "2026-09-22",
    };

    await updateTransaction(9, request);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/transactions/9", {
      method: "PUT",
      body: JSON.stringify(request),
    });
  });

  it("deletes a transaction", async () => {
    await deleteTransaction(9);

    expect(mockApiRequest).toHaveBeenCalledWith("/api/transactions/9", {
      method: "DELETE",
    });
  });
});
