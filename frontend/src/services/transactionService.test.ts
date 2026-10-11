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

  it("serializes a category filter and omits it when undefined", async () => {
    await getTransactions({ categoryId: 42, type: "EXPENSE", page: 0 });
    await getTransactions({ categoryId: undefined, type: "EXPENSE" });

    expect(mockApiRequest).toHaveBeenNthCalledWith(
      1,
      "/api/transactions?type=EXPENSE&page=0&categoryId=42",
    );
    expect(mockApiRequest).toHaveBeenNthCalledWith(
      2,
      "/api/transactions?type=EXPENSE",
    );
  });

  it("sends a new category instead of a category ID", async () => {
    const request = {
      newCategory: { name: "Pet Care", iconKey: "paw-print" },
      type: "EXPENSE" as const,
      amount: 40,
      description: "Vet",
      transactionDate: "2026-09-30",
    };

    await createTransaction(request);
    await updateTransaction(9, { ...request, newCategory: { name: "Pets" } });

    expect(mockApiRequest).toHaveBeenNthCalledWith(1, "/api/transactions", {
      method: "POST",
      body: '{"newCategory":{"name":"Pet Care","iconKey":"paw-print"},"type":"EXPENSE","amount":40,"description":"Vet","transactionDate":"2026-09-30"}',
    });
    expect(mockApiRequest).toHaveBeenNthCalledWith(2, "/api/transactions/9", {
      method: "PUT",
      body: '{"newCategory":{"name":"Pets"},"type":"EXPENSE","amount":40,"description":"Vet","transactionDate":"2026-09-30"}',
    });
  });

  it("allows exactly one category selection at compile time", () => {
    const fields = {
      type: "EXPENSE" as const,
      amount: 1,
      description: "x",
      transactionDate: "2026-09-30",
    };
    // @ts-expect-error -- both a category ID and a new category
    void createTransaction({ ...fields, categoryId: 1, newCategory: { name: "Pets" } });
    // @ts-expect-error -- neither a category ID nor a new category
    void createTransaction({ ...fields });

    expect(mockApiRequest).toHaveBeenCalledTimes(2);
  });

  it("passes the abort signal through when one is given", async () => {
    const controller = new AbortController();

    await getTransactions({ page: 1 }, controller.signal);

    expect(mockApiRequest).toHaveBeenCalledExactlyOnceWith("/api/transactions?page=1", { signal: controller.signal });
  });
});
