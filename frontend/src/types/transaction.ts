import type { CategoryIconKey, CategorySelection } from "./category";

export type TransactionType = "INCOME" | "EXPENSE";

export type TransactionSortField = "amount" | "transactionDate" | "createdAt";

export type SortDirection = "asc" | "desc";

interface TransactionFields {
  type: TransactionType;
  amount: number;
  description: string;
  transactionDate: string;
}

export type CreateTransactionRequest = TransactionFields & CategorySelection;

export type UpdateTransactionRequest = TransactionFields & CategorySelection;

export interface TransactionResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  categoryIconKey: CategoryIconKey;
  type: TransactionType;
  amount: number;
  description: string;
  transactionDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionFilterRequest {
  type?: TransactionType;
  search?: string;
  startDate?: string;
  endDate?: string;
  minAmount?: number;
  maxAmount?: number;
  sortBy?: TransactionSortField;
  sortDirection?: SortDirection;
  page?: number;
  size?: number;
  /** One of the user's categories; omitted means all categories. */
  categoryId?: number;
}

export interface PagedTransactionResponse {
  transactions: TransactionResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
