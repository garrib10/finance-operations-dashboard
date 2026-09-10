import { apiRequest } from "./api";
import type {
  CreateTransactionRequest,
  PagedTransactionResponse,
  TransactionFilterRequest,
  TransactionResponse,
  UpdateTransactionRequest,
} from "../types/transaction";

function buildTransactionQuery(filters: TransactionFilterRequest = {}): string {
  const searchParams = new URLSearchParams();

  if (filters.type) {
    searchParams.set("type", filters.type);
  }

  if (filters.search) {
    searchParams.set("search", filters.search);
  }

  if (filters.startDate) {
    searchParams.set("startDate", filters.startDate);
  }

  if (filters.endDate) {
    searchParams.set("endDate", filters.endDate);
  }

  if (filters.minAmount !== undefined) {
    searchParams.set("minAmount", filters.minAmount.toString());
  }

  if (filters.maxAmount !== undefined) {
    searchParams.set("maxAmount", filters.maxAmount.toString());
  }

  if (filters.sortBy) {
    searchParams.set("sortBy", filters.sortBy);
  }

  if (filters.sortDirection) {
    searchParams.set("sortDirection", filters.sortDirection);
  }

  if (filters.page !== undefined) {
    searchParams.set("page", filters.page.toString());
  }

  if (filters.size !== undefined) {
    searchParams.set("size", filters.size.toString());
  }

  const queryString = searchParams.toString();

  return queryString ? `?${queryString}` : "";
}

export function getTransactions(
  filters: TransactionFilterRequest = {},
): Promise<PagedTransactionResponse> {
  return apiRequest<PagedTransactionResponse>(
    `/api/transactions${buildTransactionQuery(filters)}`,
  );
}

export function getTransaction(id: number): Promise<TransactionResponse> {
  return apiRequest<TransactionResponse>(`/api/transactions/${id}`);
}

export function createTransaction(
  request: CreateTransactionRequest,
): Promise<TransactionResponse> {
  return apiRequest<TransactionResponse>("/api/transactions", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function updateTransaction(
  id: number,
  request: UpdateTransactionRequest,
): Promise<TransactionResponse> {
  return apiRequest<TransactionResponse>(`/api/transactions/${id}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function deleteTransaction(id: number): Promise<void> {
  return apiRequest<void>(`/api/transactions/${id}`, {
    method: "DELETE",
  });
}
